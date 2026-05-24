package com.antsight.backend.domain.user.dto;

import com.antsight.backend.domain.user.User;
import com.antsight.backend.domain.user.UserRole;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserSummary {

    private final Long id;
    private final String email;
    private final String nickname;
    private final UserRole role;
    private final boolean isApproved;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastLoginAt;

    private UserSummary(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.nickname = user.getNickname();
        this.role = user.getRole();
        this.isApproved = user.isApproved();
        this.createdAt = user.getCreatedAt();
        this.lastLoginAt = user.getLastLoginAt();
    }

    public static UserSummary from(User user) {
        return new UserSummary(user);
    }
}
