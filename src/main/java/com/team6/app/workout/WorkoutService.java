package com.team6.app.workout;

import com.team6.app.ranking.RankingService;
import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import com.team6.app.workout.WorkoutDtos.CreateRequest;
import com.team6.app.workout.WorkoutDtos.PhotoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutRepository workoutRepository;
    private final WorkoutPhotoService workoutPhotoService;
    private final UserRepository userRepository;
    private final RankingService rankingService;

    @Transactional
    public WorkoutResponse create(Long userId, CreateRequest request) {
        User user = userRepository.getReferenceById(userId);
        Workout workout = workoutRepository.save(new Workout(
                user, request.type(), request.durationMin(), request.distanceKm(),
                request.memo(), request.workoutDate()));
        rankingService.addScore(userId, workout.getDurationMin(), workout.getWorkoutDate());
        return WorkoutResponse.from(workout);
    }

    @Transactional(readOnly = true)
    public WorkoutResponse getDetail(Long workoutId) {
        Workout workout = workoutRepository.findWithUserById(workoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "운동 기록을 찾을 수 없습니다."));
        String photoUrl = workoutPhotoService.findPhotoUrl(workoutId).orElse(null);
        return WorkoutResponse.from(workout, photoUrl);
    }

    @Transactional(readOnly = true)
    public Page<WorkoutResponse> getUserWorkouts(Long userId, Pageable pageable) {
        return workoutRepository.findByUser_IdOrderByIdDesc(userId, pageable)
                .map(w -> WorkoutResponse.from(w, workoutPhotoService.findPhotoUrl(w.getId()).orElse(null)));
    }

    @Transactional
    public void delete(Long workoutId, Long currentUserId) {
        Workout workout = getOrThrow(workoutId);
        if (!workout.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 기록만 삭제할 수 있습니다.");
        }
        workoutPhotoService.deleteByWorkoutId(workoutId);
        workoutRepository.delete(workout);
        rankingService.subtractScore(currentUserId, workout.getDurationMin(), workout.getWorkoutDate());
    }

    @Transactional
    public PhotoResponse attachPhoto(Long workoutId, Long currentUserId, MultipartFile file) {
        Workout workout = getOrThrow(workoutId);
        if (!workout.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 기록에만 사진을 첨부할 수 있습니다.");
        }
        WorkoutPhoto photo = workoutPhotoService.attach(workoutId, file);
        return new PhotoResponse("/photos/" + photo.getStoredKey(), photo.getStoredKey());
    }

    private Workout getOrThrow(Long id) {
        return workoutRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "운동 기록을 찾을 수 없습니다."));
    }
}
