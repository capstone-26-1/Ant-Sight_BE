package com.antsight.backend.domain.antindex;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.domain.antindex.dto.AntIndexLatestResponse;
import com.antsight.backend.domain.antindex.dto.AntIndexRankingResponse;
import com.antsight.backend.domain.antindex.dto.AntIndexSeriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AntIndexService {

    private final AntIndexScoreRepository repository;

    @Transactional(readOnly = true)
    public AntIndexLatestResponse getLatest(String ticker) {
        AntIndexLatestView view = repository.findLatestView(ticker)
                .orElseThrow(() -> new ApiException(ErrorCode.ANT_INDEX_NOT_FOUND));
        return new AntIndexLatestResponse(
                view.getTicker(),
                view.getTimestamp(),
                view.getScore(),
                Sentiment.valueOf(view.getSentiment()),
                view.getPostCount() == null ? 0L : view.getPostCount());
    }

    @Transactional(readOnly = true)
    public AntIndexSeriesResponse getSeries(String ticker,
                                            LocalDateTime from,
                                            LocalDateTime to,
                                            String interval) {
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
        List<AntIndexBucketView> rows = switch (interval) {
            case "15m" -> repository.findSeries15m(ticker, from, to);
            case "1h"  -> repository.findSeries1h(ticker, from, to);
            case "1d"  -> repository.findSeries1d(ticker, from, to);
            default    -> throw new IllegalStateException("unexpected interval: " + interval);
        };
        List<AntIndexSeriesResponse.Point> points = rows.stream()
                .map(v -> new AntIndexSeriesResponse.Point(
                        v.getBucketTs(),
                        v.getAvgScore(),
                        Sentiment.fromScore(v.getAvgScore().doubleValue()),
                        v.getPostCount() == null ? 0L : v.getPostCount()))
                .toList();
        return new AntIndexSeriesResponse(ticker, interval, points);
    }

    @Transactional(readOnly = true)
    public AntIndexRankingResponse getRanking(String window, String direction, int limit) {
        LocalDateTime from = LocalDateTime.now().minus(parseWindowDuration(window));
        Pageable pageable = PageRequest.of(0, limit);
        List<AntIndexRankingView> rows = "positive".equals(direction)
                ? repository.findRankingPositive(from, pageable)
                : repository.findRankingNegative(from, pageable);
        List<AntIndexRankingResponse.Item> items = rows.stream()
                .map(v -> new AntIndexRankingResponse.Item(
                        v.getTicker(),
                        v.getAvgScore(),
                        v.getPostCount() == null ? 0L : v.getPostCount()))
                .toList();
        return new AntIndexRankingResponse(window, direction, items);
    }

    private Duration parseWindowDuration(String window) {
        return switch (window) {
            case "15m" -> Duration.ofMinutes(15);
            case "1h"  -> Duration.ofHours(1);
            case "24h" -> Duration.ofHours(24);
            default    -> throw new IllegalStateException("unexpected window: " + window);
        };
    }
}
