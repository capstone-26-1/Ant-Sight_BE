package com.example.demo;

import com.example.demo.PostDto;
import com.example.demo.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * 단건 저장
     * POST /posts
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto.BulkResponse createPost(
        @Valid @RequestBody PostDto.PostRequest request
    ) {
        return postService.saveSingle(request);
    }

    /**
     * 벌크 저장 (Python crawler가 주로 사용)
     * POST /posts/bulk
     * Body: { "posts": [ { "stock_code": "005930", "title": "...", ... }, ... ] }
     */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto.BulkResponse createPostsBulk(
        @Valid @RequestBody PostDto.BulkRequest request
    ) {
        return postService.saveBulk(request.getPosts());
    }

    /**
     * 전체 저장 현황
     * GET /posts/progress
     */
    @GetMapping("/progress")
    public PostDto.ProgressResponse getProgress() {
        return postService.getProgress();
    }

    /**
     * 종목별 저장 건수 TOP 50
     * GET /posts/stats
     */
    @GetMapping("/stats")
    public List<PostDto.StockStatResponse> getStats() {
        return postService.getStats();
    }

    /**
     * 헬스체크
     * GET /posts/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "time", LocalDateTime.now().toString());
    }
}
