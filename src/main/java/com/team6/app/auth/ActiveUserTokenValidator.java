package com.team6.app.auth;

import com.team6.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

// 토큰의 subject(userId)가 지금도 존재하는 회원인지 확인함.
// JWT는 서버에 저장하지 않아 만료 전까지 유효하므로, 탈퇴한 회원의 토큰을 바로 막으려고 요청마다 확인함 (기본키 조회 1번)
@Component
@RequiredArgsConstructor
public class ActiveUserTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_USER =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "탈퇴했거나 존재하지 않는 회원의 토큰입니다.", null);

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            return userRepository.existsById(Long.valueOf(jwt.getSubject()))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(INVALID_USER);
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(INVALID_USER);
        }
    }
}
