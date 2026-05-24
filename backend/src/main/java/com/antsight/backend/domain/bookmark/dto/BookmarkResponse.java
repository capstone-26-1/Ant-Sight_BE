package com.antsight.backend.domain.bookmark.dto;

import com.antsight.backend.domain.bookmark.Bookmark;
import com.antsight.backend.domain.stock.Market;
import com.antsight.backend.domain.stock.Stock;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class BookmarkResponse {

    private final Long bookmarkId;
    private final LocalDateTime bookmarkedAt;

    // 종목 정보
    private final String ticker;
    private final String name;
    private final Market market;
    private final String sector;
    private final Integer marketCapRank;

    private BookmarkResponse(Long bookmarkId, LocalDateTime bookmarkedAt,
                             String ticker, String name, Market market,
                             String sector, Integer marketCapRank) {
        this.bookmarkId = bookmarkId;
        this.bookmarkedAt = bookmarkedAt;
        this.ticker = ticker;
        this.name = name;
        this.market = market;
        this.sector = sector;
        this.marketCapRank = marketCapRank;
    }

    public static BookmarkResponse of(Bookmark bookmark, Stock stock) {
        return new BookmarkResponse(
                bookmark.getId(),
                bookmark.getCreatedAt(),
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                stock.getSector(),
                stock.getMarketCapRank()
        );
    }

    // 종목 정보를 찾을 수 없는 경우 (비활성화된 종목 등)
    public static BookmarkResponse ofUnknownStock(Bookmark bookmark) {
        return new BookmarkResponse(
                bookmark.getId(),
                bookmark.getCreatedAt(),
                bookmark.getTicker(),
                null, null, null, null
        );
    }
}
