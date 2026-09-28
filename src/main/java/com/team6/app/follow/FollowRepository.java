package com.team6.app.follow;

import com.team6.app.user.User;
import com.team6.app.workout.Workout;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    // 조회 후 삭제하지 않고 한 번에 지움. 지운 행 수를 돌려받음
    @Modifying(clearAutomatically = true)
    @Query("delete from Follow f where f.follower.id = :followerId and f.followee.id = :followeeId")
    int deleteByPair(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    // 팔로우한 사람들의 기록을 최신순으로 가져옴
    // 작성자를 함께 가져와(join fetch) 목록 건수만큼 조회가 늘어나지 않게 함
    @Query("""
            select w from Workout w
            join fetch w.user
            where w.user.id in (
                select f.followee.id from Follow f where f.follower.id = :followerId)
            order by w.workoutDate desc, w.id desc
            """)
    List<Workout> findFeed(@Param("followerId") Long followerId, Pageable pageable);

    // 사람 찾기: 나를 뺀 사용자 최신 가입순, keyword가 있으면 닉네임에 포함된 사용자만
    // UserRepository를 건드리지 않으려고 여기에 둠
    @Query("""
            select u from User u
            where u.id <> :userId
              and (:keyword is null or lower(u.nickname) like lower(concat('%', :keyword, '%')))
            order by u.id desc
            """)
    List<User> findOthers(@Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);

    // 이 사용자를 팔로우하는 사람들 (최근 팔로우 순)
    @Query("select f.follower from Follow f where f.followee.id = :userId order by f.id desc")
    List<User> findFollowers(@Param("userId") Long userId, Pageable pageable);

    // 이 사용자가 팔로우하는 사람들 (최근 팔로우 순)
    @Query("select f.followee from Follow f where f.follower.id = :userId order by f.id desc")
    List<User> findFollowees(@Param("userId") Long userId, Pageable pageable);

    long countByFollowerId(Long followerId);

    long countByFolloweeId(Long followeeId);

    // 프로필의 기록 수. 기록 쪽 저장소(A 파트)를 건드리지 않으려고 여기에 둠
    @Query("select count(w) from Workout w where w.user.id = :userId")
    long countWorkouts(@Param("userId") Long userId);

    // 주어진 사용자들 중 내가 팔로우 중인 id만 골라냄 (목록 20명을 쿼리 1번으로 확인)
    @Query("select f.followee.id from Follow f where f.follower.id = :followerId and f.followee.id in :ids")
    List<Long> findFolloweeIds(@Param("followerId") Long followerId, @Param("ids") List<Long> ids);
}
