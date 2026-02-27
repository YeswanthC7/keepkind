package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/items/{itemId}")
public class AskController {

    private final JdbcTemplate jdbc;
    private final OllamaEmbeddingClient embedder;
    private final OllamaChatClient chat;

    public AskController(JdbcTemplate jdbc, OllamaEmbeddingClient embedder, OllamaChatClient chat) {
        this.jdbc = jdbc;
        this.embedder = embedder;
        this.chat = chat;
    }

    @GetMapping("/ask")
    public Map<String, Object> ask(
            @PathVariable long itemId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int k
    ) {
        if (q == null || q.trim().isEmpty()) throw new IllegalArgumentException("q is required");
        int topK = Math.max(1, Math.min(k, 10));

        // 1) Embed query
        var qVec = embedder.embedOne(q.trim());
        String pgVec = toPgVector(qVec);

        // 2) Retrieve top-k chunks
        List<Map<String, Object>> ctx = jdbc.queryForList(
                "SELECT id, source_id, chunk_index, content, (embedding <=> ?::vector) AS distance " +
                "FROM chunks " +
                "WHERE item_id = ? AND embedding IS NOT NULL " +
                "ORDER BY embedding <=> ?::vector " +
                "LIMIT ?",
                pgVec, itemId, pgVec, topK
        );

        // 3) Build prompt
        StringBuilder contextBlock = new StringBuilder();
        for (Map<String, Object> row : ctx) {
            contextBlock.append("CHUNK ")
                    .append(row.get("id"))
                    .append(" (source ")
                    .append(row.get("source_id"))
                    .append("):\n")
                    .append(row.get("content"))
                    .append("\n\n");
        }

        String system = """
                You are KeepKind. Answer using ONLY the provided context.
                If the answer is not in the context, say: "I don't have enough information in the provided sources."
                Do not guess. Keep it concise.
                """;

        String user = "Question:\n" + q.trim() + "\n\nContext:\n" + contextBlock;

        // 4) Generate answer
        String answer = chat.chat(system, user);

        // 5) Return answer + citations
        List<Map<String, Object>> citations = ctx.stream()
                .map(r -> Map.of(
                        "chunkId", r.get("id"),
                        "sourceId", r.get("source_id"),
                        "chunkIndex", r.get("chunk_index"),
                        "distance", r.get("distance")
                ))
                .toList();

        return Map.of(
                "itemId", itemId,
                "question", q.trim(),
                "answer", answer,
                "contextUsed", ctx.size(),
                "citations", citations
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
