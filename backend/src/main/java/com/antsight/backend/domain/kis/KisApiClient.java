package com.antsight.backend.domain.kis;

import com.antsight.backend.config.KisProperties;
import com.antsight.backend.domain.kis.dto.KisCandle;
import com.antsight.backend.domain.kis.dto.KisQuote;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisApiClient {

    private static final int MAX_RETRY = 3;

    /**
     * 호출 허용 TR_ID 화이트리스트.
     * 거래(주문/취소/정정) 관련 TR_ID는 절대 추가하지 말 것.
     */
    private static final Set<String> ALLOWED_TR_IDS = Set.of(
            "FHKST03010200",   // 분봉 차트 조회
            "FHKST03010100",   // 일봉 차트 조회
            "FHKST01010100",   // 주식 현재가 시세
            "FHKST01010300",   // 주식 호가
            "CTPF1604R"        // 종목 정보 조회
    );

    private final KisTokenService kisTokenService;
    private final KisProperties kisProperties;
    private final RestClient restClient;

    // -------------------------------------------------------------------------
    // 분봉 조회
    // -------------------------------------------------------------------------

    public List<KisCandle> fetchFiveMinCandles(String stockCode, String inputHour) {
        final String trId = "FHKST03010200";
        validateTrId(trId);
        log.info("[KIS] 분봉 조회 시작 — ticker={}, hour={}", stockCode, inputHour);

        String url = kisProperties.getBaseUrl()
                + "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice";

        Map<String, String> params = Map.of(
                "FID_ETC_CLS_CODE", "",
                "FID_COND_MRKT_DIV_CODE", "J",
                "FID_INPUT_ISCD", stockCode,
                "FID_INPUT_HOUR_1", inputHour,
                "FID_PW_DATA_INCU_YN", "Y"
        );

        RawCandleResponse response = executeWithRetry(
                () -> callGet(url, trId, stockCode, params, RawCandleResponse.class),
                stockCode
        );

        assertSuccess(response.rtCd(), response.msgCd(), response.msg1());

        List<KisCandle> candles = response.output2().stream()
                .map(this::toCandle)
                .toList();

        log.info("[KIS] 분봉 조회 완료 — ticker={}, count={}", stockCode, candles.size());
        return candles;
    }

    // -------------------------------------------------------------------------
    // 일봉 조회
    // -------------------------------------------------------------------------

    public List<KisCandle> fetchDailyCandles(String stockCode, String fromDate, String toDate) {
        final String trId = "FHKST03010100";
        validateTrId(trId);
        log.info("[KIS] 일봉 조회 시작 — ticker={}, from={}, to={}", stockCode, fromDate, toDate);

        String url = kisProperties.getBaseUrl()
                + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";

        Map<String, String> params = Map.of(
                "FID_COND_MRKT_DIV_CODE", "J",
                "FID_INPUT_ISCD", stockCode,
                "FID_INPUT_DATE_1", fromDate,
                "FID_INPUT_DATE_2", toDate,
                "FID_PERIOD_DIV_CODE", "D",
                "FID_ORG_ADJ_PRC", "0"
        );

        RawCandleResponse response = executeWithRetry(
                () -> callGet(url, trId, stockCode, params, RawCandleResponse.class),
                stockCode
        );

        assertSuccess(response.rtCd(), response.msgCd(), response.msg1());

        List<KisCandle> candles = response.output2().stream()
                .map(this::toCandle)
                .toList();

        log.info("[KIS] 일봉 조회 완료 — ticker={}, count={}", stockCode, candles.size());
        return candles;
    }

    // -------------------------------------------------------------------------
    // 현재가 조회
    // -------------------------------------------------------------------------

    public KisQuote fetchCurrentPrice(String stockCode) {
        final String trId = "FHKST01010100";
        validateTrId(trId);

        String url = kisProperties.getBaseUrl()
                + "/uapi/domestic-stock/v1/quotations/inquire-price";

        Map<String, String> params = Map.of(
                "FID_COND_MRKT_DIV_CODE", "J",
                "FID_INPUT_ISCD", stockCode
        );

        RawQuoteResponse response = executeWithRetry(
                () -> callGet(url, trId, stockCode, params, RawQuoteResponse.class),
                stockCode
        );

        assertSuccess(response.rtCd(), response.msgCd(), response.msg1());

        RawQuote q = response.output();
        return new KisQuote(
                stockCode,
                parseBigDecimal(q.stckPrpr()),
                parseBigDecimal(q.prdyVrss()),
                parseBigDecimal(q.prdyCtrt()),
                parseLong(q.acmlVol()),
                parseBigDecimal(q.stckOprc()),
                parseBigDecimal(q.stckHgpr()),
                parseBigDecimal(q.stckLwpr())
        );
    }

    // -------------------------------------------------------------------------
    // 공통 HTTP 호출
    // -------------------------------------------------------------------------

    private <T> T callGet(String url, String trId, String ticker, Map<String, String> params, Class<T> responseType) {
        // 이중 방어: 호출 경로의 마지막 choke point에서도 화이트리스트 검증
        validateTrId(trId);

        if (!kisProperties.isMock()) {
            log.info("[REAL] API call: ticker={}, trId={}", ticker, trId);
        }

        String token = kisTokenService.getValidToken();

        return restClient.get()
                .uri(url, builder -> {
                    params.forEach(builder::queryParam);
                    return builder.build();
                })
                .header("Content-Type", "application/json")
                .header("authorization", "Bearer " + token)
                .header("appkey", kisProperties.getAppKey())
                .header("appsecret", kisProperties.getAppSecret())
                .header("tr_id", trId)
                .header("custtype", "P")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    int status = res.getStatusCode().value();
                    if (status == 401) {
                        throw new RetryWithTokenRefreshException();
                    }
                    if (status == 429) {
                        throw new RetryWithDelayException();
                    }
                    String body = new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (status == 403 && body.contains("EGW00133")) {
                        throw new KisTokenCooldownException(60);
                    }
                    throw new KisApiException("KIS API 4xx 오류: " + status + " — " + body);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new RetryableKisException("KIS API 5xx 오류: " + res.getStatusCode().value());
                })
                .body(responseType);
    }

    // -------------------------------------------------------------------------
    // 재시도 로직
    // -------------------------------------------------------------------------

    private <T> T executeWithRetry(ApiCall<T> call, String ticker) {
        int attempt = 0;
        while (true) {
            try {
                return call.execute();
            } catch (RetryWithTokenRefreshException e) {
                if (attempt++ >= 1) throw new KisApiException("토큰 갱신 후에도 401 응답");
                log.warn("[KIS] 401 응답 — 토큰 갱신 후 재시도 (ticker={})", ticker);
                kisTokenService.refreshTokenManually();
            } catch (RetryWithDelayException e) {
                if (attempt++ >= 1) throw new KisApiException("429 응답 재시도 실패");
                log.warn("[KIS] 429 응답 — 1초 대기 후 재시도 (ticker={})", ticker);
                sleep(1_000);
            } catch (RetryableKisException e) {
                if (attempt >= MAX_RETRY) {
                    log.error("[KIS] 5xx 최대 재시도 초과 (ticker={}): {}", ticker, e.getMessage());
                    throw new KisApiException(e.getMessage());
                }
                long delay = (long) Math.pow(2, attempt) * 1_000L;
                log.warn("[KIS] 5xx 응답 — {}ms 후 재시도 ({}/{}) ticker={}", delay, attempt + 1, MAX_RETRY, ticker);
                sleep(delay);
                attempt++;
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new KisApiException("재시도 대기 중 인터럽트 발생");
        }
    }

    private void validateTrId(String trId) {
        if (!ALLOWED_TR_IDS.contains(trId)) {
            log.error("CRITICAL: 허용되지 않은 TR_ID 호출 시도: {}", trId);
            throw new SecurityException("허용되지 않은 API: " + trId);
        }
    }

    private void assertSuccess(String rtCd, String msgCd, String msg1) {
        if (!"0".equals(rtCd)) {
            log.warn("[KIS] 비정상 응답 — rtCd={}, msgCd={}, msg={}", rtCd, msgCd, msg1);
            throw new KisApiException("KIS 응답 오류 [%s] %s".formatted(msgCd, msg1));
        }
    }

    // -------------------------------------------------------------------------
    // DTO 매핑
    // -------------------------------------------------------------------------

    private KisCandle toCandle(RawCandle raw) {
        return new KisCandle(
                raw.stckBsopDate(),
                raw.stckCntgHour(),
                parseBigDecimal(raw.stckOprc()),
                parseBigDecimal(raw.stckHgpr()),
                parseBigDecimal(raw.stckLwpr()),
                parseBigDecimal(raw.stckPrpr()),
                parseLong(raw.cntgVol()),
                null,
                null
        );
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.replace(",", ""));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        return Long.parseLong(value.replace(",", ""));
    }

    // -------------------------------------------------------------------------
    // 내부 타입
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface ApiCall<T> {
        T execute();
    }

    private static class RetryWithTokenRefreshException extends RuntimeException {}
    private static class RetryWithDelayException extends RuntimeException {}
    private static class RetryableKisException extends RuntimeException {
        RetryableKisException(String msg) { super(msg); }
    }

    private record RawCandleResponse(
            @JsonProperty("rt_cd")   String rtCd,
            @JsonProperty("msg_cd")  String msgCd,
            @JsonProperty("msg1")    String msg1,
            @JsonProperty("output2") List<RawCandle> output2
    ) {}

    private record RawCandle(
            @JsonProperty("stck_bsop_date") String stckBsopDate,
            @JsonProperty("stck_cntg_hour") String stckCntgHour,
            @JsonProperty("stck_oprc")      String stckOprc,
            @JsonProperty("stck_hgpr")      String stckHgpr,
            @JsonProperty("stck_lwpr")      String stckLwpr,
            @JsonProperty("stck_prpr")      String stckPrpr,
            @JsonProperty("cntg_vol")       String cntgVol
    ) {}

    private record RawQuoteResponse(
            @JsonProperty("rt_cd")  String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1")   String msg1,
            @JsonProperty("output") RawQuote output
    ) {}

    private record RawQuote(
            @JsonProperty("stck_prpr") String stckPrpr,    // 주식 현재가
            @JsonProperty("prdy_vrss") String prdyVrss,    // 전일 대비
            @JsonProperty("prdy_ctrt") String prdyCtrt,    // 전일 대비율
            @JsonProperty("acml_vol")  String acmlVol,     // 누적 거래량
            @JsonProperty("stck_oprc") String stckOprc,    // 시가
            @JsonProperty("stck_hgpr") String stckHgpr,    // 고가
            @JsonProperty("stck_lwpr") String stckLwpr     // 저가
    ) {}
}
