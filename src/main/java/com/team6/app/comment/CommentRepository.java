package com.team6.app.comment;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 오래된 순(등록순)으로 조회. 작성자를 함께 가져와(join fetch) N+1을 막음
    @EntityGraph(attributePaths = "author")
    List<Comment> findByWorkout_IdOrderByIdAsc(Long workoutId);

    long countByWorkout_Id(Long workoutId);
}
