package com.antsight.backend.domain.antindex.dto;

import com.antsight.backend.domain.antindex.Sentiment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AntIndexLatestResponse(
        String ticker,
        LocalDateTime timestamp,
        BigDecimal score,
        Sentiment sentiment,
        Long postCount
) {}
