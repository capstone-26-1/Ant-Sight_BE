package com.antsight.backend.domain.antindex;

import com.antsight.backend.common.response.ApiResponse;
import com.antsight.backend.domain.antindex.dto.AntIndexLatestResponse;
import com.antsight.backend.domain.antindex.dto.AntIndexRankingResponse;
import com.antsight.backend.domain.antindex.dto.AntIndexSeriesResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ant-index")
@RequiredArgsConstructor
@Validated
public class AntIndexController {

    private static final String TICKER_REGEX = "^\\d{6}$";
    private static final String INTERVAL_REGEX = "^(15m|1h|1d)$";
    private static final String WINDOW_REGEX = "^(15m|1h|24h)$";
    private static final String DIRECTION_REGEX = "^(positive|negative)$";

    private final AntIndexService antIndexService;

    @GetMapping("/{ticker}/latest")
    public ApiResponse<AntIndexLatestResponse> latest(
            @PathVariable @Pattern(regexp = TICKER_REGEX,
                    message = "ticker 는 6자리 숫자여야 합니다.") String ticker) {
        return ApiResponse.ok(antIndexService.getLatest(ticker));
    }

    @GetMapping("/{ticker}")
    public ApiResponse<AntIndexSeriesResponse> series(
            @PathVariable @Pattern(regexp = TICKER_REGEX,
                    message = "ticker 는 6자리 숫자여야 합니다.") String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "15m")
                @Pattern(regexp = INTERVAL_REGEX,
                    message = "interval 은 15m, 1h, 1d 중 하나여야 합니다.") String interval) {
        LocalDateTime toResolved = to == null ? LocalDateTime.now() : to;
        return ApiResponse.ok(antIndexService.getSeries(ticker, from, toResolved, interval));
    }

    @GetMapping("/ranking")
    public ApiResponse<AntIndexRankingResponse> ranking(
            @RequestParam(defaultValue = "24h")
                @Pattern(regexp = WINDOW_REGEX,
                    message = "window 는 15m, 1h, 24h 중 하나여야 합니다.") String window,
            @RequestParam(defaultValue = "positive")
                @Pattern(regexp = DIRECTION_REGEX,
                    message = "direction 은 positive 또는 negative 여야 합니다.") String direction,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ApiResponse.ok(antIndexService.getRanking(window, direction, limit));
    }
}
