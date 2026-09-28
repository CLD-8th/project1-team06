package com.team6.app.workout;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPhotoRepository extends JpaRepository<WorkoutPhoto, Long> {

    Optional<WorkoutPhoto> findByWorkoutId(Long workoutId);
}
