package com.antsight.backend.domain.antindex;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByStockCodeAndCreatedAtBetween(String stockCode,
                                                  LocalDateTime from,
                                                  LocalDateTime to);
}
