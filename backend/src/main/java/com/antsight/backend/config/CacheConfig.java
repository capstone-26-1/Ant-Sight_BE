package com.antsight.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String VOLJUMP_TODAY = "voljumpToday";
    public static final String VOLJUMP_DETAIL = "voljumpDetail";
    public static final String VOLJUMP_HISTORY = "voljumpHistory";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                VOLJUMP_TODAY, VOLJUMP_DETAIL, VOLJUMP_HISTORY);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(1_000));
        return manager;
    }
}
