package com.keepkind;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkService {

    private final JdbcTemplate jdbc;

    public ChunkService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        int start = 0;

        while (start < t.length()) {
            int end = Math.min(t.length(), start + chunkSize);
            out.add(t.substring(start, end));
            if (end == t.length()) break;
            start = Math.max(0, end - overlap);
        }
        return out;
    }

    public void insertChunks(long itemId, long sourceId, List<String> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            int idx = i;
            String content = chunks.get(i);
            jdbc.update(
                "INSERT INTO chunks(item_id, source_id, chunk_index, content) VALUES (?, ?, ?, ?)",
                itemId, sourceId, idx, content
            );
        }
    }
}
