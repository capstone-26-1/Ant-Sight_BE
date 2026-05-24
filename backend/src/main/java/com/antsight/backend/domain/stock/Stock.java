package com.antsight.backend.domain.stock;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stocks", catalog = "stock_data")
@Getter
@NoArgsConstructor
public class Stock {

    @Id
    @Column(length = 20)
    private String ticker;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Market market;

    @Column(length = 100)
    private String sector;

    @Column(name = "market_cap_rank")
    private Integer marketCapRank;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
