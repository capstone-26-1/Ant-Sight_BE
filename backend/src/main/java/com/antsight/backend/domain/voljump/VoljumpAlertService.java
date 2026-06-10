package com.antsight.backend.domain.voljump;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.config.CacheConfig;
import com.antsight.backend.domain.voljump.dto.VoljumpDetailResponse;
import com.antsight.backend.domain.voljump.dto.VoljumpHistoryResponse;
import com.antsight.backend.domain.voljump.dto.VoljumpTodayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VoljumpAlertService {

    private static final String VALID_STATUS = "VALID";

    private final VoljumpAlertRepository repository;

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.VOLJUMP_TODAY, key = "#minProb + ':' + #limit")
    public VoljumpTodayResponse getToday(BigDecimal minProb, int limit) {
        Optional<LocalDate> latestOpt = repository.findLatestValidBaseDay();
        if (latestOpt.isEmpty()) {
            return new VoljumpTodayResponse(null, null, List.of());
        }
        LocalDate baseDay = latestOpt.get();
        List<VoljumpAlertView> rows = repository.findTodayItems(baseDay, minProb, limit);
        List<VoljumpTodayResponse.Item> items = rows.stream()
                .map(v -> new VoljumpTodayResponse.Item(
                        v.getTicker(),
                        v.getJumpProb(),
                        v.getPosts(),
                        v.getDisp(),
                        v.getStatus(),
                        Severity.fromProb(v.getJumpProb())))
                .toList();
        // scored_at: items 가 있으면 첫 행 (모두 동일 base_day 라 사실상 동일 배치 시각)
        return new VoljumpTodayResponse(
                baseDay,
                rows.isEmpty() ? null : rows.get(0).getScoredAt(),
                items);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.VOLJUMP_DETAIL, key = "#ticker")
    public VoljumpDetailResponse getDetail(String ticker) {
        VoljumpAlertView v = repository.findLatestByTicker(ticker)
                .orElseThrow(() -> new ApiException(ErrorCode.VOLJUMP_NOT_FOUND));
        Severity severity = VALID_STATUS.equals(v.getStatus())
                ? Severity.fromProb(v.getJumpProb())
                : null;
        return new VoljumpDetailResponse(
                v.getTicker(),
                v.getBaseDay(),
                v.getScoredAt(),
                v.getJumpProb(),
                v.getPosts(),
                v.getDisp(),
                v.getStatus(),
                severity);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.VOLJUMP_HISTORY, key = "#ticker + ':' + #days")
    public VoljumpHistoryResponse getHistory(String ticker, int days) {
        List<VoljumpHistoryView> rows = repository.findHistory(ticker, days);
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.VOLJUMP_NOT_FOUND);
        }
        List<VoljumpHistoryResponse.Point> points = rows.stream()
                .map(r -> new VoljumpHistoryResponse.Point(
                        r.getBaseDay(),
                        r.getJumpProb(),
                        r.getStatus()))
                .toList();
        return new VoljumpHistoryResponse(ticker, points);
    }
}
