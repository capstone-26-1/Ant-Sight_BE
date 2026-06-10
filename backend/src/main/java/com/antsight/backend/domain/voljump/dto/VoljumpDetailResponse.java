package com.antsight.backend.domain.voljump.dto;

import com.antsight.backend.domain.voljump.Severity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record VoljumpDetailResponse(
        String ticker,
        LocalDate baseDay,
        LocalDateTime scoredAt,
        BigDecimal jumpProb,
        Integer posts,
        BigDecimal disp,
        String status,
        Severity severity
) {}
