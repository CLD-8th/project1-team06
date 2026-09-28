package com.team6.app.workout;

import com.team6.app.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 운동 기록 (A 파트 소유). 피드 조회에 필요한 최소 필드만 API 4 요청 형태로 둠
@Entity
@Table(name = "workouts",
        indexes = @Index(name = "idx_workouts_user_date", columnList = "user_id, workout_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(length = 500)
    private String memo;

    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Workout(User user, String type, int durationMin, BigDecimal distanceKm, String memo, LocalDate workoutDate) {
        this.user = user;
        this.type = type;
        this.durationMin = durationMin;
        this.distanceKm = distanceKm;
        this.memo = memo;
        this.workoutDate = workoutDate;
        this.createdAt = LocalDateTime.now();
    }
}
