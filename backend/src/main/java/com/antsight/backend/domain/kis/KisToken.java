package com.antsight.backend.domain.kis;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "kis_tokens", catalog = "stock_data")
@Getter
@NoArgsConstructor
public class KisToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String environment;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "token_type", length = 20)
    private String tokenType;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Builder
    private KisToken(String environment, String accessToken, String tokenType, LocalDateTime expiresAt) {
        this.environment = environment;
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.issuedAt = LocalDateTime.now();
    }

    public void update(String accessToken, String tokenType, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.issuedAt = LocalDateTime.now();
    }

    /** 만료 1시간 전부터 만료로 간주 (안전 마진) */
    public boolean isExpired() {
        return LocalDateTime.now().plusHours(1).isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isExpired();
    }
}
