package com.antsight.backend.domain.quote.dto;

import com.antsight.backend.domain.kis.dto.KisQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuoteResponse(
        String ticker,
        BigDecimal currentPrice,
        BigDecimal changeAmount,
        BigDecimal changeRate,
        Long accVolume,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        LocalDateTime fetchedAt
) {
    public static QuoteResponse from(KisQuote q, LocalDateTime fetchedAt) {
        return new QuoteResponse(
                q.ticker(),
                q.currentPrice(),
                q.changeAmount(),
                q.changeRate(),
                q.accVolume(),
                q.openPrice(),
                q.highPrice(),
                q.lowPrice(),
                fetchedAt
        );
    }
}
