package com.antsight.backend.domain.candle;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.domain.antindex.Sentiment;
import com.antsight.backend.domain.candle.dto.CandleResponse;
import com.antsight.backend.domain.candle.dto.CandleSeriesResponse;
import com.antsight.backend.domain.kis.dto.KisCandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleService {

    private static final Duration MAX_CANDLE_RANGE = Duration.ofDays(7);

    private final CandleRepository candleRepository;

    @Transactional
    public int saveAll(String ticker, List<KisCandle> kisCandles) {
        int saved = 0;
        for (KisCandle k : kisCandles) {
            try {
                LocalDateTime time = k.toLocalDateTime();
                candleRepository.upsert(
                        ticker,
                        time,
                        k.openPrice(),
                        k.highPrice(),
                        k.lowPrice(),
                        k.closePrice(),
                        k.volume() == null ? 0L : k.volume()
                );
                saved++;
            } catch (Exception e) {
                log.warn("[Candle] upsert 실패 ticker={}, date={}, time={}, err={}",
                        ticker, k.tradeDate(), k.tradeTime(), e.getMessage());
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CandleResponse> findRecent(String ticker, int limit) {
        return candleRepository.findByTickerOrderByCandleTimeDesc(ticker)
                .stream()
                .limit(limit)
                .map(CandleResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandleResponse> findRange(String ticker, LocalDateTime from, LocalDateTime to) {
        return candleRepository
                .findByTickerAndCandleTimeBetweenOrderByCandleTimeAsc(ticker, from, to)
                .stream()
                .map(CandleResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CandleSeriesResponse findRangeWithAntIndex(String ticker,
                                                     LocalDateTime from,
                                                     LocalDateTime to,
                                                     boolean withAntIndex) {
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
        if (Duration.between(from, to).compareTo(MAX_CANDLE_RANGE) > 0) {
            throw new ApiException(ErrorCode.DATE_RANGE_TOO_LARGE);
        }

        List<CandleSeriesResponse.Point> points;
        if (withAntIndex) {
            points = candleRepository.findRangeWithAntIndex(ticker, from, to).stream()
                    .map(v -> new CandleSeriesResponse.Point(
                            v.getCandleTime(),
                            v.getOpenPrice(),
                            v.getHighPrice(),
                            v.getLowPrice(),
                            v.getClosePrice(),
                            v.getVolume(),
                            v.getAntIndex(),
                            v.getSentiment() == null ? null : Sentiment.valueOf(v.getSentiment())))
                    .toList();
        } else {
            points = candleRepository
                    .findByTickerAndCandleTimeBetweenOrderByCandleTimeAsc(ticker, from, to).stream()
                    .map(c -> new CandleSeriesResponse.Point(
                            c.getCandleTime(),
                            c.getOpenPrice(),
                            c.getHighPrice(),
                            c.getLowPrice(),
                            c.getClosePrice(),
                            c.getVolume(),
                            null,
                            null))
                    .toList();
        }
        return new CandleSeriesResponse(ticker, withAntIndex, points);
    }
}
