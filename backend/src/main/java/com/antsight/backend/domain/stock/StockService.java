package com.antsight.backend.domain.stock;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.domain.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private static final int ACTIVE_STOCKS_LIMIT = 100;

    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public List<StockDto> search(String query, int limit) {
        return stockRepository.searchByTickerOrName(query.trim(), limit)
                .stream()
                .map(StockDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public StockDto findByTicker(String ticker) {
        return stockRepository.findById(ticker.toUpperCase())
                .map(StockDto::fromEntity)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<StockDto> findActiveStocks() {
        return stockRepository.findByIsActiveTrue()
                .stream()
                .limit(ACTIVE_STOCKS_LIMIT)
                .map(StockDto::fromEntity)
                .toList();
    }
}
