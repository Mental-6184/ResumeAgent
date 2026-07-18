package com.resumeagent.controller;

import com.resumeagent.dto.ChatRequest;
import com.resumeagent.dto.ChatResponse;
import com.resumeagent.dto.ConversationMessageResponse;
import com.resumeagent.service.ChatService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String message,
                             @RequestParam(defaultValue = "default") String conversationId,
                             @RequestParam(defaultValue = "rag") String mode) {
        return chatService.stream(new ChatRequest(conversationId, message, mode));
    }

    @GetMapping("/history")
    public List<ConversationMessageResponse> history(
            @RequestParam(defaultValue = "default") String conversationId) {
        return chatService.history(conversationId);
    }
}

