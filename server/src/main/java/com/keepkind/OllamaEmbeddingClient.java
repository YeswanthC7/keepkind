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

    @SuppressWarnings("unchecked")
    public List<Double> embedOne(String text) {
        Map<String, Object> body = Map.of(
                "model", model,
                "input", text
        );

        Map<String, Object> resp = http.post()
                .uri(baseUrl + "/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (resp == null || !resp.containsKey("embeddings")) {
            throw new IllegalStateException("Missing embeddings in response");
        }

        // Ollama returns: {"embeddings":[[...]]} for single input
        List<List<Double>> embs = (List<List<Double>>) resp.get("embeddings");
        if (embs == null || embs.isEmpty()) {
            throw new IllegalStateException("Empty embeddings array");
        }
        return embs.get(0);
    }
}
