package com.team6.app.comment;

import com.team6.app.comment.CommentDtos.CreateRequest;
import com.team6.app.comment.CommentDtos.Response;
import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import com.team6.app.workout.Workout;
import com.team6.app.workout.WorkoutRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final WorkoutRepository workoutRepository;
    private final UserRepository userRepository;

    @Transactional
    public Response create(Long workoutId, Long authorId, CreateRequest request) {
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "운동 기록을 찾을 수 없습니다."));
        User author = userRepository.getReferenceById(authorId);
        Comment comment = commentRepository.save(new Comment(workout, author, request.content()));
        return Response.from(comment);
    }

    @Transactional(readOnly = true)
    public List<Response> list(Long workoutId) {
        if (!workoutRepository.existsById(workoutId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "운동 기록을 찾을 수 없습니다.");
        }
        return commentRepository.findByWorkout_IdOrderByIdAsc(workoutId).stream()
                .map(Response::from)
                .toList();
    }

    @Transactional
    public void delete(Long commentId, Long currentUserId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
        if (!comment.getAuthor().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 댓글만 삭제할 수 있습니다.");
        }
        commentRepository.delete(comment);
    }
}
