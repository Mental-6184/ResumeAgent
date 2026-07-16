package com.resumeagent.service;

import com.resumeagent.dto.ChatRequest;
import com.resumeagent.dto.ChatResponse;
import com.resumeagent.dto.ConversationMessageResponse;
import com.resumeagent.dto.KnowledgeSearchItem;
import com.resumeagent.model.ConversationMessage;
import com.resumeagent.model.SafetyVerdict;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final SafetyService safetyService;
    private final PromptBuilder promptBuilder;
    private final LlmService llmService;
    private final AuditService auditService;
    private final Map<String, List<ConversationMessage>> conversationStore = new ConcurrentHashMap<>();

    public ChatService(KnowledgeBaseService knowledgeBaseService,
                       SafetyService safetyService,
                       PromptBuilder promptBuilder,
                       LlmService llmService,
                       AuditService auditService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.safetyService = safetyService;
        this.promptBuilder = promptBuilder;
        this.llmService = llmService;
        this.auditService = auditService;
    }

    public ChatResponse chat(ChatRequest request) {
        SafetyVerdict verdict = safetyService.inspect(request.message());
        if (!verdict.allowed()) {
            auditService.record("chat-blocked", request.conversationId(), verdict.sanitizedText(), verdict.reason(), "injection");
            appendMessage(request.conversationId(), "user", verdict.sanitizedText());
            appendMessage(request.conversationId(), "assistant", verdict.reason());
            return new ChatResponse(request.conversationId(), verdict.reason(), List.of());
        }

        List<ConversationMessage> history = historyMessages(request.conversationId());
        // 用对话历史扩展检索 query，解决"这个项目"等指代问题
        String searchQuery = buildContextualQuery(request.message(), history);
        List<KnowledgeSearchItem> contexts = knowledgeBaseService.search(searchQuery).items();
        String systemPrompt = promptBuilder.buildChatSystemPrompt();
        String userPrompt = promptBuilder.buildChatUserPrompt(request.message(), history, contexts);

        // 将对话历史转为 messages 格式传给 LLM
        List<Map<String, String>> historyMessages = history.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList();

        String reply = llmService.generate(systemPrompt, userPrompt, historyMessages)
                .orElseGet(() -> promptBuilder.buildFallbackChatAnswer(request.message(), contexts));

        appendMessage(request.conversationId(), "user", verdict.sanitizedText());
        appendMessage(request.conversationId(), "assistant", reply);
        auditService.record("chat", request.conversationId(), verdict.sanitizedText(), reply, verdict.reason());
        return new ChatResponse(request.conversationId(), reply, buildCitations(contexts));
    }

    public SseEmitter stream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                ChatResponse response = chat(request);
                for (String chunk : splitIntoChunks(response.reply())) {
                    emitter.send(chunk);
                    Thread.sleep(80L);
                }
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    public List<ConversationMessageResponse> history(String conversationId) {
        List<ConversationMessage> messages = conversationStore.getOrDefault(conversationId, Collections.emptyList());
        return messages.stream()
                .map(message -> new ConversationMessageResponse(message.role(), message.content()))
                .toList();
    }

    private List<String> buildCitations(List<KnowledgeSearchItem> contexts) {
        return contexts.stream()
                .limit(3)
                .map(item -> "[" + item.sourceName() + "] " + item.content())
                .toList();
    }

    private List<ConversationMessage> historyMessages(String conversationId) {
        return conversationStore.getOrDefault(conversationId, Collections.emptyList());
    }

    private void appendMessage(String conversationId, String role, String content) {
        conversationStore.computeIfAbsent(conversationId, key -> new CopyOnWriteArrayList<>())
                .add(new ConversationMessage(role, content));
    }

    /**
     * 基于对话历史扩展检索 query，解决"这个项目"、"它"等指代词导致的检索不精准问题。
     * 从最近的对话中提取项目名和关键词，拼接到当前 query 中。
     */
    private String buildContextualQuery(String currentMessage, List<ConversationMessage> history) {
        if (history.isEmpty()) {
            return currentMessage;
        }

        // 检查当前消息是否包含指代词
        boolean hasReference = currentMessage.contains("这个") || currentMessage.contains("那个")
                || currentMessage.contains("它") || currentMessage.contains("其")
                || currentMessage.contains("该项目") || currentMessage.contains("该系统")
                || currentMessage.contains("上述") || currentMessage.contains("之前")
                || currentMessage.contains("刚才") || currentMessage.contains("那个项目")
                || currentMessage.contains("做这个") || currentMessage.contains("这个系统");

        if (!hasReference) {
            return currentMessage;
        }

        // 从最近 4 轮对话中提取关键词
        int start = Math.max(0, history.size() - 4);
        List<String> recentTexts = new ArrayList<>();
        for (int i = start; i < history.size(); i++) {
            recentTexts.add(history.get(i).content());
        }
        String recentContext = String.join(" ", recentTexts);

        // 提取项目名关键词
        String[] projectNames = {"FundPilot", "MentalBlog", "Sec-Sonar", "SecSonar", "GazeForge",
                "ResumeAgent", "车库管理", "3D粒子", "Particle", "ShotNote", "PocketLedger",
                "PUBG", "图书管理", "博客", "基金", "安全态势", "截图标注", "记账", "粒子系统"};
        List<String> foundProjects = new ArrayList<>();
        for (String name : projectNames) {
            if (recentContext.contains(name)) {
                foundProjects.add(name);
            }
        }

        if (foundProjects.isEmpty()) {
            return currentMessage;
        }

        // 拼接：当前消息 + 提取到的项目名
        return currentMessage + " " + String.join(" ", foundProjects);
    }

    private List<String> splitIntoChunks(String reply) {
        List<String> chunks = new ArrayList<>();
        String[] lines = reply.split("\n");
        for (String line : lines) {
            if (!line.isBlank()) {
                chunks.add(line);
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(reply);
        }
        return chunks;
    }
}

