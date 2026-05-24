package com.antsight.backend.domain.kis.dto;

import java.math.BigDecimal;

/**
 * KIS 현재가 응답 (TR_ID: FHKST01010100) — 핵심 필드만 추출.
 */
public record KisQuote(
        String ticker,
        BigDecimal currentPrice,
        BigDecimal changeAmount,   // 전일 대비 (음수 가능)
        BigDecimal changeRate,     // % 단위
        Long accVolume,            // 누적 거래량
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice
) {}
