package com.antsight.backend.domain.voljump.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VoljumpHistoryResponse(
        String ticker,
        List<Point> points
) {
    public record Point(
            LocalDate baseDay,
            BigDecimal jumpProb,
            String status
    ) {}
}
