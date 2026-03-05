package com.keepkind;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OllamaEmbeddingClient {

    private final RestClient http;
    private final String baseUrl;
    private final String model;

    public OllamaEmbeddingClient(
            @Value("${keepkind.ollama.baseUrl}") String baseUrl,
            @Value("${keepkind.ollama.embedModel}") String model
    ) {
        this.http = RestClient.create();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    /**
     * Embeds a single input string using Ollama.
     *
     * Supports both Ollama response shapes:
     * 1) /api/embed returning {"embeddings":[[...]]}
     * 2) /api/embeddings returning {"embedding":[...]}
     */
    @SuppressWarnings("unchecked")
    public List<Double> embedOne(String text) {
        String t = (text == null) ? "" : text.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("text is required");

        // Prefer Ollama's /api/embeddings endpoint
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", t
        );

        Map<String, Object> resp = http.post()
                .uri(baseUrl + "/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (resp == null) {
            throw new IllegalStateException("Null response from embeddings endpoint");
        }

        // Shape A: {"embedding":[...]}
        if (resp.containsKey("embedding")) {
            Object e = resp.get("embedding");
            if (e instanceof List<?> list && !list.isEmpty()) {
                return (List<Double>) list;
            }
            throw new IllegalStateException("Empty embedding array");
        }

        // Shape B (older/multi): {"embeddings":[[...]]}
        if (resp.containsKey("embeddings")) {
            Object e = resp.get("embeddings");
            if (e instanceof List<?> outer && !outer.isEmpty()) {
                Object first = outer.get(0);
                if (first instanceof List<?> inner && !inner.isEmpty()) {
                    return (List<Double>) inner;
                }
            }
            throw new IllegalStateException("Empty embeddings array");
        }

        throw new IllegalStateException("Missing embedding(s) in response: expected 'embedding' or 'embeddings'");
    }
}