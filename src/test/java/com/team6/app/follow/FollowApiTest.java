package com.team6.app.follow;

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

// MySQL · Redis 없이 H2(MySQL 모드)로 API 8~10 동작을 확인함
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:follow;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-test-secret-test-secret-32",
        "management.health.redis.enabled=false"})
class FollowApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkoutRepository workoutRepository;
    @Autowired
    FollowRepository followRepository;

    MockMvc mvc;
    User me;
    User alice;
    User bob;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        followRepository.deleteAll();
        workoutRepository.deleteAll();
        userRepository.deleteAll();
        me = userRepository.save(new User("me@test.com", "pw", "me"));
        alice = userRepository.save(new User("alice@test.com", "pw", "alice"));
        bob = userRepository.save(new User("bob@test.com", "pw", "bob"));
    }

    // 로그인 토큰 대신 subject가 userId인 Jwt를 인증 주체로 넣음
    private RequestPostProcessor login(User user) {
        return jwt().jwt(j -> j.subject(String.valueOf(user.getId())));
    }

    private void follow(User from, User to) throws Exception {
        mvc.perform(post("/users/{id}/follow", to.getId()).with(login(from)))
                .andExpect(status().isNoContent());
    }

    @Test
    void 팔로우_성공_204() throws Exception {
        follow(me, alice);
        assertThat(followRepository.existsByFollowerIdAndFolloweeId(me.getId(), alice.getId())).isTrue();
    }

    @Test
    void 자기_자신_팔로우_400() throws Exception {
        mvc.perform(post("/users/{id}/follow", me.getId()).with(login(me)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_사용자_팔로우_404() throws Exception {
        mvc.perform(post("/users/{id}/follow", 999_999L).with(login(me)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 중복_팔로우_409() throws Exception {
        follow(me, alice);
        mvc.perform(post("/users/{id}/follow", alice.getId()).with(login(me)))
                .andExpect(status().isConflict());
        assertThat(followRepository.count()).isEqualTo(1);
    }

    @Test
    void 토큰_없으면_401() throws Exception {
        mvc.perform(post("/users/{id}/follow", alice.getId())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/users/{id}/follow", alice.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/feed")).andExpect(status().isUnauthorized());
    }

    @Test
    void 언팔로우_204_반복해도_204() throws Exception {
        follow(me, alice);
        mvc.perform(delete("/users/{id}/follow", alice.getId()).with(login(me)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/users/{id}/follow", alice.getId()).with(login(me)))
                .andExpect(status().isNoContent());
        assertThat(followRepository.count()).isZero();
    }

    @Test
    void 피드는_팔로우한_사람_기록만_최신순() throws Exception {
        LocalDate d = LocalDate.of(2026, 9, 21);
        workoutRepository.save(new Workout(alice, "RUN", 30, null, null, d));
        workoutRepository.save(new Workout(alice, "SWIM", 40, null, null, d.plusDays(2)));
        workoutRepository.save(new Workout(bob, "GYM", 50, null, null, d.plusDays(5)));
        workoutRepository.save(new Workout(me, "RUN", 60, null, null, d.plusDays(6)));
        follow(me, alice);

        mvc.perform(get("/feed").with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("SWIM"))
                .andExpect(jsonPath("$[0].userId").value(alice.getId()))
                .andExpect(jsonPath("$[0].nickname").value("alice"))
                .andExpect(jsonPath("$[1].type").value("RUN"));
    }

    @Test
    void 피드는_20개씩_끊음() throws Exception {
        for (int i = 0; i < 25; i++) {
            workoutRepository.save(new Workout(alice, "RUN", 10, null, null, LocalDate.of(2026, 9, 1).plusDays(i)));
        }
        follow(me, alice);

        mvc.perform(get("/feed").param("page", "0").with(login(me)))
                .andExpect(jsonPath("$.length()").value(20));
        mvc.perform(get("/feed").param("page", "1").with(login(me)))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void 팔로우_없으면_빈_피드() throws Exception {
        mvc.perform(get("/feed").with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 음수_page_400() throws Exception {
        mvc.perform(get("/feed").param("page", "-1").with(login(me)))
                .andExpect(status().isBadRequest());
    }
}
