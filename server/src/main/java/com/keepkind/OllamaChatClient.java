package com.keepkind;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OllamaChatClient {

    private final RestClient http;
    private final String baseUrl;
    private final String model;

    public OllamaChatClient(
            @Value("${keepkind.ollama.baseUrl}") String baseUrl,
            @Value("${keepkind.ollama.chatModel}") String model
    ) {
        this.http = RestClient.create();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @SuppressWarnings("unchecked")
    public String chat(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)
                )
        );

        Map<String, Object> resp = http.post()
                .uri(baseUrl + "/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (resp == null || !resp.containsKey("message")) {
            throw new IllegalStateException("Missing message in response");
        }
        Map<String, Object> msg = (Map<String, Object>) resp.get("message");
        Object content = msg.get("content");
        return content == null ? "" : content.toString();
    }
}
