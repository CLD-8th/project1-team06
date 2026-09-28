package com.team6.app.ranking;

import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RankingService {

    // 이번 주 포함 5주가 지나면 키가 자동 삭제됨
    private static final int KEEP_WEEKS = 5;
    private static final String UNKNOWN_NICKNAME = "(알 수 없음)";

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    // 운동 기록 등록 시 호출: workoutDate가 속한 주의 랭킹에 운동 시간(분)을 더함
    public void addScore(Long userId, int durationMin, LocalDate workoutDate) {
        if (durationMin <= 0) {
            return;
        }
        applyScoreDelta(userId, durationMin, workoutDate);
    }

    // 운동 기록 삭제 시 호출: workoutDate가 속한 주의 랭킹에서 운동 시간(분)만큼 뺌 (A 파트에서 추가)
    public void subtractScore(Long userId, int durationMin, LocalDate workoutDate) {
        if (durationMin <= 0) {
            return;
        }
        applyScoreDelta(userId, -durationMin, workoutDate);
    }

    private void applyScoreDelta(Long userId, int deltaMinutes, LocalDate workoutDate) {
        if (deltaMinutes == 0) {
            return;
        }
        String key = WeeklyRankingKey.of(workoutDate);
        String member = String.valueOf(userId);
        Double score = redisTemplate.opsForZSet().incrementScore(key, member, deltaMinutes);
        // 기록을 모두 지워 0분 이하가 되면 랭킹에 "0분"으로 남지 않도록 멤버를 제거함
        if (score != null && score <= 0) {
            redisTemplate.opsForZSet().remove(key, member);
        }
        redisTemplate.expireAt(key, expireAt(workoutDate));
    }

    // date가 속한 주의 상위 size명을 운동 시간 순으로 조회
    public WeeklyRankingResponse getWeeklyTop(LocalDate date, int size) {
        String key = WeeklyRankingKey.of(date);
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, size - 1);
        if (tuples == null || tuples.isEmpty()) {
            return new WeeklyRankingResponse(WeeklyRankingKey.weekLabel(date), List.of());
        }

        List<Long> userIds = tuples.stream().map(t -> Long.valueOf(t.getValue())).toList();
        // TOP N 사용자의 닉네임을 한 번의 쿼리로 조회
        Map<Long, String> nicknames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));

        List<WeeklyRankingResponse.Entry> entries = new ArrayList<>();
        int rank = 1;
        for (TypedTuple<String> tuple : tuples) {
            Long userId = Long.valueOf(tuple.getValue());
            long minutes = tuple.getScore() == null ? 0 : tuple.getScore().longValue();
            entries.add(new WeeklyRankingResponse.Entry(
                    rank++, userId, nicknames.getOrDefault(userId, UNKNOWN_NICKNAME), minutes));
        }
        return new WeeklyRankingResponse(WeeklyRankingKey.weekLabel(date), entries);
    }

    private Instant expireAt(LocalDate workoutDate) {
        return WeeklyRankingKey.weekStart(workoutDate)
                .plusWeeks(KEEP_WEEKS)
                .atStartOfDay(WeeklyRankingKey.ZONE)
                .toInstant();
    }
}
