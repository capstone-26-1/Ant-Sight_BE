package com.antsight.backend.domain.antindex;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "scores_v2", catalog = "ant_index")
@Getter
@NoArgsConstructor
@IdClass(AntIndexScore.AntIndexScoreId.class)
public class AntIndexScore {

    @Id
    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Id
    @Column(name = "bucket_time", nullable = false)
    private LocalDateTime bucketTime;

    @Column(name = "post_count")
    private Integer postCount;

    @Column(name = "ant_index", precision = 5, scale = 2)
    private BigDecimal antIndex;

    @Column(name = "greed_raw", precision = 6, scale = 4)
    private BigDecimal greedRaw;

    @Column(name = "fear_raw", precision = 6, scale = 4)
    private BigDecimal fearRaw;

    @Column(name = "mean_stance", precision = 6, scale = 4)
    private BigDecimal meanStance;

    @Column(name = "std_stance", precision = 6, scale = 4)
    private BigDecimal stdStance;

    @Column(name = "mean_euphoria", precision = 6, scale = 4)
    private BigDecimal meanEuphoria;

    @Column(name = "mean_anxiety", precision = 6, scale = 4)
    private BigDecimal meanAnxiety;

    @Column(name = "mean_capit", precision = 6, scale = 4)
    private BigDecimal meanCapit;

    @Column(name = "mean_anger", precision = 6, scale = 4)
    private BigDecimal meanAnger;

    @Column(name = "analysis_ratio", precision = 6, scale = 4)
    private BigDecimal analysisRatio;

    // 복합 PK 클래스
    @NoArgsConstructor
    @Getter
    public static class AntIndexScoreId implements Serializable {
        private String stockCode;
        private LocalDateTime bucketTime;

        public AntIndexScoreId(String stockCode, LocalDateTime bucketTime) {
            this.stockCode = stockCode;
            this.bucketTime = bucketTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AntIndexScoreId)) return false;
            AntIndexScoreId that = (AntIndexScoreId) o;
            return Objects.equals(stockCode, that.stockCode) &&
                    Objects.equals(bucketTime, that.bucketTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stockCode, bucketTime);
        }
    }
}