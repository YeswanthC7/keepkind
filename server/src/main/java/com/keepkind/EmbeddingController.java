package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sources")
public class EmbeddingController {

    private final JdbcTemplate jdbc;
    private final OllamaEmbeddingClient embedder;

    public EmbeddingController(JdbcTemplate jdbc, OllamaEmbeddingClient embedder) {
        this.jdbc = jdbc;
        this.embedder = embedder;
    }

    @PostMapping("/{sourceId}/embed")
    public Map<String, Object> embedSource(@PathVariable long sourceId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, item_id, content FROM chunks WHERE source_id = ? ORDER BY chunk_index ASC",
                sourceId
        );

        int updated = 0;
        for (Map<String, Object> r : rows) {
            long chunkId = ((Number) r.get("id")).longValue();
            String content = (String) r.get("content");

            var vec = embedder.embedOne(content);

            // Store as pgvector literal: '[1,2,3]'
            String pgVec = toPgVector(vec);

            updated += jdbc.update(
                    "UPDATE chunks SET embedding = ?::vector WHERE id = ?",
                    pgVec, chunkId
            );
        }

        return Map.of("sourceId", sourceId, "chunksEmbedded", updated);
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
