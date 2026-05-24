package com.antsight.backend.domain.kis._test;

import com.antsight.backend.config.KisProperties;
import com.antsight.backend.domain.kis.KisApiClient;
import com.antsight.backend.domain.kis.KisTokenService;
import com.antsight.backend.domain.kis.dto.KisCandle;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 검증용 임시 컨트롤러 — 검증 완료 후 삭제할 것
 */
@RestController
@RequestMapping("/_test/kis")
@RequiredArgsConstructor
public class KisTestController {

    private final KisTokenService kisTokenService;
    private final KisApiClient kisApiClient;
    private final KisProperties kisProperties;

    @GetMapping("/token")
    public Map<String, String> token() {
        String token = kisTokenService.getValidToken();
        String masked = token.length() > 20 ? token.substring(0, 20) + "..." : token;
        return Map.of(
                "token", masked,
                "environment", kisProperties.getEnvironment()
        );
    }

    @GetMapping("/candles/{ticker}")
    public List<KisCandle> candles(@PathVariable String ticker) {
        return kisApiClient.fetchFiveMinCandles(ticker, "153000");
    }
}
