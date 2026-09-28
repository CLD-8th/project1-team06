package com.team6.app.workout;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPhotoRepository extends JpaRepository<WorkoutPhoto, Long> {

    Optional<WorkoutPhoto> findByWorkoutId(Long workoutId);

    // 목록 화면(피드 등)에서 기록 여러 개의 사진을 쿼리 1번으로 가져오기 위함
    List<WorkoutPhoto> findByWorkoutIdIn(Collection<Long> workoutIds);
}
