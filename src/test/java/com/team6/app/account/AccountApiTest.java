package com.team6.app.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team6.app.comment.Comment;
import com.team6.app.comment.CommentRepository;
import com.team6.app.follow.Follow;
import com.team6.app.follow.FollowRepository;
import com.team6.app.ranking.WeeklyRankingKey;
import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import com.team6.app.workout.Workout;
import com.team6.app.workout.WorkoutPhoto;
import com.team6.app.workout.WorkoutPhotoRepository;
import com.team6.app.workout.WorkoutRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// MySQL · Redis 없이 H2(MySQL 모드)로 회원 탈퇴를 확인함. 랭킹 정리는 Redis 모의 객체 호출로 확인
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-test-secret-test-secret-32",
        "app.upload-dir=build/test-uploads",
        "management.health.redis.enabled=false"})
class AccountApiTest {

    private static final Path UPLOAD_DIR = Path.of("build/test-uploads");
    private static final String PASSWORD = "password1234";

    @Autowired
    WebApplicationContext context;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkoutRepository workoutRepository;
    @Autowired
    WorkoutPhotoRepository workoutPhotoRepository;
    @Autowired
    CommentRepository commentRepository;
    @Autowired
    FollowRepository followRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    StringRedisTemplate redisTemplate;
    @MockitoBean
    ZSetOperations<String, String> zSetOperations;

    MockMvc mvc;
    User me;
    User alice;
    Workout myWorkout;
    Workout aliceWorkout;
    String photoKey;
    String profileKey;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        commentRepository.deleteAll();
        workoutPhotoRepository.deleteAll();
        followRepository.deleteAll();
        workoutRepository.deleteAll();
        userRepository.deleteAll();
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        me = new User("me@test.com", passwordEncoder.encode(PASSWORD), "me");
        profileKey = "profile_test_" + System.nanoTime() + ".png";
        me.changeProfileImage(profileKey);
        me = userRepository.save(me);
        alice = userRepository.save(new User("alice@test.com", passwordEncoder.encode(PASSWORD), "alice"));

        myWorkout = workoutRepository.save(new Workout(me, "CARDIO", 30, null, null, LocalDate.of(2026, 9, 28)));
        workoutRepository.save(new Workout(me, "STRENGTH", 40, null, null, LocalDate.of(2026, 9, 20)));
        aliceWorkout = workoutRepository.save(new Workout(alice, "CARDIO", 50, null, null, LocalDate.of(2026, 9, 28)));

        photoKey = "photo_test_" + System.nanoTime() + ".png";
        workoutPhotoRepository.save(new WorkoutPhoto(myWorkout.getId(), photoKey, "a.png", 4L));
        Files.createDirectories(UPLOAD_DIR);
        Files.write(UPLOAD_DIR.resolve(photoKey), new byte[] {1});
        Files.write(UPLOAD_DIR.resolve(profileKey), new byte[] {1});

        commentRepository.save(new Comment(myWorkout, alice, "alice가 내 기록에"));
        commentRepository.save(new Comment(aliceWorkout, me, "내가 alice 기록에"));
        commentRepository.save(new Comment(aliceWorkout, alice, "alice가 자기 기록에"));
        followRepository.save(new Follow(me, alice));
        followRepository.save(new Follow(alice, me));
    }

    private RequestPostProcessor login(User user) {
        return jwt().jwt(j -> j.subject(String.valueOf(user.getId())));
    }

    private String body(String password) {
        return "{\"password\":\"" + password + "\"}";
    }

    @Test
    void 탈퇴하면_내_데이터는_모두_지워지고_남의_데이터는_남음() throws Exception {
        mvc.perform(delete("/users/me").with(login(me)).contentType("application/json").content(body(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(me.getId())).isEmpty();
        assertThat(workoutRepository.count()).isEqualTo(1);                     // alice 기록만 남음
        assertThat(workoutPhotoRepository.count()).isZero();
        assertThat(commentRepository.count()).isEqualTo(1);                     // alice가 자기 기록에 단 댓글만
        assertThat(commentRepository.findAll().get(0).getContent()).isEqualTo("alice가 자기 기록에");
        assertThat(followRepository.count()).isZero();
        assertThat(Files.exists(UPLOAD_DIR.resolve(photoKey))).isFalse();
        assertThat(Files.exists(UPLOAD_DIR.resolve(profileKey))).isFalse();

        // 기록이 있던 두 주의 랭킹에서 빠짐
        verify(zSetOperations).remove(WeeklyRankingKey.of(LocalDate.of(2026, 9, 28)), String.valueOf(me.getId()));
        verify(zSetOperations).remove(WeeklyRankingKey.of(LocalDate.of(2026, 9, 20)), String.valueOf(me.getId()));

        // 남은 사람의 프로필 수치도 바뀜
        mvc.perform(get("/users/{id}/profile", alice.getId()).with(login(alice)))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followeeCount").value(0));
    }

    @Test
    void 탈퇴하면_로그인_안되고_같은_이메일로_다시_가입_가능() throws Exception {
        mvc.perform(delete("/users/me").with(login(me)).contentType("application/json").content(body(PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"me@test.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/signup").contentType("application/json")
                        .content("{\"email\":\"me@test.com\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"again\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 비밀번호가_틀리면_400이고_아무것도_지워지지_않음() throws Exception {
        mvc.perform(delete("/users/me").with(login(me)).contentType("application/json").content(body("wrong-password")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/users/me").with(login(me)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(me.getId())).isPresent();
        assertThat(workoutRepository.count()).isEqualTo(3);
        assertThat(commentRepository.count()).isEqualTo(3);
        assertThat(Files.exists(UPLOAD_DIR.resolve(photoKey))).isTrue();
        verify(zSetOperations, never()).remove(anyString(), anyString());
    }

    @Test
    void Redis가_실패해도_탈퇴는_완료() throws Exception {
        given(zSetOperations.remove(anyString(), anyString())).willThrow(new RedisConnectionFailureException("down"));

        mvc.perform(delete("/users/me").with(login(me)).contentType("application/json").content(body(PASSWORD)))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(me.getId())).isEmpty();
    }

    @Test
    void 로그인_안하면_401() throws Exception {
        mvc.perform(delete("/users/me").contentType("application/json").content(body(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // jwt() 모의 토큰은 디코더 검증을 건너뛰므로, 실제 로그인 토큰으로 확인함
    @Test
    void 탈퇴하면_쓰던_토큰은_바로_401() throws Exception {
        String login = mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"me@test.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + login.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/users/me").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(delete("/users/me").header("Authorization", token)
                        .contentType("application/json").content(body(PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/users/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/feed").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/workouts").header("Authorization", token).contentType("application/json")
                        .content("{\"type\":\"CARDIO\",\"durationMin\":10,\"workoutDate\":\"2026-09-28\"}"))
                .andExpect(status().isUnauthorized());

        // 같은 이메일로 다시 가입해도 옛 토큰(옛 id)은 계속 막힘
        mvc.perform(post("/auth/signup").contentType("application/json")
                        .content("{\"email\":\"me@test.com\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"again\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/users/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }
}
