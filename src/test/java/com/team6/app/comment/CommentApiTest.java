package com.team6.app.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import com.team6.app.workout.Workout;
import com.team6.app.workout.WorkoutRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// MySQL · Redis 없이 H2(MySQL 모드)로 댓글 등록/조회/삭제를 확인함
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:comment;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-test-secret-test-secret-32",
        "management.health.redis.enabled=false"})
class CommentApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkoutRepository workoutRepository;
    @Autowired
    CommentRepository commentRepository;

    MockMvc mvc;
    User me;
    User alice;
    Workout workout;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        commentRepository.deleteAll();
        workoutRepository.deleteAll();
        userRepository.deleteAll();
        me = userRepository.save(new User("me@test.com", "pw", "me"));
        alice = userRepository.save(new User("alice@test.com", "pw", "alice"));
        workout = workoutRepository.save(new Workout(me, "CARDIO", 30, null, null, LocalDate.now()));
    }

    // 로그인 토큰 대신 subject가 userId인 Jwt를 인증 주체로 넣음
    private RequestPostProcessor login(User user) {
        return jwt().jwt(j -> j.subject(String.valueOf(user.getId())));
    }

    @Test
    void 댓글_등록_성공_201() throws Exception {
        mvc.perform(post("/workouts/{id}/comments", workout.getId())
                        .with(login(alice))
                        .contentType("application/json")
                        .content("{\"content\":\"화이팅!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("alice"))
                .andExpect(jsonPath("$.content").value("화이팅!"));
        assertThat(commentRepository.count()).isEqualTo(1);
    }

    @Test
    void 빈_내용은_400() throws Exception {
        mvc.perform(post("/workouts/{id}/comments", workout.getId())
                        .with(login(alice))
                        .contentType("application/json")
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_기록에_댓글_등록하면_404() throws Exception {
        mvc.perform(post("/workouts/{id}/comments", 999_999L)
                        .with(login(alice))
                        .contentType("application/json")
                        .content("{\"content\":\"안녕\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 댓글_목록은_인증_없이_조회_가능() throws Exception {
        commentRepository.save(new Comment(workout, alice, "1등 축하!"));
        mvc.perform(get("/workouts/{id}/comments", workout.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("1등 축하!"))
                .andExpect(jsonPath("$[0].nickname").value("alice"));
    }

    @Test
    void 본인_댓글_삭제_성공_204() throws Exception {
        Comment c = commentRepository.save(new Comment(workout, alice, "삭제될 댓글"));
        mvc.perform(delete("/comments/{id}", c.getId()).with(login(alice)))
                .andExpect(status().isNoContent());
        assertThat(commentRepository.existsById(c.getId())).isFalse();
    }

    @Test
    void 남의_댓글_삭제하면_403() throws Exception {
        Comment c = commentRepository.save(new Comment(workout, alice, "지우면 안 됨"));
        mvc.perform(delete("/comments/{id}", c.getId()).with(login(me)))
                .andExpect(status().isForbidden());
        assertThat(commentRepository.existsById(c.getId())).isTrue();
    }
}
