package com.antsight.backend.domain.stock;

import com.antsight.backend.common.response.ApiResponse;
import com.antsight.backend.domain.candle.CandleService;
import com.antsight.backend.domain.candle.dto.CandleResponse;
import com.antsight.backend.domain.quote.QuoteService;
import com.antsight.backend.domain.quote.dto.QuoteResponse;
import com.antsight.backend.domain.stock.dto.StockDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Validated
public class StockController {

    private final StockService stockService;
    private final CandleService candleService;
    private final QuoteService quoteService;

    @GetMapping("/search")
    public ApiResponse<List<StockDto>> search(
            @RequestParam("q") @NotBlank String q,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(stockService.search(q, limit));
    }

    @GetMapping("/active")
    public ApiResponse<List<StockDto>> active() {
        return ApiResponse.ok(stockService.findActiveStocks());
    }

    @GetMapping("/{ticker}")
    public ApiResponse<StockDto> getByTicker(@PathVariable String ticker) {
        return ApiResponse.ok(stockService.findByTicker(ticker));
    }

    @GetMapping("/{ticker}/candles")
    public ApiResponse<List<CandleResponse>> candles(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return ApiResponse.ok(candleService.findRecent(ticker.toUpperCase(), limit));
    }

    @GetMapping("/{ticker}/quote")
    public ApiResponse<QuoteResponse> quote(@PathVariable String ticker) {
        return ApiResponse.ok(quoteService.getQuote(ticker));
    }
}
