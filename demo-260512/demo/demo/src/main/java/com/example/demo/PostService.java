package com.example.demo;

import com.example.demo.PostDto;
import com.example.demo.Post;
import com.example.demo.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    /**
     * 벌크 저장: 한 건씩 처리하여 중복(stock_code + title + timestamp)은 스킵.
     * 동시성/중복 상황에 견고.
     */
    public PostDto.BulkResponse saveBulk(List<PostDto.PostRequest> requests) {
        int inserted = 0;
        int skipped = 0;

        for (PostDto.PostRequest req : requests) {
            if (trySaveOne(req)) {
                inserted++;
            } else {
                skipped++;
            }
        }

        log.info("[PostService] bulk 저장 완료 - 총 {}건 / 삽입 {}건 / 중복 스킵 {}건",
                requests.size(), inserted, skipped);

        return new PostDto.BulkResponse(requests.size(), inserted, skipped);
    }

    /**
     * 한 건만 별도 트랜잭션으로 저장. 중복(unique 위반)이면 false 반환.
     * REQUIRES_NEW로 각 호출마다 독립 트랜잭션 → 한 건 실패가 다른 건에 영향 없음.
     * DB의 uq_post (stock_code + title + timestamp) 제약에 중복 판단을 위임.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean trySaveOne(PostDto.PostRequest req) {
        try {
            postRepository.saveAndFlush(req.toEntity());
            return true;
        } catch (DataIntegrityViolationException e) {
            // unique 위반 = 이미 있음 = 스킵
            return false;
        }
    }

    /**
     * 단건 저장
     */
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