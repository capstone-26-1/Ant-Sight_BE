package com.antsight.backend.domain.voljump;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.common.response.ApiResponse;
import com.antsight.backend.domain.voljump.dto.VoljumpDetailResponse;
import com.antsight.backend.domain.voljump.dto.VoljumpHistoryResponse;
import com.antsight.backend.domain.voljump.dto.VoljumpTodayResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/voljump")
@RequiredArgsConstructor
@Validated
public class VoljumpAlertController {

    private static final String TICKER_REGEX = "^\\d{6}$";
    private static final BigDecimal MIN_PROB_FLOOR = BigDecimal.ZERO;
    private static final BigDecimal MIN_PROB_CEIL = BigDecimal.ONE;
    private static final int LIMIT_FLOOR = 1;
    private static final int LIMIT_CEIL = 100;
    private static final int DAYS_FLOOR = 1;
    private static final int DAYS_CEIL = 90;
    private static final String EMPTY_TODAY_MESSAGE = "오늘 적재 데이터 없음 (배치 대기 중)";

    private final VoljumpAlertService service;

    @GetMapping("/today")
    public ApiResponse<VoljumpTodayResponse> today(
            @RequestParam(name = "min_prob", required = false, defaultValue = "0.2") BigDecimal minProb,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {
        if (minProb.compareTo(MIN_PROB_FLOOR) < 0
                || minProb.compareTo(MIN_PROB_CEIL) > 0
                || limit < LIMIT_FLOOR
                || limit > LIMIT_CEIL) {
            throw new ApiException(ErrorCode.VOLJUMP_INVALID_PARAM);
        }
        VoljumpTodayResponse data = service.getToday(minProb, limit);
        return data.baseDay() == null
                ? ApiResponse.ok(data, EMPTY_TODAY_MESSAGE)
                : ApiResponse.ok(data);
    }

    @GetMapping("/{ticker}")
    public ApiResponse<VoljumpDetailResponse> detail(
            @PathVariable @Pattern(regexp = TICKER_REGEX,
                    message = "ticker 는 6자리 숫자여야 합니다.") String ticker) {
        return ApiResponse.ok(service.getDetail(ticker));
    }

    @GetMapping("/history/{ticker}")
    public ApiResponse<VoljumpHistoryResponse> history(
            @PathVariable @Pattern(regexp = TICKER_REGEX,
                    message = "ticker 는 6자리 숫자여야 합니다.") String ticker,
            @RequestParam(required = false, defaultValue = "30") int days) {
        if (days < DAYS_FLOOR || days > DAYS_CEIL) {
            throw new ApiException(ErrorCode.VOLJUMP_INVALID_DAYS);
        }
        return ApiResponse.ok(service.getHistory(ticker, days));
    }
}
