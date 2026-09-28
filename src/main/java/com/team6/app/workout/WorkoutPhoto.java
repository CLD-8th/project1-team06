package com.team6.app.workout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 기록당 인증사진 1장. workout_id에 UNIQUE를 둬서 여러 장이 쌓이지 않게 함
@Entity
@Table(name = "workout_photos",
        uniqueConstraints = @UniqueConstraint(name = "uk_workout_photos_workout_id", columnNames = "workout_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkoutPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workout_id", nullable = false)
    private Long workoutId;

    // 실제 저장 파일명 (UUID_원본파일명). /photos/{storedKey}로 조회함
    @Column(name = "stored_key", nullable = false, length = 255)
    private String storedKey;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public WorkoutPhoto(Long workoutId, String storedKey, String originalName, Long sizeBytes) {
        this.workoutId = workoutId;
        this.storedKey = storedKey;
        this.originalName = originalName;
        this.sizeBytes = sizeBytes;
        this.createdAt = LocalDateTime.now();
    }
}
