package com.antsight.backend.domain.bookmark;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    List<Bookmark> findByUserId(Long userId);

    boolean existsByUserIdAndTicker(Long userId, String ticker);

    // 삭제 후 영향 행 수를 반환 — 0이면 존재하지 않는 북마크
    int deleteByUserIdAndTicker(Long userId, String ticker);
}
