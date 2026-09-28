package com.team6.app.workout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// 인증사진 파일을 로컬 볼륨(app.upload-dir)에 저장함. 기록당 1장만 허용함
@Slf4j
@Service
public class WorkoutPhotoService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadDir;
    private final WorkoutPhotoRepository workoutPhotoRepository;

    public WorkoutPhotoService(@Value("${app.upload-dir}") String uploadDir,
                                WorkoutPhotoRepository workoutPhotoRepository) throws IOException {
        this.uploadDir = Paths.get(uploadDir);
        Files.createDirectories(this.uploadDir);
        this.workoutPhotoRepository = workoutPhotoRepository;
    }

    @Transactional
    public WorkoutPhoto attach(Long workoutId, MultipartFile file) {
        validate(file);
        // 기존 사진이 있으면 파일과 행을 먼저 지우고 새로 저장함 (1장 제한)
        workoutPhotoRepository.findByWorkoutId(workoutId).ifPresent(this::deleteFileAndRow);

        String storedKey = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
        try {
            file.transferTo(uploadDir.resolve(storedKey));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "사진 저장에 실패했습니다.", e);
        }
        return workoutPhotoRepository.save(
                new WorkoutPhoto(workoutId, storedKey, file.getOriginalFilename(), file.getSize()));
    }

    @Transactional
    public void deleteByWorkoutId(Long workoutId) {
        workoutPhotoRepository.findByWorkoutId(workoutId).ifPresent(this::deleteFileAndRow);
    }

    @Transactional(readOnly = true)
    public Optional<String> findPhotoUrl(Long workoutId) {
        return workoutPhotoRepository.findByWorkoutId(workoutId).map(p -> "/photos/" + p.getStoredKey());
    }

    private void deleteFileAndRow(WorkoutPhoto photo) {
        try {
            Files.deleteIfExists(uploadDir.resolve(photo.getStoredKey()));
        } catch (IOException e) {
            // 파일 삭제 실패로 전체 요청을 막지 않음. 고아 파일은 추후 정리 대상으로 남김
            log.warn("사진 파일 삭제 실패: {}", photo.getStoredKey(), e);
        }
        workoutPhotoRepository.delete(photo);
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 5MB를 넘을 수 없습니다.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JPG, PNG, WEBP 파일만 업로드할 수 있습니다.");
        }
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
