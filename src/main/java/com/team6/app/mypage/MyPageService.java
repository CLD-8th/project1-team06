package com.team6.app.mypage;

import com.team6.app.mypage.MyPageDtos.DayMinutes;
import com.team6.app.mypage.MyPageDtos.MeResponse;
import com.team6.app.mypage.MyPageDtos.SummaryResponse;
import com.team6.app.mypage.MyPageDtos.TypeTotal;
import com.team6.app.mypage.MyPageDtos.WeeklyStatsResponse;
import com.team6.app.mypage.MyPageStatsRepository.Totals;
import com.team6.app.mypage.MyPageStatsRepository.TypeRow;
import com.team6.app.ranking.WeeklyRankingKey;
import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import com.team6.app.workout.WorkoutRepository;
import com.team6.app.workout.WorkoutRepository.DailyMinutes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyPageService {

    // 연속 운동일을 셀 때 최대로 보는 날짜 수
    private static final int STREAK_LOOKBACK = 400;

    private final UserRepository userRepository;
    private final MyPageStatsRepository statsRepository;
    private final WorkoutRepository workoutRepository;
    private final ProfilePhotoStorage photoStorage;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public MeResponse updateNickname(Long userId, String nickname) {
        User user = getUser(userId);
        user.changeNickname(nickname.strip());
        return toMe(user);
    }

    // 새 사진을 먼저 저장하고, DB 커밋이 끝난 뒤 예전 파일을 지움 (실패해도 예전 사진이 남도록)
    @Transactional
    public MeResponse changePhoto(Long userId, MultipartFile file) {
        User user = getUser(userId);
        String oldKey = user.getProfileImageKey();
        String newKey = photoStorage.save(userId, file);
        user.changeProfileImage(newKey);
        afterCommit(() -> photoStorage.delete(oldKey), () -> photoStorage.delete(newKey));
        return toMe(user);
    }

    @Transactional
    public void deletePhoto(Long userId) {
        User user = getUser(userId);
        String oldKey = user.getProfileImageKey();
        user.changeProfileImage(null);
        afterCommit(() -> photoStorage.delete(oldKey), () -> { });
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Long userId) {
        getUser(userId);
        LocalDate today = LocalDate.now(WeeklyRankingKey.ZONE);
        LocalDate start = WeeklyRankingKey.weekStart(today);
        Totals all = statsRepository.totals(userId);
        Totals week = statsRepository.totalsBetween(userId, start, start.plusDays(6));
        return new SummaryResponse(
                all.getCount(), all.getMinutes(),
                week.getCount(), week.getMinutes(),
                weekRank(userId, today),
                streak(userId, today),
                toTypeTotals(statsRepository.byType(userId)));
    }

    // 11. date가 속한 주(월~일)의 통계
    @Transactional(readOnly = true)
    public WeeklyStatsResponse weekly(Long userId, LocalDate date) {
        LocalDate start = WeeklyRankingKey.weekStart(date);
        LocalDate end = start.plusDays(6);
        Totals totals = statsRepository.totalsBetween(userId, start, end);
        Map<LocalDate, Integer> byDate = workoutRepository.sumMinutesByDate(userId, start, end).stream()
                .collect(Collectors.toMap(DailyMinutes::getWorkoutDate, DailyMinutes::getMinutes));
        List<DayMinutes> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            days.add(new DayMinutes(d, byDate.getOrDefault(d, 0)));
        }
        return new WeeklyStatsResponse(
                WeeklyRankingKey.weekLabel(date), start, end,
                totals.getCount(), totals.getMinutes(),
                toTypeTotals(statsRepository.byTypeBetween(userId, start, end)),
                days);
    }

    // 오늘(없으면 어제)부터 거꾸로 하루도 빠짐없이 기록한 날 수
    private int streak(Long userId, LocalDate today) {
        Set<LocalDate> dates = new HashSet<>(
                statsRepository.recentDates(userId, today, PageRequest.of(0, STREAK_LOOKBACK)));
        LocalDate day = dates.contains(today) ? today : today.minusDays(1);
        int count = 0;
        while (dates.contains(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    // 주간 랭킹 Sorted Set에서 순위를 읽음. Redis 장애로 마이페이지 전체가 실패하지 않게 null로 둠
    private Integer weekRank(Long userId, LocalDate today) {
        try {
            Long rank = redisTemplate.opsForZSet().reverseRank(WeeklyRankingKey.of(today), String.valueOf(userId));
            return rank == null ? null : rank.intValue() + 1;
        } catch (DataAccessException e) {
            log.warn("주간 랭킹 순위 조회 실패: userId={}", userId, e);
            return null;
        }
    }

    private List<TypeTotal> toTypeTotals(List<TypeRow> rows) {
        return rows.stream()
                .map(r -> new TypeTotal(r.getType(), r.getCount(), r.getMinutes()))
                .toList();
    }

    private void afterCommit(Runnable onCommit, Runnable onRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            onCommit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                (status == STATUS_COMMITTED ? onCommit : onRollback).run();
            }
        });
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private MeResponse toMe(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getPhotoUrl(),
                user.getCreatedAt().toLocalDate());
    }
}
