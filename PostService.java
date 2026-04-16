package com.example.demo;

import com.example.demo.PostDto;
import com.example.demo.Post;
import com.example.demo.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    /**
     * 벌크 저장: 중복(stock_code + title + timestamp)은 스킵
     */
    @Transactional
    public PostDto.BulkResponse saveBulk(List<PostDto.PostRequest> requests) {

        List<Post> toInsert = new ArrayList<>();
        int skipped = 0;

        for (PostDto.PostRequest req : requests) {
            boolean exists = postRepository.existsByStockCodeAndTitleAndTimestamp(
                req.getStockCode(), req.getTitle(), req.getTimestamp()
            );
            if (exists) {
                skipped++;
            } else {
                toInsert.add(req.toEntity());
            }
        }

        if (!toInsert.isEmpty()) {
            postRepository.saveAll(toInsert);
        }

        log.info("[PostService] bulk 저장 완료 - 총 {}건 / 삽입 {}건 / 중복 스킵 {}건",
            requests.size(), toInsert.size(), skipped);

        return new PostDto.BulkResponse(requests.size(), toInsert.size(), skipped);
    }

    /**
     * 단건 저장
     */
    @Transactional
    public PostDto.BulkResponse saveSingle(PostDto.PostRequest request) {
        return saveBulk(List.of(request));
    }

    /**
     * 전체 저장 현황 조회
     */
    @Transactional(readOnly = true)
    public PostDto.ProgressResponse getProgress() {
        long totalPosts  = postRepository.count();
        long totalStocks = postRepository.countDistinctStockCode();
        return new PostDto.ProgressResponse(totalPosts, totalStocks);
    }

    /**
     * 종목별 저장 건수 TOP 50
     */
    @Transactional(readOnly = true)
    public List<PostDto.StockStatResponse> getStats() {
        return postRepository.countGroupByStockCode()
            .stream()
            .limit(50)
            .map(row -> new PostDto.StockStatResponse(
                (String) row[0],
                (Long)   row[1]
            ))
            .toList();
    }
}
