package com.team6.app.comment;

import com.team6.app.comment.CommentDtos.CreateRequest;
import com.team6.app.comment.CommentDtos.Response;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// GET /workouts/{id}/comments는 SecurityConfig의 "/workouts/**" GET 허용 규칙에 걸려 인증 불필요.
// POST/DELETE는 anyRequest().authenticated()라 JWT가 필요함. SecurityConfig는 따로 안 고쳐도 됨.
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/workouts/{workoutId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long workoutId,
                            @Valid @RequestBody CreateRequest request) {
        return commentService.create(workoutId, currentUserId(jwt), request);
    }

    @GetMapping("/workouts/{workoutId}/comments")
    public List<Response> list(@PathVariable Long workoutId) {
        return commentService.list(workoutId);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        commentService.delete(id, currentUserId(jwt));
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
