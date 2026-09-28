package com.team6.app.follow;

// 사람 목록 한 줄 (검색 · 팔로워 · 팔로잉). following은 내가 이 사용자를 팔로우 중인지
public record UserFollowResponse(Long id, String nickname, String photoUrl, boolean following) {
}
