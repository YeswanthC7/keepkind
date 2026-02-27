package com.keepkind;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/debug")
public class EmbeddingTestController {

    private final OllamaEmbeddingClient embeddings;

    public EmbeddingTestController(OllamaEmbeddingClient embeddings) {
        this.embeddings = embeddings;
    }

    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam String text) {
        var vec = embeddings.embedOne(text);
        return Map.of(
                "dims", vec.size(),
                "head", vec.subList(0, Math.min(5, vec.size()))
        );
    }
}
