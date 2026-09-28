package com.team6.app.account;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    public record WithdrawRequest(String password) {
    }

    // 회원 탈퇴 (비밀번호 확인). 기록 · 사진 · 댓글 · 팔로우 · 랭킹 점수까지 모두 삭제
    @DeleteMapping("/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal Jwt jwt, @RequestBody WithdrawRequest request) {
        accountService.withdraw(Long.valueOf(jwt.getSubject()), request.password());
    }
}
