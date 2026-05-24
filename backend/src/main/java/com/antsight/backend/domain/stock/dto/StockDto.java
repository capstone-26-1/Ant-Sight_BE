package com.antsight.backend.domain.stock.dto;

import com.antsight.backend.domain.stock.Market;
import com.antsight.backend.domain.stock.Stock;
import lombok.Getter;

@Getter
public class StockDto {

    private final String ticker;
    private final String name;
    private final Market market;
    private final String sector;
    private final Integer marketCapRank;

    private StockDto(String ticker, String name, Market market, String sector, Integer marketCapRank) {
        this.ticker = ticker;
        this.name = name;
        this.market = market;
        this.sector = sector;
        this.marketCapRank = marketCapRank;
    }

    public static StockDto fromEntity(Stock stock) {
        return new StockDto(
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                stock.getSector(),
                stock.getMarketCapRank()
        );
    }
}
