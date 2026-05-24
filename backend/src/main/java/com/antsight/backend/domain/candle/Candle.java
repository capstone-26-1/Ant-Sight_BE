package com.antsight.backend.domain.candle;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "candles_5min", catalog = "stock_data")
@Getter
@NoArgsConstructor
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(name = "candle_time", nullable = false)
    private LocalDateTime candleTime;

    @Column(name = "open_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private Long volume;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Candle(String ticker, LocalDateTime candleTime,
                   BigDecimal openPrice, BigDecimal highPrice,
                   BigDecimal lowPrice, BigDecimal closePrice,
                   Long volume) {
        this.ticker = ticker;
        this.candleTime = candleTime;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }
}
