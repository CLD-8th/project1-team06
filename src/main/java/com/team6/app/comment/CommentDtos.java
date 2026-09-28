package com.team6.app.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class CommentDtos {

    private CommentDtos() {
    }

    public record CreateRequest(@NotBlank @Size(max = 300) String content) {
    }

    public record Response(
            Long id, Long workoutId, Long userId, String nickname, String content, LocalDateTime createdAt) {

        static Response from(Comment c) {
            return new Response(
                    c.getId(),
                    c.getWorkout().getId(),
                    c.getAuthor().getId(),
                    c.getAuthor().getNickname(),
                    c.getContent(),
                    c.getCreatedAt());
        }
    }
}
