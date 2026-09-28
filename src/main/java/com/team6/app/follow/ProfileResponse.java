package com.team6.app.follow;

import java.time.LocalDate;

// followerCount: 이 사용자를 팔로우하는 수, followeeCount: 이 사용자가 팔로우하는 수
// following: 보는 사람이 이 사용자를 팔로우 중인지, me: 보는 사람 자신의 프로필인지
public record ProfileResponse(
        Long id,
        String nickname,
        String photoUrl,
        LocalDate joinedAt,
        long workoutCount,
        long followerCount,
        long followeeCount,
        boolean following,
        boolean me) {
}
