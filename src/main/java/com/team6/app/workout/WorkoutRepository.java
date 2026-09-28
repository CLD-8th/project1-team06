package com.team6.app.workout;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    // 상세 조회(5번)에서 user를 N+1 없이 함께 가져옴
    @EntityGraph(attributePaths = "user")
    Optional<Workout> findWithUserById(Long id);

    // 특정 사용자 기록 목록(6번), 최신순
    @EntityGraph(attributePaths = "user")
    Page<Workout> findByUser_IdOrderByIdDesc(Long userId, Pageable pageable);

    // 6번 종류 필터 (마이페이지 탭)
    @EntityGraph(attributePaths = "user")
    Page<Workout> findByUser_IdAndTypeOrderByIdDesc(Long userId, String type, Pageable pageable);

    /**
     * 사용자의 주간 날짜별 운동시간 합계.
     * 주간 통계(C)가 사용하는 조회임. 기록이 없는 날짜는 행 자체가 없으니 호출 측에서 0으로 채워야 함.
     */
    @Query("""
            select w.workoutDate as workoutDate, sum(w.durationMin) as minutes
            from Workout w
            where w.user.id = :userId and w.workoutDate between :start and :end
            group by w.workoutDate
            """)
    List<DailyMinutes> sumMinutesByDate(@Param("userId") Long userId,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end);

    interface DailyMinutes {
        LocalDate getWorkoutDate();
        Integer getMinutes();
    }
}
