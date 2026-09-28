package com.team6.app.account;

import com.team6.app.mypage.ProfilePhotoStorage;
import com.team6.app.ranking.WeeklyRankingKey;
import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

// 회원 탈퇴. 사용자와 연결된 데이터를 한 트랜잭션에서 모두 지우고,
// 파일 · Redis 정리는 DB 커밋이 끝난 뒤에 함 (DB가 실패했는데 파일만 사라지는 일이 없도록)
@Slf4j
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;
    private final ProfilePhotoStorage profilePhotoStorage;
    private final StringRedisTemplate redisTemplate;
    private final Path uploadDir;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, EntityManager em,
                          ProfilePhotoStorage profilePhotoStorage, StringRedisTemplate redisTemplate,
                          @Value("${app.upload-dir}") String uploadDir) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.em = em;
        this.profilePhotoStorage = profilePhotoStorage;
        this.redisTemplate = redisTemplate;
        this.uploadDir = Paths.get(uploadDir);
    }

    @Transactional
    public void withdraw(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        // 토큰만 탈취돼도 계정이 지워지지 않게 비밀번호를 한 번 더 확인함
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다.");
        }

        // 지우기 전에 커밋 후 정리할 대상(사진 파일, 랭킹 주차)을 모아 둠
        List<String> photoKeys = em.createQuery("""
                        select p.storedKey from WorkoutPhoto p
                        where p.workoutId in (select w.id from Workout w where w.user.id = :userId)
                        """, String.class)
                .setParameter("userId", userId)
                .getResultList();
        Set<String> rankingKeys = new TreeSet<>();
        em.createQuery("select distinct w.workoutDate from Workout w where w.user.id = :userId", LocalDate.class)
                .setParameter("userId", userId)
                .getResultList()
                .forEach(d -> rankingKeys.add(WeeklyRankingKey.of(d)));
        String profileKey = user.getProfileImageKey();

        // FK 순서대로 지움: 댓글 → 인증사진 → 기록 → 팔로우 → 사용자
        int comments = em.createQuery("""
                        delete from Comment c
                        where c.author.id = :userId
                           or c.workout.id in (select w.id from Workout w where w.user.id = :userId)
                        """)
                .setParameter("userId", userId).executeUpdate();
        em.createQuery("""
                        delete from WorkoutPhoto p
                        where p.workoutId in (select w.id from Workout w where w.user.id = :userId)
                        """)
                .setParameter("userId", userId).executeUpdate();
        int workouts = em.createQuery("delete from Workout w where w.user.id = :userId")
                .setParameter("userId", userId).executeUpdate();
        int follows = em.createQuery("delete from Follow f where f.follower.id = :userId or f.followee.id = :userId")
                .setParameter("userId", userId).executeUpdate();
        em.createQuery("delete from User u where u.id = :userId")
                .setParameter("userId", userId).executeUpdate();
        log.info("회원 탈퇴: userId={}, 기록 {}건, 댓글 {}건, 팔로우 {}건", userId, workouts, comments, follows);

        List<String> files = new ArrayList<>(photoKeys);
        afterCommit(() -> cleanUp(userId, files, profileKey, rankingKeys));
    }

    private void cleanUp(Long userId, List<String> photoKeys, String profileKey, Set<String> rankingKeys) {
        photoKeys.forEach(key -> {
            try {
                Files.deleteIfExists(uploadDir.resolve(key));
            } catch (IOException e) {
                log.warn("인증사진 파일 삭제 실패: {}", key, e);
            }
        });
        profilePhotoStorage.delete(profileKey);
        // 랭킹에서 빼지 못해도 탈퇴는 이미 끝남. 남은 점수는 키 만료(5주)와 함께 사라짐
        try {
            rankingKeys.forEach(key -> redisTemplate.opsForZSet().remove(key, String.valueOf(userId)));
        } catch (DataAccessException e) {
            log.warn("탈퇴 회원 랭킹 정리 실패: userId={}", userId, e);
        }
    }

    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
