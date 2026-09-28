package com.team6.app.workout;

import com.team6.app.workout.WorkoutDtos.CreateRequest;
import com.team6.app.workout.WorkoutDtos.PhotoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// GET /workouts/**, GET /users/*/workouts는 SecurityConfig에서 인증 없이 허용함.
// POST/DELETE는 anyRequest().authenticated()에 걸려 JWT가 필요함.
@RestController
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;

    @PostMapping("/workouts")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        return workoutService.create(currentUserId(jwt), request);
    }

    @GetMapping("/workouts/{id}")
    public WorkoutResponse detail(@PathVariable Long id) {
        return workoutService.getDetail(id);
    }

    @GetMapping("/users/{userId}/workouts")
    public Page<WorkoutResponse> userWorkouts(@PathVariable Long userId,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return workoutService.getUserWorkouts(userId, pageable);
    }

    @DeleteMapping("/workouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        workoutService.delete(id, currentUserId(jwt));
    }

    // multipart part 이름은 "photo"
    @PostMapping(value = "/workouts/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse uploadPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                      @RequestPart("photo") MultipartFile photo) {
        return workoutService.attachPhoto(id, currentUserId(jwt), photo);
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
