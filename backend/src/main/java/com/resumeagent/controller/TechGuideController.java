package com.resumeagent.controller;

import com.resumeagent.service.TechGuideService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/techguide")
public class TechGuideController {

    private final TechGuideService techGuideService;

    public TechGuideController(TechGuideService techGuideService) {
        this.techGuideService = techGuideService;
    }

    /**
     * SSE 流式教学端点。
     * 前端通过 EventSource GET 请求，接收逐行流式教学内容。
     *
     * @param module         模块ID（java/spring/database/rag/agent/security/resume-agent/fundpilot）
     * @param tag            知识点标签（可选）
     * @param message        用户消息
     * @param conversationId 对话ID（用于维护上下文）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String module,
                             @RequestParam(defaultValue = "") String tag,
                             @RequestParam String message,
                             @RequestParam(defaultValue = "default") String conversationId) {
        return techGuideService.stream(module, tag, message, conversationId);
    }
}
