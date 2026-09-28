package com.team6.app.mypage;

import com.team6.app.workout.Workout;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// 마이페이지 통계용 읽기 전용 조회. 기록 쪽 저장소(A 파트)를 건드리지 않으려고 따로 둠
public interface MyPageStatsRepository extends Repository<Workout, Long> {

    @Query("""
            select count(w) as count, coalesce(sum(w.durationMin), 0) as minutes
            from Workout w
            where w.user.id = :userId
            """)
    Totals totals(@Param("userId") Long userId);

    @Query("""
            select count(w) as count, coalesce(sum(w.durationMin), 0) as minutes
            from Workout w
            where w.user.id = :userId and w.workoutDate between :start and :end
            """)
    Totals totalsBetween(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select w.type as type, count(w) as count, sum(w.durationMin) as minutes
            from Workout w
            where w.user.id = :userId
            group by w.type
            """)
    List<TypeRow> byType(@Param("userId") Long userId);

    @Query("""
            select w.type as type, count(w) as count, sum(w.durationMin) as minutes
            from Workout w
            where w.user.id = :userId and w.workoutDate between :start and :end
            group by w.type
            """)
    List<TypeRow> byTypeBetween(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 연속 운동일 계산용: 오늘 이전 기록 날짜를 최신순으로 (중복 제거)
    @Query("""
            select distinct w.workoutDate
            from Workout w
            where w.user.id = :userId and w.workoutDate <= :today
            order by w.workoutDate desc
            """)
    List<LocalDate> recentDates(@Param("userId") Long userId, @Param("today") LocalDate today, Pageable pageable);

    interface Totals {
        Long getCount();

        Long getMinutes();
    }

    interface TypeRow {
        String getType();

        Long getCount();

        Long getMinutes();
    }
}
