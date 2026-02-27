package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/items/{itemId}/chunks")
public class ChunkSearchController {

    private final JdbcTemplate jdbc;

    public ChunkSearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @PathVariable long itemId,
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (q == null || q.trim().isEmpty()) {
            throw new IllegalArgumentException("q is required");
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));

        return jdbc.queryForList(
                "SELECT id, source_id, chunk_index, content " +
                "FROM chunks " +
                "WHERE item_id = ? AND content ILIKE ? " +
                "ORDER BY id DESC " +
                "LIMIT ?",
                itemId, "%" + q.trim() + "%", safeLimit
        );
    }
}
