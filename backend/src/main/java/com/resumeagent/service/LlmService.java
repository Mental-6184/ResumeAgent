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
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmService.class);
    private static final ObjectMapper STREAM_MAPPER = new ObjectMapper();

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
            HttpRequest request = buildRequest(systemPrompt, userPrompt, history, false)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(buildRequestBody(systemPrompt, userPrompt, history, false)),
                            StandardCharsets.UTF_8))
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

    /**
     * 真流式生成：请求 OpenAI 兼容接口的 stream 模式，每收到一个增量 delta
     * 立即回调 onDelta（供 SSE 实时转发），完成后返回聚合全文。
     *
     * 降级语义与 {@link #generate} 一致：未配置、请求失败或无任何输出时返回
     * {@link Optional#empty()}；若已输出部分内容后中断，则返回已输出的部分。
     */
    public Optional<String> generateStream(String systemPrompt,
                                           String userPrompt,
                                           List<Map<String, String>> history,
                                           Consumer<String> onDelta) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        StringBuilder full = new StringBuilder();
        try {
            HttpRequest request = buildRequest(systemPrompt, userPrompt, history, true)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(buildRequestBody(systemPrompt, userPrompt, history, true)),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("LLM stream request failed, status={}", response.statusCode());
                return Optional.empty();
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> parseStreamLine(line).ifPresent(delta -> {
                    full.append(delta);
                    onDelta.accept(delta);
                }));
            }
        } catch (RuntimeException exception) {
            // onDelta 回调抛出的异常（如 SSE 通道已断开）继续向上传播，不静默吞掉
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("LLM stream request failed", exception);
        }
        return full.length() == 0 ? Optional.empty() : Optional.of(full.toString());
    }

    /**
     * 解析 OpenAI 兼容的 SSE 增量帧（"data: {...}"），提取 choices[0].delta.content。
     * 非增量帧（[DONE]、注释行、心跳、损坏 JSON）一律返回 empty。
     * 独立为静态方法以便单元测试。
     */
    public static Optional<String> parseStreamLine(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) {
            return Optional.empty();
        }
        String payload = trimmed.substring("data:".length()).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return Optional.empty();
        }
        try {
            JsonNode choices = STREAM_MAPPER.readTree(payload).path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return Optional.empty();
            }
            JsonNode delta = choices.get(0).path("delta").path("content");
            if (delta.isMissingNode() || delta.isNull()) {
                return Optional.empty();
            }
            String text = delta.asText();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt,
                                                 List<Map<String, String>> history, boolean stream) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model", properties.getOpenai().getChatModel());
        requestBody.put("temperature", properties.getOpenai().getTemperature());
        requestBody.put("max_tokens", properties.getOpenai().getMaxTokens());
        requestBody.put("top_p", properties.getOpenai().getTopP());
        requestBody.put("messages", buildMessages(systemPrompt, userPrompt, history));
        if (stream) {
            requestBody.put("stream", true);
        }
        return requestBody;
    }

    private List<Map<String, String>> buildMessages(String systemPrompt, String userPrompt,
                                                    List<Map<String, String>> history) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userPrompt));
        return messages;
    }

    private HttpRequest.Builder buildRequest(String systemPrompt, String userPrompt,
                                             List<Map<String, String>> history, boolean stream) {
        return HttpRequest.newBuilder()
                .uri(buildChatCompletionsUri())
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json");
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
