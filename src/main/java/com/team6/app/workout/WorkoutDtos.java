package com.team6.app.workout;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    public record CreateRequest(
            @NotBlank @Pattern(regexp = "CARDIO|STRENGTH", message = "type은 CARDIO 또는 STRENGTH여야 합니다.") String type,
            @NotNull @Min(1) @Max(300) Integer durationMin,
            // 유산소일 때만 씀. 선택 입력이라 null 허용
            @DecimalMin(value = "0.01") BigDecimal distanceKm,
            @Size(max = 500) String memo,
            @NotNull @PastOrPresent LocalDate workoutDate) {
    }

    public record PhotoResponse(String photoUrl, String storedKey) {
    }
}
