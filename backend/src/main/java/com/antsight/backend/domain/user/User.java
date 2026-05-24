package com.antsight.backend.domain.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", schema = "members")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role;

    @Column(name = "is_approved", nullable = false)
    private boolean isApproved;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder
    private User(String email, String passwordHash, String nickname, UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = role;
        this.isApproved = true;
        this.createdAt = LocalDateTime.now();
    }

    public boolean canLogin() {
        return this.isApproved;
    }

    public void block() {
        this.isApproved = false;
    }

    public void unblock(Long adminId) {
        this.isApproved = true;
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = adminId;
    }

    // 하위 호환용 — AdminInitializer에서 사용
    public void approve(Long adminId) {
        unblock(adminId);
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
