package com.team6.app.mypage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// 프로필 사진 파일을 인증사진과 같은 로컬 볼륨(app.upload-dir)에 저장함. /photos/{key}로 조회됨
@Slf4j
@Component
public class ProfilePhotoStorage {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadDir;

    public ProfilePhotoStorage(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.uploadDir = Paths.get(uploadDir);
        Files.createDirectories(this.uploadDir);
    }

    // 검증 후 저장하고 저장 키를 돌려줌
    public String save(Long userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 5MB를 넘을 수 없습니다.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JPG, PNG, WEBP 파일만 업로드할 수 있습니다.");
        }
        String key = "profile_" + userId + "_" + UUID.randomUUID() + extension(file.getContentType());
        try {
            file.transferTo(uploadDir.resolve(key));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "사진 저장에 실패했습니다.", e);
        }
        return key;
    }

    // 파일 삭제 실패로 요청 전체를 막지 않음. 남은 파일은 추후 정리 대상
    public void delete(String key) {
        if (key == null) {
            return;
        }
        try {
            Files.deleteIfExists(uploadDir.resolve(key));
        } catch (IOException e) {
            log.warn("프로필 사진 삭제 실패: {}", key, e);
        }
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
