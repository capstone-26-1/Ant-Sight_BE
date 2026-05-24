package com.antsight.backend.domain.collector;

import com.antsight.backend.config.CollectorProperties;
import com.antsight.backend.domain.candle.CandleService;
import com.antsight.backend.domain.kis.KisApiClient;
import com.antsight.backend.domain.kis.dto.KisCandle;
import com.antsight.backend.domain.stock.Stock;
import com.antsight.backend.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 5분봉 수집 스케줄러 (옵션 B: forward-only, 백필 없음).
 *
 * - 정규장(09:00~15:30 KST) 5분 단위로 스케줄러 fire.
 * - active 종목 목록(약 100개)을 순회하며 KIS 분봉 API 호출 → upsert.
 * - 호출 간 apiDelayMs 대기로 rate-limit 회피.
 * - app.collector.enabled=false면 잡 자체는 동작하나 진입 시 즉시 return.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final CollectorProperties collectorProperties;
    private final StockRepository stockRepository;
    private final KisApiClient kisApiClient;
    private final CandleService candleService;

    @Scheduled(cron = "${app.collector.cron-regular}", zone = "Asia/Seoul")
    public void collectFiveMinCandles() {
        if (!collectorProperties.isEnabled()) {
            log.debug("[Collector] 비활성화 상태 — skip");
            return;
        }

        String inputHour = LocalTime.now().format(HOUR_FORMAT);
        List<Stock> targets = stockRepository.findByIsActiveTrue();
        log.info("[Collector] 5분봉 수집 시작 — targets={}, hour={}", targets.size(), inputHour);

        int ok = 0;
        int fail = 0;
        for (Stock s : targets) {
            try {
                List<KisCandle> candles = kisApiClient.fetchFiveMinCandles(s.getTicker(), inputHour);
                int saved = candleService.saveAll(s.getTicker(), candles);
                ok++;
                log.debug("[Collector] ticker={} saved={}", s.getTicker(), saved);
            } catch (Exception e) {
                fail++;
                log.warn("[Collector] 수집 실패 ticker={}, err={}", s.getTicker(), e.getMessage());
            }
            sleep(collectorProperties.getApiDelayMs());
        }

        log.info("[Collector] 5분봉 수집 종료 — ok={}, fail={}", ok, fail);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
