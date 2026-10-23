package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.resumeagent.config.ResumeAgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EmbeddingServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldUseConfiguredOpenAiCompatibleEmbeddingEndpoint() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/embeddings", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        ResumeAgentProperties properties = new ResumeAgentProperties();
        properties.getOpenai().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setEmbeddingModel("test-embedding");

        double[] vector = new EmbeddingService(properties, new ObjectMapper()).embed("Java 后端");

        assertThat(vector).containsExactly(0.1, 0.2, 0.3);
        var request = new ObjectMapper().readTree(requestBody.get());
        assertThat(request.path("input").asText()).isEqualTo("Java 后端");
        assertThat(request.path("model").asText()).isEqualTo("test-embedding");
    }

    @Test
    void shouldKeepLocal128DimensionFallbackWhenRemoteEmbeddingIsNotConfigured() {
        double[] vector = new EmbeddingService().embed("Java 后端");

        assertThat(vector).hasSize(128);
    }
}
