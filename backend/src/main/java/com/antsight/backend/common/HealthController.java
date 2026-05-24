package com.antsight.backend.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public Map<String, String> health() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("db", checkDb());
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    private String checkDb() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }
}
