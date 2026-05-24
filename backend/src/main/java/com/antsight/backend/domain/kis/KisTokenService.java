package com.antsight.backend.domain.kis;

import com.antsight.backend.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisTokenService {

    // KIS는 토큰 발급을 앱키당 1분에 1회로 제한(EGW00133).
    // 콜드 스타트/만료 직후 동시 요청이 KIS를 동시에 때리지 않도록 인스턴스 내 쿨다운 적용.
    private static final long TOKEN_ISSUE_COOLDOWN_MS = 60_000L;
    private final Object tokenIssueLock = new Object();
    private long lastIssueAttemptMillis = 0L;

    private final KisTokenRepository kisTokenRepository;
    private final KisProperties kisProperties;
    private final RestClient restClient;

    @Transactional
    public String getValidToken() {
        Optional<String> cached = kisTokenRepository.findByEnvironment(kisProperties.getEnvironment())
                .filter(KisToken::isValid)
                .map(KisToken::getAccessToken);
        if (cached.isPresent()) {
            return cached.get();
        }
        log.info("[KIS] 유효한 토큰 없음 — 신규 발급 시도 (environment={})", kisProperties.getEnvironment());
        return issueWithCooldown().getAccessToken();
    }

    @Transactional
    public void refreshTokenManually() {
        log.info("[KIS] 토큰 강제 갱신 요청 (environment={})", kisProperties.getEnvironment());
        issueWithCooldown();
    }

    private KisToken issueWithCooldown() {
        synchronized (tokenIssueLock) {
            Optional<KisToken> fresh = kisTokenRepository.findByEnvironment(kisProperties.getEnvironment())
                    .filter(KisToken::isValid);
            if (fresh.isPresent()) {
                log.info("[KIS] 락 대기 중 다른 스레드가 토큰을 발급함 — 재사용");
                return fresh.get();
            }

            long now = System.currentTimeMillis();
            long elapsed = now - lastIssueAttemptMillis;
            if (lastIssueAttemptMillis > 0 && elapsed < TOKEN_ISSUE_COOLDOWN_MS) {
                long retryAfterSec = Math.max(1, (TOKEN_ISSUE_COOLDOWN_MS - elapsed + 999) / 1000);
                log.warn("[KIS] 토큰 발급 쿨다운 — {}초 후 재시도 가능", retryAfterSec);
                throw new KisTokenCooldownException(retryAfterSec);
            }

            lastIssueAttemptMillis = now;
            return issueAndSave();
        }
    }

    private KisToken issueAndSave() {
        TokenResponse response = requestNewToken();

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(response.expiresIn());

        KisToken token = kisTokenRepository.findByEnvironment(kisProperties.getEnvironment())
                .map(existing -> {
                    existing.update(response.accessToken(), response.tokenType(), expiresAt);
                    return existing;
                })
                .orElseGet(() -> KisToken.builder()
                        .environment(kisProperties.getEnvironment())
                        .accessToken(response.accessToken())
                        .tokenType(response.tokenType())
                        .expiresAt(expiresAt)
                        .build());

        kisTokenRepository.save(token);
        log.info("[KIS] 토큰 발급 완료 — expiresAt={}", expiresAt);
        return token;
    }

    private TokenResponse requestNewToken() {
        String url = kisProperties.getBaseUrl() + "/oauth2/tokenP";

        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", kisProperties.getAppKey(),
                "appsecret", kisProperties.getAppSecret()
        );

        log.info("[KIS] 토큰 발급 요청 → {}", url);

        // KIS는 에러 상황에서 (a) 200 OK + error_code 본문, (b) 4xx + error_code 본문 둘 다 가능.
        // 따라서 Map으로 받아서 error_code/누락 필드를 직접 검증한다.
        Map<String, Object> raw;
        try {
            raw = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("[KIS] 토큰 발급 HTTP 에러: status={}, body={}", e.getStatusCode(), responseBody);
            if (responseBody != null && responseBody.contains("EGW00133")) {
                throw new KisTokenCooldownException(60);
            }
            throw new KisApiException("KIS 토큰 발급 실패 (" + e.getStatusCode() + "): " + responseBody);
        }

        if (raw == null || raw.isEmpty()) {
            throw new KisApiException("KIS 토큰 응답이 비어있습니다.");
        }
        if (raw.containsKey("error_code")) {
            String errorCode = String.valueOf(raw.get("error_code"));
            String errorDesc = String.valueOf(raw.get("error_description"));
            if ("EGW00133".equals(errorCode)) {
                throw new KisTokenCooldownException(60);
            }
            throw new KisApiException("KIS 토큰 발급 실패 [%s] %s".formatted(errorCode, errorDesc));
        }

        String accessToken = (String) raw.get("access_token");
        String tokenType = (String) raw.get("token_type");
        Object expiresInObj = raw.get("expires_in");

        if (accessToken == null || expiresInObj == null) {
            throw new KisApiException("KIS 토큰 응답 필드 누락: " + raw);
        }

        long expiresIn = ((Number) expiresInObj).longValue();
        return new TokenResponse(accessToken, tokenType, expiresIn);
    }

    private record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
