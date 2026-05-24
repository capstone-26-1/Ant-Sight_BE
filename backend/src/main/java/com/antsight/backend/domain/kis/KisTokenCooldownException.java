package com.antsight.backend.domain.kis;

public class KisTokenCooldownException extends KisApiException {

    private final long retryAfterSeconds;

    public KisTokenCooldownException(long retryAfterSeconds) {
        super("KIS 토큰 발급 쿨다운 중 — " + retryAfterSeconds + "초 후 재시도 가능");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
