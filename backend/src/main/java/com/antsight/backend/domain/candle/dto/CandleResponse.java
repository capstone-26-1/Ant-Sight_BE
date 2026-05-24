package com.antsight.backend.domain.candle.dto;

import com.antsight.backend.domain.candle.Candle;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CandleResponse(
        String ticker,
        LocalDateTime candleTime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        Long volume
) {
    public static CandleResponse fromEntity(Candle c) {
        return new CandleResponse(
                c.getTicker(),
                c.getCandleTime(),
                c.getOpenPrice(),
                c.getHighPrice(),
                c.getLowPrice(),
                c.getClosePrice(),
                c.getVolume()
        );
    }
}
