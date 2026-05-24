package com.antsight.backend.domain.bookmark;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "bookmarks",
    schema = "members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ticker"})
)
@Getter
@NoArgsConstructor
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Bookmark(Long userId, String ticker) {
        this.userId = userId;
        this.ticker = ticker;
        this.createdAt = LocalDateTime.now();
    }
}
