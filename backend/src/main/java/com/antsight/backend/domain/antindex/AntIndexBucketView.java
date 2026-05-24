package com.antsight.backend.domain.antindex;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface AntIndexBucketView {
    LocalDateTime getBucketTs();
    BigDecimal getAvgScore();
    Long getPostCount();
}
