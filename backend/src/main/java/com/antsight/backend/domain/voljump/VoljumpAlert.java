package com.antsight.backend.domain.voljump;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "voljump_alert", catalog = "ant_index")
@Getter
@NoArgsConstructor
@IdClass(VoljumpAlert.VoljumpAlertId.class)
public class VoljumpAlert {

    @Id
    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Id
    @Column(name = "base_day", nullable = false)
    private LocalDate baseDay;

    @Column(name = "jump_prob", precision = 6, scale = 4)
    private BigDecimal jumpProb;

    @Column(name = "posts")
    private Integer posts;

    @Column(name = "disp", precision = 6, scale = 4)
    private BigDecimal disp;

    @Column(name = "scored_at")
    private LocalDateTime scoredAt;

    @Column(name = "status", length = 10)
    private String status;

    @NoArgsConstructor
    @Getter
    public static class VoljumpAlertId implements Serializable {
        private String stockCode;
        private LocalDate baseDay;

        public VoljumpAlertId(String stockCode, LocalDate baseDay) {
            this.stockCode = stockCode;
            this.baseDay = baseDay;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VoljumpAlertId that)) return false;
            return Objects.equals(stockCode, that.stockCode) &&
                    Objects.equals(baseDay, that.baseDay);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stockCode, baseDay);
        }
    }
}
