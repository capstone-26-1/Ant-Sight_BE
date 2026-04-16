package com.example.demo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "posts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_post",
            columnNames = {"stock_code", "title", "timestamp"}
        )
    },
    indexes = {
        @Index(name = "ix_stock_code",      columnList = "stock_code"),
        @Index(name = "ix_stock_timestamp", columnList = "stock_code, timestamp")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(length = 100)
    private String writer;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(length = 30)
    private String timestamp;   // "YYYY.MM.DD HH:MM" 원본 형식 보존

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int likes;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int dislikes;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int views;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int comments;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
