package com.antsight.backend.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, String> {

    List<Stock> findByIsActiveTrue();

    /**
     * 검색 정렬 우선순위:
     * 1. 티커 정확 일치
     * 2. 티커 prefix 매치
     * 3. 종목명 부분 포함
     * 동일 그룹 내 market_cap_rank 오름차순
     */
    @Query("""
            SELECT s FROM Stock s
            WHERE s.isActive = true
              AND (UPPER(s.ticker) = UPPER(:query)
                   OR UPPER(s.ticker) LIKE UPPER(CONCAT(:query, '%'))
                   OR s.name LIKE CONCAT('%', :query, '%'))
            ORDER BY
              CASE
                WHEN UPPER(s.ticker) = UPPER(:query) THEN 0
                WHEN UPPER(s.ticker) LIKE UPPER(CONCAT(:query, '%')) THEN 1
                ELSE 2
              END,
              s.marketCapRank ASC NULLS LAST
            LIMIT :limit
            """)
    List<Stock> searchByTickerOrName(@Param("query") String query, @Param("limit") int limit);
}
