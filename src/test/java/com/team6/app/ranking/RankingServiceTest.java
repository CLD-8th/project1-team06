package com.team6.app.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private UserRepository userRepository;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(redisTemplate, userRepository);
    }

    @Test
    void 기록_등록_시_해당_주_키에_운동_시간만큼_점수를_더하고_5주_뒤_만료() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);

        rankingService.addScore(7L, 45, LocalDate.of(2026, 10, 1));

        verify(zSetOps).incrementScore("ranking:weekly:2026-W40", "7", 45);
        // 주 시작(2026-09-28 월) + 5주 = 2026-11-02 00:00 KST
        verify(redisTemplate).expireAt("ranking:weekly:2026-W40", Instant.parse("2026-11-01T15:00:00Z"));
    }

    @Test
    void 운동_시간이_0분_이하면_점수를_반영하지_않음() {
        rankingService.addScore(7L, 0, LocalDate.of(2026, 10, 1));

        verify(redisTemplate, never()).opsForZSet();
        verify(zSetOps, never()).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    void TOP_N을_점수_순으로_닉네임과_함께_반환() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(TypedTuple.of("2", 120.0));
        tuples.add(TypedTuple.of("1", 90.0));
        tuples.add(TypedTuple.of("9", 30.0));
        given(zSetOps.reverseRangeWithScores("ranking:weekly:2026-W40", 0, 9)).willReturn(tuples);
        given(userRepository.findAllById(any())).willReturn(List.of(user(1L, "준엽"), user(2L, "윤기")));

        WeeklyRankingResponse response = rankingService.getWeeklyTop(LocalDate.of(2026, 9, 28), 10);

        assertThat(response.week()).isEqualTo("2026-W40");
        assertThat(response.entries()).containsExactly(
                new WeeklyRankingResponse.Entry(1, 2L, "윤기", 120),
                new WeeklyRankingResponse.Entry(2, 1L, "준엽", 90),
                new WeeklyRankingResponse.Entry(3, 9L, "(알 수 없음)", 30));
    }

    @Test
    void 랭킹이_비어_있으면_빈_목록() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);
        given(zSetOps.reverseRangeWithScores(anyString(), any(Long.class), any(Long.class))).willReturn(Set.of());

        WeeklyRankingResponse response = rankingService.getWeeklyTop(LocalDate.of(2026, 9, 28), 10);

        assertThat(response.entries()).isEmpty();
        verify(userRepository, never()).findAllById(any());
    }

    private static User user(Long id, String nickname) {
        User user = new User(nickname + "@team6.test", "hash", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
