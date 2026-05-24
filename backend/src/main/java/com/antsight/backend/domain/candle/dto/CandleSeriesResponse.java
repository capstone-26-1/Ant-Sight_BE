package com.antsight.backend.domain.candle.dto;

import com.antsight.backend.domain.antindex.Sentiment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CandleSeriesResponse(
        String ticker,
        boolean withAntIndex,
        List<Point> points
) {
    public record Point(
            LocalDateTime candleTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume,
            BigDecimal antIndex,
            Sentiment sentiment
    ) {}
}
