package com.antsight.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.collector")
@Getter
@Setter
public class CollectorProperties {

    /** false면 스케줄 잡이 실행되더라도 즉시 return — 운영 토글용 */
    private boolean enabled = false;

    /** 종목 한 건 호출 간 대기(ms) — KIS rate-limit 회피 */
    private long apiDelayMs = 100;

    /** 5분봉 정규장 수집 cron (KST). 예: "0 5/5 9-15 * * MON-FRI" */
    private String cronRegular = "0 5/5 9-15 * * MON-FRI";
}
