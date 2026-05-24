package com.antsight.backend.domain.antindex;

public enum Sentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE;

    public static Sentiment fromScore(double score) {
        if (score >= 70.0) return POSITIVE;
        if (score <= 30.0) return NEGATIVE;
        return NEUTRAL;
    }
}
