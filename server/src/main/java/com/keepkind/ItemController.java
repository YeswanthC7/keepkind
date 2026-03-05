package com.keepkind;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
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

        String name = req.name().trim();
        String category = (req.category() == null || req.category().trim().isEmpty())
                ? "unknown"
                : req.category().trim();

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO items(name, category) VALUES (?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, name);
            ps.setString(2, category);
            return ps;
        }, kh);

        Number id = kh.getKey();
        if (id == null) {
            // Avoid Map.of NPE and make the failure explicit
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to create item (missing id)");
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id.longValue());
        resp.put("name", name);
        resp.put("category", category);
        return resp;
    }
}