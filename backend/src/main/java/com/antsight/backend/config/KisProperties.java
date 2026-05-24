package com.antsight.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.kis")
@Getter
@Setter
public class KisProperties {

    private String environment;
    private String appKey;
    private String appSecret;
    private String accountNumber;
    private String baseUrlMock;
    private String baseUrlReal;

    public String getBaseUrl() {
        return "MOCK".equalsIgnoreCase(environment) ? baseUrlMock : baseUrlReal;
    }

    public boolean isMock() {
        return "MOCK".equalsIgnoreCase(environment);
    }
}
