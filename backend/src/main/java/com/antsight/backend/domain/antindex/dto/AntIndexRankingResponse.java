package com.antsight.backend.domain.antindex.dto;

import java.math.BigDecimal;
import java.util.List;

public record AntIndexRankingResponse(
        String window,
        String direction,
        List<Item> items
) {
    public record Item(
            String ticker,
            BigDecimal avgScore,
            Long postCount
    ) {}
}
