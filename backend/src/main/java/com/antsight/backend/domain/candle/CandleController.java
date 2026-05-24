package com.antsight.backend.domain.candle;

import com.antsight.backend.common.response.ApiResponse;
import com.antsight.backend.domain.candle.dto.CandleSeriesResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/candles")
@RequiredArgsConstructor
@Validated
public class CandleController {

    private static final String TICKER_REGEX = "^\\d{6}$";

    private final CandleService candleService;

    @GetMapping("/{ticker}")
    public ApiResponse<CandleSeriesResponse> range(
            @PathVariable @Pattern(regexp = TICKER_REGEX,
                    message = "ticker 는 6자리 숫자여야 합니다.") String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "true") boolean withAntIndex) {
        LocalDateTime toResolved = to == null ? LocalDateTime.now() : to;
        return ApiResponse.ok(candleService.findRangeWithAntIndex(ticker, from, toResolved, withAntIndex));
    }
}
