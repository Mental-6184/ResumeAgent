package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * LLM 流式 SSE 帧解析单测（纯解析，无网络调用）。
 */
class LlmServiceStreamParseTest {

    @Test
    void shouldExtractDeltaContentFromSseFrame() {
        Optional<String> delta = LlmService.parseStreamLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}");

        assertThat(delta).contains("你好");
    }

    @Test
    void shouldExtractMultiByteDeltaContent() {
        Optional<String> delta = LlmService.parseStreamLine(
                "data: {\"id\":\"x\",\"choices\":[{\"delta\":{\"content\":\"Spring Boot\"}}]}");

        assertThat(delta).contains("Spring Boot");
    }

    @Test
    void shouldIgnoreNonDeltaFrames() {
        assertThat(LlmService.parseStreamLine("data: [DONE]")).isEmpty();
        assertThat(LlmService.parseStreamLine("data:")).isEmpty();
        assertThat(LlmService.parseStreamLine("data: ")).isEmpty();
        assertThat(LlmService.parseStreamLine(": keep-alive")).isEmpty();
        assertThat(LlmService.parseStreamLine("event: message")).isEmpty();
        assertThat(LlmService.parseStreamLine("")).isEmpty();
        assertThat(LlmService.parseStreamLine(null)).isEmpty();
    }

    @Test
    void shouldIgnoreMalformedOrIncompletePayloads() {
        assertThat(LlmService.parseStreamLine("data: {broken json")).isEmpty();
        assertThat(LlmService.parseStreamLine("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}")).isEmpty();
        assertThat(LlmService.parseStreamLine("data: {\"choices\":[]}")).isEmpty();
        assertThat(LlmService.parseStreamLine("data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}")).isEmpty();
    }
}
