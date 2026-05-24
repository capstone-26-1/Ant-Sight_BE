package com.antsight.backend.domain.antindex;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AntIndexScoreRepository extends JpaRepository<AntIndexScore, Long> {

    Optional<AntIndexScore> findTopByTickerOrderByTimestampDesc(String ticker);

    /**
     * 최신 스코어 1건 + raw_data 의 post_count 추출.
     */
    @Query(value = """
            SELECT
              s.ticker    AS ticker,
              s.timestamp AS timestamp,
              s.score     AS score,
              s.sentiment AS sentiment,
              COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.raw_data, '$.post_count')) AS UNSIGNED), 0) AS postCount
            FROM ant_index.scores s
            WHERE s.ticker = :ticker
            ORDER BY s.timestamp DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<AntIndexLatestView> findLatestView(@Param("ticker") String ticker);

    /**
     * 15분 버킷 그대로. raw_data JSON 에서 post_count 추출.
     */
    @Query(value = """
            SELECT
              s.timestamp AS bucketTs,
              s.score     AS avgScore,
              COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.raw_data, '$.post_count')) AS UNSIGNED), 0) AS postCount
            FROM ant_index.scores s
            WHERE s.ticker = :ticker
              AND s.timestamp >= :from
              AND s.timestamp <= :to
            ORDER BY s.timestamp ASC
            """, nativeQuery = true)
    List<AntIndexBucketView> findSeries15m(@Param("ticker") String ticker,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * 1시간 다운샘플링. AVG(score), SUM(post_count). 버킷은 시간 floor.
     */
    @Query(value = """
            SELECT
              STR_TO_DATE(DATE_FORMAT(s.timestamp, '%Y-%m-%d %H:00:00'), '%Y-%m-%d %H:%i:%s') AS bucketTs,
              AVG(s.score) AS avgScore,
              SUM(COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.raw_data, '$.post_count')) AS UNSIGNED), 0)) AS postCount
            FROM ant_index.scores s
            WHERE s.ticker = :ticker
              AND s.timestamp >= :from
              AND s.timestamp <= :to
            GROUP BY DATE_FORMAT(s.timestamp, '%Y-%m-%d %H:00:00')
            ORDER BY bucketTs ASC
            """, nativeQuery = true)
    List<AntIndexBucketView> findSeries1h(@Param("ticker") String ticker,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * 1일 다운샘플링. AVG(score), SUM(post_count). 버킷은 날짜 floor.
     */
    @Query(value = """
            SELECT
              CAST(DATE(s.timestamp) AS DATETIME) AS bucketTs,
              AVG(s.score) AS avgScore,
              SUM(COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.raw_data, '$.post_count')) AS UNSIGNED), 0)) AS postCount
            FROM ant_index.scores s
            WHERE s.ticker = :ticker
              AND s.timestamp >= :from
              AND s.timestamp <= :to
            GROUP BY DATE(s.timestamp)
            ORDER BY bucketTs ASC
            """, nativeQuery = true)
    List<AntIndexBucketView> findSeries1d(@Param("ticker") String ticker,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * 윈도우 구간 동안의 ticker 별 AVG(score), SUM(post_count).
     * post_count 합이 0 인 종목은 제외 (의미 있는 윈도우만).
     * direction=positive → AVG DESC, negative → AVG ASC.
     */
    @Query(value = """
            SELECT
              s.ticker AS ticker,
              AVG(s.score) AS avgScore,
              SUM(COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.raw_data, '$.post_count')) AS UNSIGNED), 0)) AS postCount
            FROM ant_index.scores s
            WHERE s.timestamp >= :from
            GROUP BY s.ticker
            HAVING SUM(COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.raw_data, '$.post_count')) AS UNSIGNED), 0)) > 0
            ORDER BY avgScore DESC
            """, nativeQuery = true)
    List<AntIndexRankingView> findRankingPositive(@Param("from") LocalDateTime from,
                                                  Pageable pageable);

    @Query(value = """
            SELECT
              s.ticker AS ticker,
              AVG(s.score) AS avgScore,
              SUM(COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.raw_data, '$.post_count')) AS UNSIGNED), 0)) AS postCount
            FROM ant_index.scores s
            WHERE s.timestamp >= :from
            GROUP BY s.ticker
            HAVING SUM(COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.raw_data, '$.post_count')) AS UNSIGNED), 0)) > 0
            ORDER BY avgScore ASC
            """, nativeQuery = true)
    List<AntIndexRankingView> findRankingNegative(@Param("from") LocalDateTime from,
                                                  Pageable pageable);
}
