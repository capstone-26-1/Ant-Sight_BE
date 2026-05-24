package com.antsight.backend.domain.antindex;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts", catalog = "stockboard")
@Getter
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", length = 10)
    private String stockCode;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String text;

    @Column(length = 30)
    private String timestamp;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ant_index", precision = 5, scale = 2)
    private BigDecimal antIndex;

    @Column(name = "ai_processed_at")
    private LocalDateTime aiProcessedAt;
}
