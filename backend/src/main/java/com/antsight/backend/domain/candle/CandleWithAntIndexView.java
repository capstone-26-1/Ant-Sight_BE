package com.antsight.backend.domain.candle;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CandleWithAntIndexView {
    LocalDateTime getCandleTime();
    BigDecimal getOpenPrice();
    BigDecimal getHighPrice();
    BigDecimal getLowPrice();
    BigDecimal getClosePrice();
    Long getVolume();
    BigDecimal getAntIndex();
    String getSentiment();
}
