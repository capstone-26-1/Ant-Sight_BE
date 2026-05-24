package com.antsight.backend.domain.kis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record KisCandle(
        String tradeDate,       // YYYYMMDD
        String tradeTime,       // HHMMSS
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        Long volume,
        Long tradeValue,        // nullable
        BigDecimal changeRate   // nullable
) {
    private static final DateTimeFormatter DT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.parse(tradeDate + tradeTime, DT_FORMATTER);
    }
}
