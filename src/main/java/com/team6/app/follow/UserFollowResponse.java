package com.team6.app.follow;

// 사람 찾기 목록 한 줄. following은 내가 이 사용자를 팔로우 중인지
public record UserFollowResponse(Long id, String nickname, boolean following) {
}
