package com.antsight.backend.domain.antindex;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AntIndexScoreRepository
        extends JpaRepository<AntIndexScore, AntIndexScore.AntIndexScoreId> {

    /**
     * 메서드 이름 기반 쿼리 - 최신 1건
     * (옛날 findTopByTickerOrderByTimestampDesc 대응)
     */
    Optional<AntIndexScore> findTopByStockCodeOrderByBucketTimeDesc(String stockCode);

    /**
     * 최신 스코어 1건 - 응답 View 형태로
     * raw_data JSON 파싱 → post_count 직접 컬럼
     */
    @Query(value = """
            SELECT
              s.stock_code  AS ticker,
              s.bucket_time AS timestamp,
              s.ant_index   AS score,
              s.post_count  AS postCount
            FROM ant_index.scores_v2 s
            WHERE s.stock_code = :stockCode
            ORDER BY s.bucket_time DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<AntIndexLatestView> findLatestView(@Param("stockCode") String stockCode);

    /**
     * 15분 버킷 (scores_v2가 이미 15분 버킷이라 그대로 SELECT)
     */
    @Query(value = """
            SELECT
              s.bucket_time AS bucketTs,
              s.ant_index   AS avgScore,
              s.post_count  AS postCount
            FROM ant_index.scores_v2 s
            WHERE s.stock_code = :stockCode
              AND s.bucket_time >= :from
              AND s.bucket_time <= :to
            ORDER BY s.bucket_time ASC
            """, nativeQuery = true)
    List<AntIndexBucketView> findSeries15m(@Param("stockCode") String stockCode,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * 1시간 다운샘플링. AVG(ant_index), SUM(post_count). 시간 floor.
     */
    @Query(value = """
            SELECT
              STR_TO_DATE(DATE_FORMAT(s.bucket_time, '%Y-%m-%d %H:00:00'), '%Y-%m-%d %H:%i:%s') AS bucketTs,
              AVG(s.ant_index) AS avgScore,
              SUM(COALESCE(s.post_count, 0)) AS postCount
            FROM ant_index.scores_v2 s
            WHERE s.stock_code = :stockCode
              AND s.bucket_time >= :from
              AND s.bucket_time <= :to
            GROUP BY DATE_FORMAT(s.bucket_time, '%Y-%m-%d %H:00:00')
            ORDER BY bucketTs ASC
            """, nativeQuery = true)
    List<AntIndexBucketView> findSeries1h(@Param("stockCode") String stockCode,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * 1일 다운샘플링. AVG(ant_index), SUM(post_count). 날짜 floor.
     */
    @Query(value = """
            SELECT
              CAST(DATE(s.bucket_time) AS DATETIME) AS bucketTs,
              AVG(s.ant_index) AS avgScore,
              SUM(COALESCE(s.post_count, 0)) AS postCount
            FROM ant_index.scores_v2 s
            WHERE s.stock_code = :stockCode
              AND s.bucket_time >= :from
              AND s.bucket_time <= :to
            GROUP BY DATE(s.bucket_time)
            ORDER BY bucketTs ASC
            """, nativeQuery = true)
    List<AntIndexBucketView> findSeries1d(@Param("stockCode") String stockCode,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * 랭킹 (positive: AVG DESC)
     */
    @Query(value = """
            SELECT
              s.stock_code AS ticker,
              AVG(s.ant_index) AS avgScore,
              SUM(COALESCE(s.post_count, 0)) AS postCount
            FROM ant_index.scores_v2 s
            WHERE s.bucket_time >= :from
            GROUP BY s.stock_code
            HAVING SUM(COALESCE(s.post_count, 0)) > 0
            ORDER BY avgScore DESC
            """, nativeQuery = true)
    List<AntIndexRankingView> findRankingPositive(@Param("from") LocalDateTime from,
                                                  Pageable pageable);

    /**
     * 랭킹 (negative: AVG ASC)
     */
    @Query(value = """
            SELECT
              s.stock_code AS ticker,
              AVG(s.ant_index) AS avgScore,
              SUM(COALESCE(s.post_count, 0)) AS postCount
            FROM ant_index.scores_v2 s
            WHERE s.bucket_time >= :from
            GROUP BY s.stock_code
            HAVING SUM(COALESCE(s.post_count, 0)) > 0
            ORDER BY avgScore ASC
            """, nativeQuery = true)
    List<AntIndexRankingView> findRankingNegative(@Param("from") LocalDateTime from,
                                                  Pageable pageable);
}