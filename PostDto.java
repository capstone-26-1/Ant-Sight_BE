package com.example.demo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.demo.Post;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// ── Request ──────────────────────────────────────────────────────

public class PostDto {

    @Getter
    @NoArgsConstructor
    public static class BulkRequest {

        @NotNull
        @Size(min = 1, max = 1000, message = "1~1000건 사이로 요청해주세요.")
        @Valid
        private List<PostRequest> posts;
    }

    @Getter
    @NoArgsConstructor
    public static class PostRequest {

        @NotBlank
        @JsonProperty("stock_code")
        private String stockCode;

        private String writer;

        @NotBlank
        private String title;

        private String text;

        private String timestamp;

        private int likes;
        private int dislikes;
        private int views;
        private int comments;

        public Post toEntity() {
            return Post.builder()
                .stockCode(stockCode)
                .writer(writer    != null ? writer    : "")
                .title(title)
                .text(text        != null ? text      : "")
                .timestamp(timestamp != null ? timestamp : "")
                .likes(likes)
                .dislikes(dislikes)
                .views(views)
                .comments(comments)
                .build();
        }
    }

    // ── Response ─────────────────────────────────────────────────

    public record BulkResponse(
        int total,
        int inserted,
        int skipped
    ) {}

    public record ProgressResponse(
        long totalPosts,
        long totalStocks
    ) {}

    public record StockStatResponse(
        String stockCode,
        long count
    ) {}
}
