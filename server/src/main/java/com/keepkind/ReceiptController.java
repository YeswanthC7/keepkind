package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Web MVP: generate a single "decision artifact" containing ALL 4 plans
     * (maintain/repair/resell/recycle) + citations + assumptions.
     *
     * Does NOT persist to receipts table.
     */
    @PostMapping("/decision")
    public Map<String, Object> createDecision(
            @PathVariable long itemId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int k
    ) {
        if (q == null || q.trim().isEmpty()) throw new IllegalArgumentException("q is required");
        int topK = Math.max(1, Math.min(k, 10));

        // Retrieve context (same retrieval logic as /ask and /receipt)
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
                You are KeepKind. Generate a decision artifact using ONLY the provided context.

                Output MUST be in this exact format and with these exact section headers:

                SUMMARY: <one short line describing the item + situation>
                CONFIDENCE: <number 0.00 to 1.00>

                MAINTAIN_STEPS:
                - <step>
                MAINTAIN_WHAT_CHANGES_THIS:
                - <condition>

                REPAIR_STEPS:
                - <step>
                REPAIR_WHAT_CHANGES_THIS:
                - <condition>

                RESELL_STEPS:
                - <step>
                RESELL_WHAT_CHANGES_THIS:
                - <condition>

                RECYCLE_STEPS:
                - <step>
                RECYCLE_WHAT_CHANGES_THIS:
                - <condition>

                ASSUMPTIONS:
                - <assumption>
                (or a single line "- none")

                Rules:
                - Use only facts supported by context. If context is insufficient, be explicit in steps (e.g., "I don't have enough information from the provided sources.").
                - Keep each bullet short (<= 1 sentence).
                - Provide 2-6 bullets per STEPS and 1-3 bullets per WHAT_CHANGES_THIS.
                - Do NOT invent store names, prices, or locations.
                """;

        String user = "Question:\n" + q.trim() + "\n\nContext:\n" + contextBlock;
        String out = chat.chat(system, user);

        ParsedDecision pd = ParsedDecision.parse(out);

        List<Map<String, Object>> cleanCitations = ctx.stream()
                .map(r -> Map.of(
                        "chunkId", r.get("id"),
                        "sourceId", r.get("source_id"),
                        "distance", r.get("distance")
                ))
                .toList();

        // Build response (avoid Map.of with lots of keys)
        Map<String, Object> maintain = new LinkedHashMap<>();
        maintain.put("title", "Maintain");
        maintain.put("steps", pd.maintainSteps);
        maintain.put("whatChangesThis", pd.maintainWhatChangesThis);

        Map<String, Object> repair = new LinkedHashMap<>();
        repair.put("title", "Repair");
        repair.put("steps", pd.repairSteps);
        repair.put("whatChangesThis", pd.repairWhatChangesThis);

        Map<String, Object> resell = new LinkedHashMap<>();
        resell.put("title", "Resell");
        resell.put("steps", pd.resellSteps);
        resell.put("whatChangesThis", pd.resellWhatChangesThis);

        Map<String, Object> recycle = new LinkedHashMap<>();
        recycle.put("title", "Recycle");
        recycle.put("steps", pd.recycleSteps);
        recycle.put("whatChangesThis", pd.recycleWhatChangesThis);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("maintain", maintain);
        options.put("repair", repair);
        options.put("resell", resell);
        options.put("recycle", recycle);

        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("chatModel", "llama3.2:3b");
        generation.put("embedModel", "nomic-embed-text");
        generation.put("kUsed", topK);
        generation.put("promptVersion", "decision-v1");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("itemId", itemId);
        resp.put("summary", pd.summary);
        resp.put("confidence", pd.confidence);
        resp.put("options", options);
        resp.put("citations", cleanCitations);
        resp.put("assumptions", pd.assumptions);
        resp.put("generation", generation);
        return resp;
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

        Integer nextV = jdbc.queryForObject(
                "SELECT COALESCE(MAX(receipt_version), 0) + 1 FROM receipts WHERE item_id = ? AND deleted_at IS NULL",
                Integer.class,
                itemId
        );
        int receiptVersion = (nextV == null) ? 1 : nextV;

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO receipts(item_id, receipt_version, question, recommendation, rationale, citations, assumptions, chat_model, embed_model, k_used, prompt_version) " +
                            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setLong(1, itemId);
            ps.setInt(2, receiptVersion);
            ps.setString(3, q.trim());
            ps.setString(4, pr.recommendation());
            ps.setString(5, pr.rationale());
            ps.setString(6, citationsJson);
            ps.setString(7, assumptionsJson);
            ps.setString(8, "llama3.2:3b");
            ps.setString(9, "nomic-embed-text");
            ps.setInt(10, topK);
            ps.setString(11, "receipt-v1");
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
        resp.put("receipt_version", receiptVersion);
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

    static class ParsedDecision {
        String summary = "Item decision";
        double confidence = 0.50;

        List<String> maintainSteps = List.of("I don't have enough information in the provided sources.");
        List<String> maintainWhatChangesThis = List.of("Add more item details or sources.");

        List<String> repairSteps = List.of("I don't have enough information in the provided sources.");
        List<String> repairWhatChangesThis = List.of("Add more item details or sources.");

        List<String> resellSteps = List.of("I don't have enough information in the provided sources.");
        List<String> resellWhatChangesThis = List.of("Add more item details or sources.");

        List<String> recycleSteps = List.of("I don't have enough information in the provided sources.");
        List<String> recycleWhatChangesThis = List.of("Add more item details or sources.");

        List<String> assumptions = List.of();

        static ParsedDecision parse(String s) {
            ParsedDecision pd = new ParsedDecision();

            String current = null;
            List<String> buf = new ArrayList<>();

            // Temp holders
            List<String> ms = null, mw = null, rs = null, rw = null, ss = null, sw = null, cs = null, cw = null, a = null;

            for (String raw : s.split("\n")) {
                String line = raw.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("SUMMARY:")) {
                    pd.summary = line.substring("SUMMARY:".length()).trim();
                    continue;
                }
                if (line.startsWith("CONFIDENCE:")) {
                    String v = line.substring("CONFIDENCE:".length()).trim();
                    pd.confidence = parseConfidence(v);
                    continue;
                }

                // Section headers
                if (isHeader(line)) {
                    // flush previous buffer
                    if (current != null) {
                        List<String> flushed = normalizeBullets(buf);
                        if ("MAINTAIN_STEPS".equals(current)) ms = flushed;
                        if ("MAINTAIN_WHAT_CHANGES_THIS".equals(current)) mw = flushed;
                        if ("REPAIR_STEPS".equals(current)) rs = flushed;
                        if ("REPAIR_WHAT_CHANGES_THIS".equals(current)) rw = flushed;
                        if ("RESELL_STEPS".equals(current)) ss = flushed;
                        if ("RESELL_WHAT_CHANGES_THIS".equals(current)) sw = flushed;
                        if ("RECYCLE_STEPS".equals(current)) cs = flushed;
                        if ("RECYCLE_WHAT_CHANGES_THIS".equals(current)) cw = flushed;
                        if ("ASSUMPTIONS".equals(current)) a = normalizeAssumptions(flushed);
                    }
                    current = line.substring(0, line.length() - 1); // remove trailing ':'
                    buf = new ArrayList<>();
                    continue;
                }

                // bullet line
                buf.add(line);
            }

            // flush last
            if (current != null) {
                List<String> flushed = normalizeBullets(buf);
                if ("MAINTAIN_STEPS".equals(current)) ms = flushed;
                if ("MAINTAIN_WHAT_CHANGES_THIS".equals(current)) mw = flushed;
                if ("REPAIR_STEPS".equals(current)) rs = flushed;
                if ("REPAIR_WHAT_CHANGES_THIS".equals(current)) rw = flushed;
                if ("RESELL_STEPS".equals(current)) ss = flushed;
                if ("RESELL_WHAT_CHANGES_THIS".equals(current)) sw = flushed;
                if ("RECYCLE_STEPS".equals(current)) cs = flushed;
                if ("RECYCLE_WHAT_CHANGES_THIS".equals(current)) cw = flushed;
                if ("ASSUMPTIONS".equals(current)) a = normalizeAssumptions(flushed);
            }

            // Apply defaults if missing
            if (ms != null && !ms.isEmpty()) pd.maintainSteps = ms;
            if (mw != null && !mw.isEmpty()) pd.maintainWhatChangesThis = mw;

            if (rs != null && !rs.isEmpty()) pd.repairSteps = rs;
            if (rw != null && !rw.isEmpty()) pd.repairWhatChangesThis = rw;

            if (ss != null && !ss.isEmpty()) pd.resellSteps = ss;
            if (sw != null && !sw.isEmpty()) pd.resellWhatChangesThis = sw;

            if (cs != null && !cs.isEmpty()) pd.recycleSteps = cs;
            if (cw != null && !cw.isEmpty()) pd.recycleWhatChangesThis = cw;

            if (a != null) pd.assumptions = a;

            // Ensure summary not empty
            if (pd.summary == null || pd.summary.isBlank()) pd.summary = "Item decision";
            // Clamp confidence
            if (pd.confidence < 0.0) pd.confidence = 0.0;
            if (pd.confidence > 1.0) pd.confidence = 1.0;

            return pd;
        }

        private static boolean isHeader(String line) {
            return line.endsWith(":") && (
                    line.equals("MAINTAIN_STEPS:") ||
                            line.equals("MAINTAIN_WHAT_CHANGES_THIS:") ||
                            line.equals("REPAIR_STEPS:") ||
                            line.equals("REPAIR_WHAT_CHANGES_THIS:") ||
                            line.equals("RESELL_STEPS:") ||
                            line.equals("RESELL_WHAT_CHANGES_THIS:") ||
                            line.equals("RECYCLE_STEPS:") ||
                            line.equals("RECYCLE_WHAT_CHANGES_THIS:") ||
                            line.equals("ASSUMPTIONS:")
            );
        }

        private static List<String> normalizeBullets(List<String> lines) {
            List<String> out = new ArrayList<>();
            for (String l : lines) {
                String t = l.trim();
                if (t.startsWith("-")) t = t.substring(1).trim();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }

        private static List<String> normalizeAssumptions(List<String> bullets) {
            if (bullets.size() == 1 && bullets.get(0).equalsIgnoreCase("none")) return List.of();
            return bullets;
        }

        private static double parseConfidence(String v) {
            try {
                double d = Double.parseDouble(v);
                if (d < 0.0) return 0.0;
                if (d > 1.0) return 1.0;
                return d;
            } catch (Exception e) {
                return 0.50;
            }
        }
    }

    @GetMapping("/receipts")
    public Map listReceipts(
            @PathVariable long itemId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);

        String where = includeDeleted ? "WHERE item_id = ?" : "WHERE item_id = ? AND deleted_at IS NULL";

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM receipts " + where,
                Integer.class,
                itemId
        );
        int safeTotal = (total == null) ? 0 : total;

        var rows = jdbc.queryForList(
                "SELECT id, item_id, created_at, receipt_version, question, recommendation, rationale, " +
                        "citations::text AS citations, assumptions::text AS assumptions, " +
                        "chat_model, embed_model, k_used, prompt_version, deleted_at " +
                        "FROM receipts " +
                        where + " " +
                        "ORDER BY created_at DESC, id DESC " +
                        "LIMIT ? OFFSET ?",
                itemId, safeLimit, safeOffset
        );

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("itemId", itemId);
        resp.put("limit", safeLimit);
        resp.put("offset", safeOffset);
        resp.put("includeDeleted", includeDeleted);
        resp.put("count", rows.size());
        resp.put("total", safeTotal);
        resp.put("receipts", rows);
        return resp;
    }

    @GetMapping("/receipts/latest")
    public Map getLatestReceiptForItem(@PathVariable long itemId) {
        try {
            return jdbc.queryForMap(
                    "SELECT id, item_id, created_at, receipt_version, question, recommendation, rationale, " +
                            "citations::text AS citations, assumptions::text AS assumptions, " +
                            "chat_model, embed_model, k_used, prompt_version, deleted_at " +
                            "FROM receipts " +
                            "WHERE item_id = ? AND deleted_at IS NULL " +
                            "ORDER BY created_at DESC, id DESC " +
                            "LIMIT 1",
                    itemId
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "no receipts found for item"
            );
        }
    }

    @GetMapping("/receipts/{receiptId}")
    public Map getReceiptForItem(@PathVariable long itemId, @PathVariable long receiptId) {
        try {
            return jdbc.queryForMap(
                    "SELECT id, item_id, created_at, receipt_version, question, recommendation, rationale, " +
                            "citations::text AS citations, assumptions::text AS assumptions, " +
                            "chat_model, embed_model, k_used, prompt_version, deleted_at " +
                            "FROM receipts " +
                            "WHERE id = ? AND item_id = ? AND deleted_at IS NULL",
                    receiptId, itemId
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "receipt not found for item"
            );
        }
    }

    @DeleteMapping("/receipts/{receiptId}")
    public Map<String, Object> softDeleteReceipt(
            @PathVariable long itemId,
            @PathVariable long receiptId
    ) {
        int updated = jdbc.update(
                "UPDATE receipts SET deleted_at = NOW() WHERE id = ? AND item_id = ? AND deleted_at IS NULL",
                receiptId, itemId
        );

        if (updated == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "receipt not found or already deleted"
            );
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("receiptId", receiptId);
        resp.put("itemId", itemId);
        resp.put("status", "deleted");
        return resp;
    }

    @GetMapping("/receipts/{receiptId}/export.md")
    public org.springframework.http.ResponseEntity<String> exportReceiptMarkdownForItem(
            @PathVariable long itemId,
            @PathVariable long receiptId
    ) {
        try {
            var row = jdbc.queryForMap(
                    "SELECT id, item_id, created_at, receipt_version, question, recommendation, rationale, " +
                            "citations::text AS citations, assumptions::text AS assumptions, " +
                            "chat_model, embed_model, k_used, prompt_version, deleted_at " +
                            "FROM receipts WHERE id = ? AND item_id = ? AND deleted_at IS NULL",
                    receiptId, itemId
            );

            StringBuilder md = new StringBuilder();
            md.append("# KeepKind Decision Receipt\n\n");
            md.append("**Receipt ID:** ").append(row.get("id")).append("\n\n");
            md.append("**Item ID:** ").append(row.get("item_id")).append("\n\n");
            md.append("**Created At:** ").append(row.get("created_at")).append("\n\n");
            md.append("**Receipt Version:** ").append(row.get("receipt_version")).append("\n\n");

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
}