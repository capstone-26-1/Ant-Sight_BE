package com.antsight.backend.domain.voljump;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VoljumpAlertRepository
        extends JpaRepository<VoljumpAlert, VoljumpAlert.VoljumpAlertId> {

    /**
     * VALID 상태의 가장 최신 base_day.
     * 주말·휴일이면 직전 영업일 반환.
     */
    @Query(value = """
            SELECT MAX(base_day)
            FROM ant_index.voljump_alert
            WHERE status = 'VALID'
            """, nativeQuery = true)
    Optional<LocalDate> findLatestValidBaseDay();

    /**
     * /today — 최신 base_day 의 VALID 행, jump_prob >= min_prob.
     * 정렬: jump_prob DESC, posts DESC, stock_code ASC.
     */
    @Query(value = """
            SELECT
              v.stock_code AS ticker,
              v.base_day   AS baseDay,
              v.scored_at  AS scoredAt,
              v.jump_prob  AS jumpProb,
              v.posts      AS posts,
              v.disp       AS disp,
              v.status     AS status
            FROM ant_index.voljump_alert v
            WHERE v.base_day = :baseDay
              AND v.status = 'VALID'
              AND v.jump_prob >= :minProb
            ORDER BY v.jump_prob DESC, v.posts DESC, v.stock_code ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<VoljumpAlertView> findTodayItems(@Param("baseDay") LocalDate baseDay,
                                          @Param("minProb") BigDecimal minProb,
                                          @Param("limit") int limit);

    /**
     * /{ticker} — 종목의 가장 최신 base_day 1행 (status 무관).
     */
    @Query(value = """
            SELECT
              v.stock_code AS ticker,
              v.base_day   AS baseDay,
              v.scored_at  AS scoredAt,
              v.jump_prob  AS jumpProb,
              v.posts      AS posts,
              v.disp       AS disp,
              v.status     AS status
            FROM ant_index.voljump_alert v
            WHERE v.stock_code = :stockCode
            ORDER BY v.base_day DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<VoljumpAlertView> findLatestByTicker(@Param("stockCode") String stockCode);

    /**
     * /history/{ticker} — 최근 :days 일.
     * status 무관, base_day ASC.
     */
    @Query(value = """
            SELECT
              v.base_day  AS baseDay,
              v.jump_prob AS jumpProb,
              v.status    AS status
            FROM ant_index.voljump_alert v
            WHERE v.stock_code = :stockCode
              AND v.base_day >= DATE_SUB(CURDATE(), INTERVAL :days DAY)
            ORDER BY v.base_day ASC
            """, nativeQuery = true)
    List<VoljumpHistoryView> findHistory(@Param("stockCode") String stockCode,
                                         @Param("days") int days);
}
