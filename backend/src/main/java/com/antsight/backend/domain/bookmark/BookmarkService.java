package com.antsight.backend.domain.bookmark;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.domain.bookmark.dto.BookmarkRequest;
import com.antsight.backend.domain.bookmark.dto.BookmarkResponse;
import com.antsight.backend.domain.stock.Stock;
import com.antsight.backend.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public List<BookmarkResponse> getBookmarks(Long userId) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserId(userId);

        List<String> tickers = bookmarks.stream()
                .map(Bookmark::getTicker)
                .toList();

        Map<String, Stock> stockMap = stockRepository.findAllById(tickers)
                .stream()
                .collect(Collectors.toMap(Stock::getTicker, Function.identity()));

        return bookmarks.stream()
                .map(b -> {
                    Stock stock = stockMap.get(b.getTicker());
                    return stock != null
                            ? BookmarkResponse.of(b, stock)
                            : BookmarkResponse.ofUnknownStock(b);
                })
                .toList();
    }

    @Transactional
    public void addBookmark(Long userId, BookmarkRequest request) {
        String ticker = request.getTicker().toUpperCase();

        if (!stockRepository.existsById(ticker)) {
            throw new ApiException(ErrorCode.STOCK_NOT_FOUND);
        }

        if (bookmarkRepository.existsByUserIdAndTicker(userId, ticker)) {
            throw new ApiException(ErrorCode.DUPLICATE_BOOKMARK);
        }

        bookmarkRepository.save(
                Bookmark.builder()
                        .userId(userId)
                        .ticker(ticker)
                        .build()
        );
    }

    @Transactional
    public void removeBookmark(Long userId, String ticker) {
        int deleted = bookmarkRepository.deleteByUserIdAndTicker(userId, ticker.toUpperCase());
        if (deleted == 0) {
            throw new ApiException(ErrorCode.BOOKMARK_NOT_FOUND);
        }
    }
}
