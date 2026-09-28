package com.team6.app.workout;

import java.math.BigDecimal;
import java.time.LocalDate;

// API 5 응답 형태. 5 · 6 · 10번이 같이 씀. photoUrl은 사진 첨부(13번) 구현 시 채움
public record WorkoutResponse(
        Long id,
        Long userId,
        String nickname,
        String type,
        int durationMin,
        BigDecimal distanceKm,
        String memo,
        LocalDate workoutDate,
        String photoUrl) {

    public static WorkoutResponse from(Workout workout) {
        return new WorkoutResponse(
                workout.getId(),
                workout.getUser().getId(),
                workout.getUser().getNickname(),
                workout.getType(),
                workout.getDurationMin(),
                workout.getDistanceKm(),
                workout.getMemo(),
                workout.getWorkoutDate(),
                null);
    }
}
