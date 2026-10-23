package com.resumeagent.controller;

import com.resumeagent.dto.TechGuideRequest;
import jakarta.validation.Valid;
import com.resumeagent.service.TechGuideService;
import com.resumeagent.service.StreamMetrics;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/techguide")
public class TechGuideController {

    private final TechGuideService techGuideService;
    private final StreamMetrics streamMetrics;

    public TechGuideController(TechGuideService techGuideService, StreamMetrics streamMetrics) {
        this.techGuideService = techGuideService;
        this.streamMetrics = streamMetrics;
    }

    /**
     * SSE 流式教学端点。
     * 前端通过 POST JSON 请求，接收 text/event-stream 教学内容。
     *
     * @param module         模块ID（java/spring/database/rag/agent/security/resume-agent/fundpilot）
     * @param tag            知识点标签（可选）
     * @param message        用户消息
     * @param conversationId 对话ID（用于维护上下文）
     */
    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody TechGuideRequest request) {
        if (!streamMetrics.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "流式请求过多，请稍后重试");
        }
        try {
            SseEmitter emitter = techGuideService.stream(
                    request.module(), request.tag(), request.message(), request.conversationId());
            emitter.onCompletion(streamMetrics::release);
            emitter.onTimeout(streamMetrics::release);
            emitter.onError(error -> streamMetrics.release());
            return emitter;
        } catch (RuntimeException exception) {
            streamMetrics.release();
            throw exception;
        }
    }
}
