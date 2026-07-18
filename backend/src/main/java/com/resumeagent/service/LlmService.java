package com.resumeagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.config.ResumeAgentProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmService.class);

    private final ResumeAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public LlmService(ResumeAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<String> generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, userPrompt, List.of());
    }

    public Optional<String> generate(String systemPrompt, String userPrompt, List<Map<String, String>> history) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            List<Map<String, String>> messages = new java.util.ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.addAll(history);
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model", properties.getOpenai().getChatModel());
            requestBody.put("temperature", properties.getOpenai().getTemperature());
            requestBody.put("max_tokens", properties.getOpenai().getMaxTokens());
            requestBody.put("top_p", properties.getOpenai().getTopP());
            requestBody.put("messages", messages);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildChatCompletionsUri())
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("LLM request failed, status={}", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return Optional.empty();
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(content.asText());
        } catch (Exception exception) {
            LOGGER.warn("LLM request failed", exception);
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        return isConfigured();
    }

    private boolean isConfigured() {
        return notBlank(properties.getOpenai().getBaseUrl()) && notBlank(properties.getOpenai().getApiKey());
    }

    private URI buildChatCompletionsUri() {
        String baseUrl = properties.getOpenai().getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/chat/completions")) {
            return URI.create(baseUrl);
        }
        return URI.create(baseUrl + "/chat/completions");
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
