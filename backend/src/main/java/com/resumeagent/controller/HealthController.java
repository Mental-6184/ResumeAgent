package com.resumeagent.controller;

import com.resumeagent.service.ChatService;
import com.resumeagent.service.KnowledgeBaseService;
import com.resumeagent.service.LlmService;
import com.resumeagent.service.StreamMetrics;
import com.resumeagent.service.TechGuideService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class HealthController {

    private final LlmService llmService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatService chatService;
    private final StreamMetrics streamMetrics;
    private final TechGuideService techGuideService;

    public HealthController(LlmService llmService, KnowledgeBaseService knowledgeBaseService,
                            ChatService chatService, StreamMetrics streamMetrics,
                            TechGuideService techGuideService) {
        this.llmService = llmService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatService = chatService;
        this.streamMetrics = streamMetrics;
        this.techGuideService = techGuideService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "llmConfigured", llmService.isAvailable(),
                "knowledgeChunks", knowledgeBaseService.chunkCount(),
                "activeConversations", chatService.activeConversationCount()
                        + techGuideService.activeConversationCount(),
                "activeStreams", streamMetrics.activeCount()
        );
    }
}
