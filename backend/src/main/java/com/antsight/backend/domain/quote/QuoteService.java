package com.antsight.backend.domain.quote;

import com.antsight.backend.domain.kis.KisApiClient;
import com.antsight.backend.domain.kis.dto.KisQuote;
import com.antsight.backend.domain.quote.dto.QuoteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 현재가 in-memory 캐시.
 *
 * - TTL 10초: 동일 종목에 대한 폭주 호출을 KIS API 1회로 합쳐 부하·rate-limit를 차단.
 * - 프론트가 5초 폴링을 해도 캐시 hit 비율이 높아짐.
 * - 멀티 인스턴스 환경에서는 인스턴스별로 캐시가 분리되지만 학교 프로젝트 규모에서는 문제 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private static final Duration TTL = Duration.ofSeconds(10);

    private final KisApiClient kisApiClient;
    private final ConcurrentHashMap<String, CachedQuote> cache = new ConcurrentHashMap<>();

    public QuoteResponse getQuote(String ticker) {
        String key = ticker.toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        CachedQuote cached = cache.get(key);
        if (cached != null && Duration.between(cached.fetchedAt, now).compareTo(TTL) < 0) {
            return QuoteResponse.from(cached.quote, cached.fetchedAt);
        }

        KisQuote fresh = kisApiClient.fetchCurrentPrice(key);
        LocalDateTime fetchedAt = LocalDateTime.now();
        cache.put(key, new CachedQuote(fresh, fetchedAt));
        return QuoteResponse.from(fresh, fetchedAt);
    }

    private record CachedQuote(KisQuote quote, LocalDateTime fetchedAt) {}
}
