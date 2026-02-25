package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health/db")
    public Map<String, Object> dbHealth() {
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of("ok", true, "db", one);
    }
}
