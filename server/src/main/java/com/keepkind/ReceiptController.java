package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/items/{itemId}")
public class ReceiptController {

    private final JdbcTemplate jdbc;
    private final OllamaEmbeddingClient embedder;
    private final OllamaChatClient chat;

    public ReceiptController(JdbcTemplate jdbc, OllamaEmbeddingClient embedder, OllamaChatClient chat) {
        this.jdbc = jdbc;
        this.embedder = embedder;
        this.chat = chat;
    }

    @PostMapping("/receipt")
    public Map<String, Object> createReceipt(
            @PathVariable long itemId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int k
    ) {
        if (q == null || q.trim().isEmpty()) throw new IllegalArgumentException("q is required");
        int topK = Math.max(1, Math.min(k, 10));

        // Retrieve context (same as /ask)
        var qVec = embedder.embedOne(q.trim());
        String pgVec = toPgVector(qVec);

        List<Map<String, Object>> ctx = jdbc.queryForList(
                "SELECT id, source_id, chunk_index, content, (embedding <=> ?::vector) AS distance " +
                        "FROM chunks " +
                        "WHERE item_id = ? AND embedding IS NOT NULL " +
                        "ORDER BY embedding <=> ?::vector " +
                        "LIMIT ?",
                pgVec, itemId, pgVec, topK
        );

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
                You are KeepKind. Create a decision receipt using ONLY the provided context.
                Output MUST be in this exact format:

                RECOMMENDATION: <one of maintain|repair|resell|recycle|keep>
                RATIONALE: <1-3 short sentences, grounded in context>
                ASSUMPTIONS: <comma-separated list, or 'none'>

                If context is insufficient, use:
                RECOMMENDATION: keep
                RATIONALE: I don't have enough information in the provided sources.
                ASSUMPTIONS: none
                """;

        String user = "Question:\n" + q.trim() + "\n\nContext:\n" + contextBlock;
        String out = chat.chat(system, user);

        ParsedReceipt pr = ParsedReceipt.parse(out);

        // Clean citations JSON for persistence (no chunk text/content)
        String citationsJson = ctx.stream()
                .map(r -> String.format("{\"chunkId\":%s,\"sourceId\":%s,\"distance\":%s}",
                        r.get("id"), r.get("source_id"), r.get("distance")))
                .reduce((a, b) -> a + "," + b)
                .map(s -> "[" + s + "]")
                .orElse("[]");

        List<Map<String, Object>> cleanCitations = ctx.stream()
                .map(r -> Map.of(
                        "chunkId", r.get("id"),
                        "sourceId", r.get("source_id"),
                        "distance", r.get("distance")
                ))
                .toList();

        String assumptionsJson = pr.assumptions().isEmpty()
                ? "[]"
                : pr.assumptions().stream()
                .map(a -> "\"" + a.replace("\"", "\\\"") + "\"")
                .reduce((a, b) -> a + "," + b)
                .map(s -> "[" + s + "]")
                .orElse("[]");

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO receipts(item_id, question, recommendation, rationale, citations, assumptions, chat_model, embed_model, k_used, prompt_version) " +
                            "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setLong(1, itemId);
            ps.setString(2, q.trim());
            ps.setString(3, pr.recommendation());
            ps.setString(4, pr.rationale());
            ps.setString(5, citationsJson);
            ps.setString(6, assumptionsJson);
            ps.setString(7, "llama3.2:3b");
            ps.setString(8, "nomic-embed-text");
            ps.setInt(9, topK);
            ps.setString(10, "receipt-v1");
            return ps;
        }, kh);

        long receiptId = kh.getKey().longValue();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("receiptId", receiptId);
        resp.put("itemId", itemId);
        resp.put("question", q.trim());
        resp.put("recommendation", pr.recommendation());
        resp.put("rationale", pr.rationale());
        resp.put("assumptions", pr.assumptions());
        resp.put("citations", cleanCitations);
        resp.put("chat_model", "llama3.2:3b");
        resp.put("embed_model", "nomic-embed-text");
        resp.put("k_used", topK);
        resp.put("prompt_version", "receipt-v1");
        return resp;
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

    record ParsedReceipt(String recommendation, String rationale, List<String> assumptions) {

        static ParsedReceipt parse(String s) {
            String rec = "keep";
            String rat = "I don't have enough information in the provided sources.";
            String ass = "none";

            for (String line : s.split("\n")) {
                String t = line.trim();
                if (t.toUpperCase().startsWith("RECOMMENDATION:")) rec = t.substring("RECOMMENDATION:".length()).trim();
                if (t.toUpperCase().startsWith("RATIONALE:")) rat = t.substring("RATIONALE:".length()).trim();
                if (t.toUpperCase().startsWith("ASSUMPTIONS:")) ass = t.substring("ASSUMPTIONS:".length()).trim();
            }

            List<String> assumptions = (ass.equalsIgnoreCase("none") || ass.isBlank())
                    ? List.of()
                    : List.of(ass.split("\\s*,\\s*"));

            return new ParsedReceipt(rec, rat, assumptions);
        }
    }

    @GetMapping("/receipts")
    public Map listReceipts(
            @PathVariable long itemId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);

        var rows = jdbc.queryForList(
                "SELECT id, item_id, question, recommendation, rationale, " +
                        "citations::text AS citations, assumptions::text AS assumptions, " +
                        "chat_model, embed_model, k_used, prompt_version " +
                        "FROM receipts " +
                        "WHERE item_id = ? " +
                        "ORDER BY id DESC " +
                        "LIMIT ? OFFSET ?",
                itemId, safeLimit, safeOffset
        );

        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("itemId", itemId);
        resp.put("limit", safeLimit);
        resp.put("offset", safeOffset);
        resp.put("count", rows.size());
        resp.put("receipts", rows);
        return resp;
    }

    @GetMapping("/receipts/{receiptId}/export.md")
    public org.springframework.http.ResponseEntity<String> exportReceiptMarkdownForItem(
            @PathVariable long itemId,
            @PathVariable long receiptId
    ) {
        try {
            var row = jdbc.queryForMap(
                    "SELECT id, item_id, question, recommendation, rationale, citations, assumptions, " +
                            "chat_model, embed_model, k_used, prompt_version " +
                            "FROM receipts WHERE id = ? AND item_id = ?",
                    receiptId, itemId
            );

            StringBuilder md = new StringBuilder();
            md.append("# KeepKind Decision Receipt\n\n");
            md.append("**Receipt ID:** ").append(row.get("id")).append("\n\n");
            md.append("**Item ID:** ").append(row.get("item_id")).append("\n\n");

            md.append("## Generation metadata\n");
            md.append("- chat_model: ").append(row.get("chat_model")).append("\n");
            md.append("- embed_model: ").append(row.get("embed_model")).append("\n");
            md.append("- k_used: ").append(row.get("k_used")).append("\n");
            md.append("- prompt_version: ").append(row.get("prompt_version")).append("\n\n");

            md.append("## Question\n");
            md.append(row.get("question")).append("\n\n");

            md.append("## Recommendation\n");
            md.append(row.get("recommendation")).append("\n\n");

            md.append("## Rationale\n");
            md.append(row.get("rationale")).append("\n\n");

            md.append("## Assumptions\n");
            String assumptions = String.valueOf(row.get("assumptions"));
            if (assumptions.equals("[]") || assumptions.equalsIgnoreCase("null")) {
                md.append("none\n\n");
            } else {
                md.append(assumptions).append("\n\n");
            }

            md.append("## Citations\n");
            md.append("```json\n");
            md.append(row.get("citations"));
            md.append("\n```\n");

            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=keepkind-receipt-" + receiptId + ".md")
                    .contentType(org.springframework.http.MediaType.valueOf("text/markdown"))
                    .body(md.toString());

        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "receipt not found for item"
            );
        }
    }

    @GetMapping("/receipts/{receiptId}")
    public Map getReceiptForItem(@PathVariable long itemId, @PathVariable long receiptId) {
        try {
                return jdbc.queryForMap(
                        "SELECT id, item_id, question, recommendation, rationale, " +
                                "citations::text AS citations, assumptions::text AS assumptions, " +
                                "chat_model, embed_model, k_used, prompt_version " +
                                "FROM receipts " +
                                "WHERE id = ? AND item_id = ?",
                        receiptId, itemId
                );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "receipt not found for item"
                );
        }
    }
}