package com.example.demo;

import com.example.demo.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    boolean existsByStockCodeAndTitleAndTimestamp(
        String stockCode, String title, String timestamp
    );

    long countByStockCode(String stockCode);

    @Query("""
        SELECT p.stockCode, COUNT(p)
        FROM Post p
        GROUP BY p.stockCode
        ORDER BY COUNT(p) DESC
    """)
    List<Object[]> countGroupByStockCode();

    @Query("SELECT COUNT(DISTINCT p.stockCode) FROM Post p")
    long countDistinctStockCode();
}
