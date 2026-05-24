package com.antsight.backend.domain.antindex;

import java.math.BigDecimal;

public interface AntIndexRankingView {
    String getTicker();
    BigDecimal getAvgScore();
    Long getPostCount();
}
