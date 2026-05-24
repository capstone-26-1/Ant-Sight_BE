package com.antsight.backend.domain.antindex.dto;

import com.antsight.backend.domain.antindex.Sentiment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AntIndexSeriesResponse(
        String ticker,
        String interval,
        List<Point> points
) {
    public record Point(
            LocalDateTime timestamp,
            BigDecimal score,
            Sentiment sentiment,
            Long postCount
    ) {}
}
