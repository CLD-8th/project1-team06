package com.team6.app.follow;

import com.team6.app.workout.Workout;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // 조회 후 삭제하지 않고 한 번에 지움. 지운 행 수를 돌려받음
    @Modifying(clearAutomatically = true)
    @Query("delete from Follow f where f.follower.id = :followerId and f.following.id = :followingId")
    int deleteByPair(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    // 팔로우한 사람들의 기록을 최신순으로 가져옴
    // 작성자를 함께 가져와(join fetch) 목록 건수만큼 조회가 늘어나지 않게 함
    @Query("""
            select w from Workout w
            join fetch w.user
            where w.user.id in (
                select f.following.id from Follow f where f.follower.id = :followerId)
            order by w.workoutDate desc, w.id desc
            """)
    List<Workout> findFeed(@Param("followerId") Long followerId, Pageable pageable);
}
