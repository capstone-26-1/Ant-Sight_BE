package com.antsight.backend.domain.voljump.dto;

import com.antsight.backend.domain.voljump.Severity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VoljumpTodayResponse(
        LocalDate baseDay,
        LocalDateTime scoredAt,
        List<Item> items
) {
    public record Item(
            String ticker,
            BigDecimal jumpProb,
            Integer posts,
            BigDecimal disp,
            String status,
            Severity severity
    ) {}
}
