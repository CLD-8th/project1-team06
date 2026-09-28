package com.team6.app.mypage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team6.app.follow.FollowRepository;
import com.team6.app.ranking.WeeklyRankingKey;
import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import com.team6.app.workout.Workout;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// MySQL · Redis 없이 H2(MySQL 모드)로 마이페이지 API를 확인함. 랭킹 순위는 Redis 모의 객체로 대신함
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mypage;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-test-secret-test-secret-32",
        "app.upload-dir=build/test-uploads",
        "management.health.redis.enabled=false"})
class MyPageApiTest {

    private static final Path UPLOAD_DIR = Path.of("build/test-uploads");

    @Autowired
    WebApplicationContext context;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkoutRepository workoutRepository;
    @Autowired
    FollowRepository followRepository;
    @MockitoBean
    StringRedisTemplate redisTemplate;
    @MockitoBean
    ZSetOperations<String, String> zSetOperations;

    MockMvc mvc;
    User me;
    User alice;
    User bob;
    LocalDate today;
    LocalDate monday;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        followRepository.deleteAll();
        workoutRepository.deleteAll();
        userRepository.deleteAll();
        me = userRepository.save(new User("me@test.com", "pw", "me"));
        alice = userRepository.save(new User("alice@test.com", "pw", "alice"));
        bob = userRepository.save(new User("bob@test.com", "pw", "bob"));
        today = LocalDate.now(WeeklyRankingKey.ZONE);
        monday = WeeklyRankingKey.weekStart(today);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
    }

    // 로그인 토큰 대신 subject가 userId인 Jwt를 인증 주체로 넣음
    private RequestPostProcessor login(User user) {
        return jwt().jwt(j -> j.subject(String.valueOf(user.getId())));
    }

    private void workout(User user, String type, int minutes, LocalDate date) {
        workoutRepository.save(new Workout(user, type, minutes, null, null, date));
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("photo", name, "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    }

    @Test
    void 요약은_누적_이번주_순위_연속일_종류별_합계() throws Exception {
        workout(me, "CARDIO", 30, today);
        workout(me, "STRENGTH", 40, today.minusDays(1));
        workout(me, "CARDIO", 20, today.minusDays(2));
        workout(me, "CARDIO", 60, today.minusDays(10));   // 연속이 끊긴 예전 기록
        workout(alice, "CARDIO", 999, today);              // 남의 기록은 섞이지 않음
        given(zSetOperations.reverseRank(WeeklyRankingKey.of(today), String.valueOf(me.getId()))).willReturn(6L);

        long weekCount = java.util.stream.Stream.of(today, today.minusDays(1), today.minusDays(2))
                .filter(d -> !d.isBefore(monday)).count();

        mvc.perform(get("/users/me/summary").with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.totalMinutes").value(150))
                .andExpect(jsonPath("$.weekCount").value(weekCount))
                .andExpect(jsonPath("$.weekRank").value(7))
                .andExpect(jsonPath("$.streakDays").value(3))
                .andExpect(jsonPath("$.byType[?(@.type=='CARDIO')].minutes").value(110))
                .andExpect(jsonPath("$.byType[?(@.type=='STRENGTH')].count").value(1));
    }

    @Test
    void 오늘_기록이_없어도_어제까지_이어졌으면_연속일_유지() throws Exception {
        workout(me, "CARDIO", 30, today.minusDays(1));
        workout(me, "CARDIO", 30, today.minusDays(2));

        mvc.perform(get("/users/me/summary").with(login(me)))
                .andExpect(jsonPath("$.streakDays").value(2));
    }

    @Test
    void 랭킹에_없거나_Redis가_실패해도_요약은_200() throws Exception {
        // Sorted Set에 없는 멤버의 순위는 null
        given(zSetOperations.reverseRank(anyString(), eq(String.valueOf(me.getId())))).willReturn(null);
        mvc.perform(get("/users/me/summary").with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekRank").doesNotExist())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.streakDays").value(0));

        given(zSetOperations.reverseRank(anyString(), eq(String.valueOf(me.getId()))))
                .willThrow(new RedisConnectionFailureException("down"));
        mvc.perform(get("/users/me/summary").with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekRank").doesNotExist());
    }

    // 11
    @Test
    void 주간_통계는_월요일부터_7칸_날짜별_합계() throws Exception {
        workout(me, "CARDIO", 30, monday);
        workout(me, "STRENGTH", 45, monday);
        workout(me, "CARDIO", 20, monday.plusDays(2));
        workout(me, "CARDIO", 90, monday.minusDays(1));    // 지난주 일요일

        mvc.perform(get("/users/me/stats/weekly").param("date", monday.plusDays(3).toString()).with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.week").value(WeeklyRankingKey.weekLabel(monday)))
                .andExpect(jsonPath("$.start").value(monday.toString()))
                .andExpect(jsonPath("$.end").value(monday.plusDays(6).toString()))
                .andExpect(jsonPath("$.workoutCount").value(3))
                .andExpect(jsonPath("$.totalMinutes").value(95))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].minutes").value(75))
                .andExpect(jsonPath("$.days[1].minutes").value(0))
                .andExpect(jsonPath("$.days[2].minutes").value(20))
                .andExpect(jsonPath("$.byType[?(@.type=='STRENGTH')].minutes").value(45));
    }

    @Test
    void 로그인_안하면_401() throws Exception {
        mvc.perform(get("/users/me/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me/stats/weekly")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/users/me").contentType("application/json").content("{\"nickname\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 닉네임_수정과_검증() throws Exception {
        mvc.perform(patch("/users/me").with(login(me)).contentType("application/json").content("{\"nickname\":\"  다경  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("다경"))
                .andExpect(jsonPath("$.joinedAt").value(today.toString()));
        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("다경");

        mvc.perform(patch("/users/me").with(login(me)).contentType("application/json").content("{\"nickname\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/users/me").with(login(me)).contentType("application/json")
                        .content("{\"nickname\":\"" + "가".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 프로필_사진_올리기_바꾸기_삭제() throws Exception {
        String first = mvc.perform(multipart("/users/me/photo").file(png("a.png")).with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String firstKey = first.replaceAll(".*\"photoUrl\":\"/photos/([^\"]+)\".*", "$1");
        assertThat(Files.exists(UPLOAD_DIR.resolve(firstKey))).isTrue();

        // 바꾸면 예전 파일은 지워지고 새 파일만 남음
        String second = mvc.perform(multipart("/users/me/photo").file(png("b.png")).with(login(me)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondKey = second.replaceAll(".*\"photoUrl\":\"/photos/([^\"]+)\".*", "$1");
        assertThat(secondKey).isNotEqualTo(firstKey);
        assertThat(Files.exists(UPLOAD_DIR.resolve(firstKey))).isFalse();
        assertThat(Files.exists(UPLOAD_DIR.resolve(secondKey))).isTrue();

        mvc.perform(get("/users/{id}/profile", me.getId()).with(login(alice)))
                .andExpect(jsonPath("$.photoUrl").value("/photos/" + secondKey));

        mvc.perform(delete("/users/me/photo").with(login(me))).andExpect(status().isNoContent());
        assertThat(Files.exists(UPLOAD_DIR.resolve(secondKey))).isFalse();
        mvc.perform(get("/users/{id}/profile", me.getId()).with(login(alice)))
                .andExpect(jsonPath("$.photoUrl").doesNotExist());
    }

    @Test
    void 프로필_사진은_이미지만() throws Exception {
        MockMultipartFile gif = new MockMultipartFile("photo", "x.gif", "image/gif", new byte[] {1, 2, 3});
        mvc.perform(multipart("/users/me/photo").file(gif).with(login(me))).andExpect(status().isBadRequest());
    }

    @Test
    void 팔로워_팔로잉_목록과_보는_사람의_팔로우_여부() throws Exception {
        mvc.perform(post("/users/{id}/follow", me.getId()).with(login(alice))).andExpect(status().isNoContent());
        mvc.perform(post("/users/{id}/follow", me.getId()).with(login(bob))).andExpect(status().isNoContent());
        mvc.perform(post("/users/{id}/follow", alice.getId()).with(login(me))).andExpect(status().isNoContent());

        // me의 팔로워: bob, alice (최근 순). me는 alice만 팔로우 중
        mvc.perform(get("/users/{id}/followers", me.getId()).with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nickname").value("bob"))
                .andExpect(jsonPath("$[0].following").value(false))
                .andExpect(jsonPath("$[1].nickname").value("alice"))
                .andExpect(jsonPath("$[1].following").value(true));
        mvc.perform(get("/users/{id}/followees", me.getId()).with(login(me)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nickname").value("alice"));
        mvc.perform(get("/users/{id}/followers", 999_999L).with(login(me))).andExpect(status().isNotFound());
    }

    @Test
    void 프로필에_가입일() throws Exception {
        mvc.perform(get("/users/{id}/profile", me.getId()).with(login(me)))
                .andExpect(jsonPath("$.joinedAt").value(today.toString()))
                .andExpect(jsonPath("$.me").value(true));
    }

    // 6번 종류 필터
    @Test
    void 기록_목록_종류_필터() throws Exception {
        workout(me, "CARDIO", 30, today);
        workout(me, "STRENGTH", 40, today);
        workout(me, "CARDIO", 20, today);

        mvc.perform(get("/users/{id}/workouts", me.getId()).param("type", "CARDIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("CARDIO"));
        mvc.perform(get("/users/{id}/workouts", me.getId()))
                .andExpect(jsonPath("$.totalElements").value(3));
    }
}
