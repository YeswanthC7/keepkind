package com.keepkind;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/receipts")
public class ReceiptReadController {

    private final JdbcTemplate jdbc;

    public ReceiptReadController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{receiptId}")
    public Map getReceipt(@PathVariable long receiptId) {
        try {
            // Cast JSONB to text so the response is stable without extra dependencies.
            return jdbc.queryForMap(
                    "SELECT id, item_id, question, recommendation, rationale, " +
                            "citations::text AS citations, assumptions::text AS assumptions, " +
                            "chat_model, embed_model, k_used, prompt_version " +
                            "FROM receipts " +
                            "WHERE id = ?",
                    receiptId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "receipt not found");
        }
    }

    @GetMapping("/{receiptId}/export.md")
    public org.springframework.http.ResponseEntity<String> exportReceiptMarkdown(@PathVariable long receiptId) {
        try {
            var row = jdbc.queryForMap(
                    "SELECT id, item_id, question, recommendation, rationale, " +
                            "citations::text AS citations, assumptions::text AS assumptions, " +
                            "chat_model, embed_model, k_used, prompt_version " +
                            "FROM receipts WHERE id = ?",
                    receiptId
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
            if ("[]".equals(assumptions) || "null".equalsIgnoreCase(assumptions)) {
                md.append("none\n\n");
            } else {
                md.append(assumptions).append("\n\n");
            }

            md.append("## Citations\n");
            md.append("```json\n");
            md.append(row.get("citations"));
            md.append("\n```\n");

            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=keepkind-receipt-" + receiptId + ".md")
                    .contentType(org.springframework.http.MediaType.valueOf("text/markdown"))
                    .body(md.toString());

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "receipt not found");
        }
    }
}