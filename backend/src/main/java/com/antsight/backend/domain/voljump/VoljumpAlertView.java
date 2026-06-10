package com.antsight.backend.domain.voljump;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface VoljumpAlertView {
    String getTicker();
    LocalDate getBaseDay();
    LocalDateTime getScoredAt();
    BigDecimal getJumpProb();
    Integer getPosts();
    BigDecimal getDisp();
    String getStatus();
}
