package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.util.Map;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final JdbcTemplate jdbc;

    public ItemController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CreateItemRequest(String name, String category) {}

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateItemRequest req) {
        if (req == null || req.name() == null || req.name().trim().isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO items(name, category) VALUES (?, ?)",
                new String[]{"id"}
            );
            ps.setString(1, req.name().trim());
            ps.setString(2, req.category());
            return ps;
        }, kh);

        Number id = kh.getKey();
        return Map.of("id", id.longValue(), "name", req.name().trim(), "category", req.category());
    }
}
