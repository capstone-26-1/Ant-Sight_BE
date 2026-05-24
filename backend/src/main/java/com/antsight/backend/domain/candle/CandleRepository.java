package com.antsight.backend.domain.candle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CandleRepository extends JpaRepository<Candle, Long> {

    List<Candle> findByTickerOrderByCandleTimeDesc(String ticker);

    List<Candle> findByTickerAndCandleTimeBetweenOrderByCandleTimeAsc(
            String ticker, LocalDateTime from, LocalDateTime to);

    /**
     * 5분봉 upsert. UNIQUE(ticker, candle_time) 충돌 시 OHLCV 갱신.
     * Collector 재실행/중복 호출에 멱등 보장.
     */
    @Modifying
    @Query(value = """
            INSERT INTO stock_data.candles_5min
                (ticker, candle_time, open_price, high_price, low_price, close_price, volume)
            VALUES (:ticker, :candleTime, :openPrice, :highPrice, :lowPrice, :closePrice, :volume)
            ON DUPLICATE KEY UPDATE
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                volume = VALUES(volume)
            """, nativeQuery = true)
    void upsert(@Param("ticker") String ticker,
                @Param("candleTime") LocalDateTime candleTime,
                @Param("openPrice") BigDecimal openPrice,
                @Param("highPrice") BigDecimal highPrice,
                @Param("lowPrice") BigDecimal lowPrice,
                @Param("closePrice") BigDecimal closePrice,
                @Param("volume") Long volume);

    /**
     * 5분봉 + 같은 15분 버킷의 ant_index.scores LEFT JOIN.
     * 매칭 키는 candle_time 의 15분 floor (FLOOR(unix/900)*900).
     * 매칭 안 되면 antIndex/sentiment 는 null.
     */
    @Query(value = """
            SELECT
              c.candle_time AS candleTime,
              c.open_price  AS openPrice,
              c.high_price  AS highPrice,
              c.low_price   AS lowPrice,
              c.close_price AS closePrice,
              c.volume      AS volume,
              s.score       AS antIndex,
              s.sentiment   AS sentiment
            FROM stock_data.candles_5min c
            LEFT JOIN ant_index.scores s
              ON s.ticker = c.ticker
             AND s.timestamp = FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(c.candle_time) / 900) * 900)
            WHERE c.ticker = :ticker
              AND c.candle_time >= :from
              AND c.candle_time <= :to
            ORDER BY c.candle_time ASC
            """, nativeQuery = true)
    List<CandleWithAntIndexView> findRangeWithAntIndex(@Param("ticker") String ticker,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);
}
