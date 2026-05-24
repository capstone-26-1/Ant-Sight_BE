package com.antsight.backend.domain.antindex;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "scores", catalog = "ant_index")
@Getter
@NoArgsConstructor
public class AntIndexScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sentiment sentiment;

    @Column(name = "raw_data", columnDefinition = "json")
    private String rawData;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
