package com.team6.app.auth;

import com.team6.app.auth.AuthDtos.LoginRequest;
import com.team6.app.auth.AuthDtos.SignupRequest;
import com.team6.app.auth.AuthDtos.SignupResponse;
import com.team6.app.auth.AuthDtos.TokenResponse;
import com.team6.app.user.User;
import com.team6.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }
        User user = userRepository.save(new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()));
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        // 이메일 없음과 비밀번호 불일치를 같은 메시지로 응답해 계정 존재 여부를 노출하지 않음
        User user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));
        String token = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        return new TokenResponse(token, "Bearer", jwtTokenProvider.getExpirationSeconds());
    }
}
