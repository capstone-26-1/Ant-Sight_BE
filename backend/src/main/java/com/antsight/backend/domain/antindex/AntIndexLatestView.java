package com.antsight.backend.domain.antindex;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface AntIndexLatestView {
    String getTicker();
    LocalDateTime getTimestamp();
    BigDecimal getScore();
    Long getPostCount();
}
