package com.antsight.backend.domain.auth;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.config.JwtUtil;
import com.antsight.backend.domain.auth.dto.AuthResponse;
import com.antsight.backend.domain.auth.dto.LoginRequest;
import com.antsight.backend.domain.auth.dto.SignupRequest;
import com.antsight.backend.domain.user.User;
import com.antsight.backend.domain.user.UserRepository;
import com.antsight.backend.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .role(UserRole.USER)
                .build();

        userRepository.save(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.canLogin()) {
            throw new ApiException(ErrorCode.USER_NOT_APPROVED);
        }

        user.recordLogin();

        String token = jwtUtil.generateToken(user);
        return AuthResponse.of(token, user);
    }
}
