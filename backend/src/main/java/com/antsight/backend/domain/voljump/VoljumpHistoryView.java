package com.antsight.backend.domain.voljump;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface VoljumpHistoryView {
    LocalDate getBaseDay();
    BigDecimal getJumpProb();
    String getStatus();
}
