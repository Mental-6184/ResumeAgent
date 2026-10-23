package com.resumeagent.controller;

import com.resumeagent.dto.ChatRequest;
import com.resumeagent.dto.ChatResponse;
import com.resumeagent.dto.ConversationMessageResponse;
import com.resumeagent.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import com.resumeagent.service.StreamMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final StreamMetrics streamMetrics;

    public ChatController(ChatService chatService, StreamMetrics streamMetrics) {
        this.chatService = chatService;
        this.streamMetrics = streamMetrics;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        if (!streamMetrics.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "流式请求过多，请稍后重试");
        }
        try {
            SseEmitter emitter = chatService.stream(request);
            emitter.onCompletion(streamMetrics::release);
            emitter.onTimeout(streamMetrics::release);
            emitter.onError(error -> streamMetrics.release());
            return emitter;
        } catch (RuntimeException exception) {
            streamMetrics.release();
            throw exception;
        }
    }

    @GetMapping("/history")
    public List<ConversationMessageResponse> history(
            @RequestParam(defaultValue = "default") String conversationId) {
        return chatService.history(conversationId);
    }
}

