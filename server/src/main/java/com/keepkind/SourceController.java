package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/items/{itemId}/sources")
public class SourceController {

    private final JdbcTemplate jdbc;
    private final ChunkService chunkService;

    public SourceController(JdbcTemplate jdbc, ChunkService chunkService) {
    	this.jdbc = jdbc;
    	this.chunkService = chunkService;
    }

    public record AddTextSourceRequest(String title, String text, String trustLevel) {}

    @PostMapping("/text")
    public Map<String, Object> addText(@PathVariable long itemId, @RequestBody AddTextSourceRequest req) {
        if (req == null || req.text() == null || req.text().trim().isEmpty()) {
            throw new IllegalArgumentException("text is required");
        }

        String uri = (req.title() == null || req.title().isBlank()) ? "text-source" : req.title().trim();
        String trust = (req.trustLevel() == null || req.trustLevel().isBlank()) ? "normal" : req.trustLevel().trim();

        String hash = sha256(req.text());

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO sources(item_id, type, uri, trust_level, content_hash) VALUES (?, 'text', ?, ?, ?)",
                new String[]{"id"}
            );
            ps.setLong(1, itemId);
            ps.setString(2, uri);
            ps.setString(3, trust);
            ps.setString(4, hash);
            return ps;
        }, kh);

        Number id = kh.getKey();
	var parts = chunkService.chunk(req.text(), 800, 120);
	chunkService.insertChunks(itemId, id.longValue(), parts);
        return Map.of( "sourceId", id.longValue(), "itemId", itemId, "type", "text", "uri", uri, "contentHash", hash, "chunksCreated", parts.size());
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
