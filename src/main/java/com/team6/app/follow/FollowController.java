package com.team6.app.follow;

import com.team6.app.workout.WorkoutResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // 8. 팔로우
    @PostMapping("/users/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        followService.follow(Long.valueOf(jwt.getSubject()), id);
    }

    // 9. 언팔로우
    @DeleteMapping("/users/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        followService.unfollow(Long.valueOf(jwt.getSubject()), id);
    }

    // 10. 피드 (팔로우한 사람들 기록 최신순 20개)
    @GetMapping("/feed")
    public List<WorkoutResponse> feed(@RequestParam(defaultValue = "0") int page,
                                      @AuthenticationPrincipal Jwt jwt) {
        return followService.feed(Long.valueOf(jwt.getSubject()), page);
    }
}
