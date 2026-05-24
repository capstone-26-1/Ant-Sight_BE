package com.antsight.backend.domain.auth.dto;

import com.antsight.backend.domain.user.User;
import com.antsight.backend.domain.user.UserRole;
import lombok.Getter;

@Getter
public class AuthResponse {

    private final String token;
    private final Long userId;
    private final String email;
    private final String nickname;
    private final UserRole role;

    private AuthResponse(String token, Long userId, String email, String nickname, UserRole role) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
    }

    public static AuthResponse of(String token, User user) {
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }
}
