package com.team6.app.follow;

import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import com.team6.app.workout.Workout;
import com.team6.app.workout.WorkoutPhotoService;
import com.team6.app.workout.WorkoutResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final int PAGE_SIZE = 20;

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final WorkoutPhotoService workoutPhotoService;

    @Transactional
    public void follow(Long followerId, Long targetId) {
        if (followerId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없습니다.");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, targetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 팔로우 중입니다.");
        }
        // 확인과 저장 사이에 같은 요청이 끼어들면 유일 제약이 막으므로 그 경우도 중복으로 응답함
        try {
            followRepository.saveAndFlush(new Follow(userRepository.getReferenceById(followerId), target));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 팔로우 중입니다.");
        }
    }

    // 팔로우하지 않은 상대여도 204로 응답함. 여러 번 눌러도 결과가 같음
    @Transactional
    public void unfollow(Long followerId, Long targetId) {
        followRepository.deleteByPair(followerId, targetId);
    }

    @Transactional(readOnly = true)
    public List<WorkoutResponse> feed(Long userId, int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        List<Workout> workouts = followRepository.findFeed(userId, PageRequest.of(page, PAGE_SIZE));
        // 사진 URL을 기록마다 따로 조회하지 않고, 이 페이지에 나온 기록들 것만 한 번에 가져옴 (A 파트)
        Map<Long, String> photoUrls = workoutPhotoService.findPhotoUrls(
                workouts.stream().map(Workout::getId).toList());
        return workouts.stream()
                .map(w -> WorkoutResponse.from(w, photoUrls.get(w.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserFollowResponse> users(Long userId, String keyword, int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.strip();
        return withFollowing(userId, followRepository.findOthers(userId, trimmed, PageRequest.of(page, PAGE_SIZE)));
    }

    // 팔로워 목록 (userId를 팔로우하는 사람들). 각 줄에 보는 사람의 팔로우 여부를 붙임
    @Transactional(readOnly = true)
    public List<UserFollowResponse> followers(Long viewerId, Long userId, int page) {
        checkListRequest(userId, page);
        return withFollowing(viewerId, followRepository.findFollowers(userId, PageRequest.of(page, PAGE_SIZE)));
    }

    // 팔로잉 목록 (userId가 팔로우하는 사람들)
    @Transactional(readOnly = true)
    public List<UserFollowResponse> followees(Long viewerId, Long userId, int page) {
        checkListRequest(userId, page);
        return withFollowing(viewerId, followRepository.findFollowees(userId, PageRequest.of(page, PAGE_SIZE)));
    }

    private void checkListRequest(Long userId, int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
    }

    // 목록 20명을 쿼리 1번으로 팔로우 여부 확인
    private List<UserFollowResponse> withFollowing(Long viewerId, List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Set<Long> followeeIds = new HashSet<>(
                followRepository.findFolloweeIds(viewerId, users.stream().map(User::getId).toList()));
        return users.stream()
                .map(u -> new UserFollowResponse(u.getId(), u.getNickname(), u.getPhotoUrl(), followeeIds.contains(u.getId())))
                .toList();
    }

    // 프로필 머리 부분: 기록 · 팔로워 · 팔로잉 수와 내가 팔로우 중인지
    @Transactional(readOnly = true)
    public ProfileResponse profile(Long viewerId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return new ProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getPhotoUrl(),
                user.getCreatedAt().toLocalDate(),
                followRepository.countWorkouts(userId),
                followRepository.countByFolloweeId(userId),
                followRepository.countByFollowerId(userId),
                followRepository.existsByFollowerIdAndFolloweeId(viewerId, userId),
                viewerId.equals(userId));
    }
}
