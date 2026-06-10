package com.antsight.backend.domain.voljump;

import java.math.BigDecimal;

public enum Severity {
    HIGH,
    MEDIUM,
    LOW;

    private static final BigDecimal HIGH_THRESHOLD = new BigDecimal("0.4");
    private static final BigDecimal MEDIUM_THRESHOLD = new BigDecimal("0.2");

    public static Severity fromProb(BigDecimal prob) {
        if (prob == null) return null;
        if (prob.compareTo(HIGH_THRESHOLD) >= 0) return HIGH;
        if (prob.compareTo(MEDIUM_THRESHOLD) >= 0) return MEDIUM;
        return LOW;
    }
}
