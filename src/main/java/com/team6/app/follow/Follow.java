package com.team6.app.follow;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// follower가 followee를 팔로우함
// (follower_id, followee_id) 유일 제약이 중복 팔로우를 막고, 피드 조회 색인 역할도 함
@Entity
@Table(name = "follows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follows_follower_followee",
                columnNames = {"follower_id", "followee_id"}),
        indexes = @Index(name = "idx_follows_followee", columnList = "followee_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Follow(User follower, User followee) {
        this.follower = follower;
        this.followee = followee;
        this.createdAt = LocalDateTime.now();
    }
}
