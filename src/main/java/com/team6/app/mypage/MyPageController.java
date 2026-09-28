package com.team6.app.mypage;

import com.team6.app.mypage.MyPageDtos.MeResponse;
import com.team6.app.mypage.MyPageDtos.SummaryResponse;
import com.team6.app.mypage.MyPageDtos.UpdateProfileRequest;
import com.team6.app.mypage.MyPageDtos.WeeklyStatsResponse;
import com.team6.app.ranking.WeeklyRankingKey;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 마이페이지: 프로필 수정 · 운동 요약 · 주간 통계. 모두 로그인 필요
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    // 닉네임 수정
    @PatchMapping
    public MeResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return myPageService.updateNickname(userId(jwt), request.nickname());
    }

    // 프로필 사진 올리기 · 바꾸기 (multipart part 이름은 "photo", 최대 5MB, JPG/PNG/WEBP)
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeResponse changePhoto(@AuthenticationPrincipal Jwt jwt, @RequestPart("photo") MultipartFile photo) {
        return myPageService.changePhoto(userId(jwt), photo);
    }

    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@AuthenticationPrincipal Jwt jwt) {
        myPageService.deletePhoto(userId(jwt));
    }

    // 누적 · 이번 주 운동량, 이번 주 순위, 연속 운동일, 종류별 합계
    @GetMapping("/summary")
    public SummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return myPageService.summary(userId(jwt));
    }

    // 11. 내 주간 운동 통계 (date 생략 시 이번 주)
    @GetMapping("/stats/weekly")
    public WeeklyStatsResponse weekly(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return myPageService.weekly(userId(jwt), date != null ? date : LocalDate.now(WeeklyRankingKey.ZONE));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
