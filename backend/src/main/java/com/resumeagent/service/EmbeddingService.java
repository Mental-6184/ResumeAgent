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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Embedding 生成器：优先使用 OpenAI 兼容接口，未配置或失败时使用本地哈希向量。 */
@Service
public class EmbeddingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int FALLBACK_DIMENSION = 128;

    private final ResumeAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public EmbeddingService(ResumeAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 供单元测试和无 Spring 场景使用的本地实现。 */
    public EmbeddingService() {
        this(new ResumeAgentProperties(), new ObjectMapper());
    }

    public double[] embed(String text) {
        if (isRemoteConfigured()) {
            try {
                double[] remote = embedRemotely(text);
                if (remote.length > 0) {
                    return remote;
                }
            } catch (Exception exception) {
                LOGGER.warn("远程 Embedding 调用失败，降级为本地哈希向量: {}", exception.getMessage());
            }
        }
        return hashEmbed(text);
    }

    public double cosineSimilarity(double[] left, double[] right) {
        if (left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftMagnitude += left[index] * left[index];
            rightMagnitude += right[index] * right[index];
        }
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private double[] embedRemotely(String text) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "model", properties.getOpenai().getEmbeddingModel(),
                "input", text == null ? "" : text));
        HttpRequest request = HttpRequest.newBuilder(embeddingUri())
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + properties.getOpenai().getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        JsonNode embedding = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
        if (!embedding.isArray()) {
            throw new IllegalStateException("响应缺少 data[0].embedding");
        }
        double[] vector = new double[embedding.size()];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = embedding.get(index).asDouble();
        }
        return vector;
    }

    private URI embeddingUri() {
        String baseUrl = properties.getOpenai().getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/embeddings")) {
            return URI.create(baseUrl);
        }
        return URI.create(baseUrl + "/embeddings");
    }

    private boolean isRemoteConfigured() {
        var openai = properties.getOpenai();
        return notBlank(openai.getBaseUrl())
                && notBlank(openai.getApiKey())
                && notBlank(openai.getEmbeddingModel());
    }

    private double[] hashEmbed(String text) {
        double[] vector = new double[FALLBACK_DIMENSION];
        for (String token : tokenize(text)) {
            int bucket = Math.floorMod(token.hashCode(), FALLBACK_DIMENSION);
            vector[bucket] += 1.0;
        }
        normalize(vector);
        return vector;
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder asciiToken = new StringBuilder();
        StringBuilder chineseToken = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                if (isChineseCharacter(character)) {
                    flushAsciiToken(tokens, asciiToken);
                    chineseToken.append(character);
                } else {
                    flushChineseToken(tokens, chineseToken);
                    asciiToken.append(character);
                }
            } else {
                flushAsciiToken(tokens, asciiToken);
                flushChineseToken(tokens, chineseToken);
            }
        }
        flushAsciiToken(tokens, asciiToken);
        flushChineseToken(tokens, chineseToken);
        return tokens;
    }

    private boolean isChineseCharacter(char character) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(character);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    private void flushAsciiToken(List<String> tokens, StringBuilder builder) {
        if (builder.length() > 0) {
            tokens.add(builder.toString());
            builder.setLength(0);
        }
    }

    private void flushChineseToken(List<String> tokens, StringBuilder builder) {
        if (builder.length() == 0) {
            return;
        }
        String token = builder.toString();
        tokens.add(token);
        for (int index = 0; index < token.length(); index++) {
            tokens.add(String.valueOf(token.charAt(index)));
        }
        for (int index = 0; index < token.length() - 1; index++) {
            tokens.add(token.substring(index, index + 2));
        }
        builder.setLength(0);
    }

    private void normalize(double[] vector) {
        double magnitude = 0.0;
        for (double value : vector) {
            magnitude += value * value;
        }
        if (magnitude == 0.0) {
            return;
        }
        double sqrt = Math.sqrt(magnitude);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= sqrt;
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
