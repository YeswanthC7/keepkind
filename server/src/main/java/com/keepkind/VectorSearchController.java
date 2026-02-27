package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/items/{itemId}/vector")
public class VectorSearchController {

    private final JdbcTemplate jdbc;
    private final OllamaEmbeddingClient embedder;

    public VectorSearchController(JdbcTemplate jdbc, OllamaEmbeddingClient embedder) {
        this.jdbc = jdbc;
        this.embedder = embedder;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @PathVariable long itemId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int k
    ) {
        if (q == null || q.trim().isEmpty()) throw new IllegalArgumentException("q is required");
        int topK = Math.max(1, Math.min(k, 20));

        var qVec = embedder.embedOne(q.trim());
        String pgVec = toPgVector(qVec);

        return jdbc.queryForList(
                "SELECT id, source_id, chunk_index, content, (embedding <=> ?::vector) AS distance " +
                "FROM chunks " +
                "WHERE item_id = ? AND embedding IS NOT NULL " +
                "ORDER BY embedding <=> ?::vector " +
                "LIMIT ?",
                pgVec, itemId, pgVec, topK
        );
    }

    private static String toPgVector(List<Double> v) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < v.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(v.get(i));
        }
        sb.append(']');
        return sb.toString();
    }
}
