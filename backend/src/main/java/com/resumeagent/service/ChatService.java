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
        final boolean isRagMode = !"llm".equals(request.mode());

        final List<KnowledgeSearchItem> contexts;
        String systemPrompt;
        String userPrompt;

        if (isRagMode) {
            // RAG 模式：检索知识库，动态构建系统提示词
            String searchQuery = buildEnhancedQuery(request.message(), history);
            contexts = knowledgeBaseService.search(searchQuery).items();
            systemPrompt = promptBuilder.buildDynamicSystemPrompt(contexts);
            userPrompt = promptBuilder.buildChatUserPrompt(request.message(), history, contexts);
        } else {
            // 纯 LLM 模式：直接对话，不检索知识库
            contexts = List.of();
            systemPrompt = promptBuilder.buildLlmSystemPrompt();
            userPrompt = request.message();
        }

        String reply;
        if (isRagMode && contexts.isEmpty()) {
            // 知识库无相关内容时，直接返回 fallback，不调用 LLM（避免幻觉）
            reply = promptBuilder.buildFallbackChatAnswer(request.message(), contexts);
        } else {
            // 将对话历史转为 messages 格式传给 LLM
            List<Map<String, String>> historyMessages = history.stream()
                    .map(m -> Map.of("role", m.role(), "content", m.content()))
                    .toList();

            reply = llmService.generate(systemPrompt, userPrompt, historyMessages)
                    .orElseGet(() -> isRagMode
                            ? promptBuilder.buildFallbackChatAnswer(request.message(), contexts)
                            : "抱歉，LLM 服务未配置。请在 application.yml 中配置 resumeagent.openai 相关参数。");
        }

        appendMessage(request.conversationId(), "user", verdict.sanitizedText());
        appendMessage(request.conversationId(), "assistant", reply);
        auditService.record(isRagMode ? "chat" : "chat-llm", request.conversationId(), verdict.sanitizedText(), reply, verdict.reason());
        return new ChatResponse(request.conversationId(), reply, isRagMode ? buildCitations(contexts) : List.of());
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
     * 构建增强的搜索查询，处理特殊场景（自我介绍、项目列举等）。
     */
    private String buildEnhancedQuery(String currentMessage, List<ConversationMessage> history) {
        String query = buildContextualQuery(currentMessage, history);

        // 自我介绍类问题：扩展搜索词以获取完整简历信息
        if (isSelfIntroductionQuery(currentMessage)) {
            return query + " 个人信息 简历 教育背景 技能 项目经历 实习";
        }

        // 项目列举类问题：扩展搜索词
        if (isProjectListingQuery(currentMessage)) {
            return query + " 项目 FundPilot Sec-Sonar 车库管理";
        }

        return query;
    }

    /** 检测是否为自我介绍类问题 */
    private boolean isSelfIntroductionQuery(String msg) {
        return msg.contains("介绍自己") || msg.contains("自我介绍") || msg.contains("你是谁")
                || msg.contains("简单介绍") || msg.contains("介绍一下你") || msg.contains("做个介绍");
    }

    /** 检测是否为项目列举类问题 */
    private boolean isProjectListingQuery(String msg) {
        return (msg.contains("项目") || msg.contains("经历"))
                && (msg.contains("哪些") || msg.contains("什么") || msg.contains("做过") || msg.contains("有"));
    }

    /**
     * 基于对话历史扩展检索 query，解决"这个项目"、"它"等指代词导致的检索不精准问题。
     * 当检测到指代词时，将最近对话中的关键信息拼接到检索 query 中。
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

        // 从最近几轮对话中提取关键词，帮助搜索引擎定位上下文
        int start = Math.max(0, history.size() - 4);
        List<String> keywords = new ArrayList<>();
        for (int i = start; i < history.size(); i++) {
            String content = history.get(i).content();
            // 提取英文单词和中文词汇作为关键词
            for (String word : content.split("[，。？！、；：\\s]+")) {
                String trimmed = word.trim();
                if (trimmed.length() >= 2 && trimmed.length() <= 20) {
                    keywords.add(trimmed);
                }
            }
        }

        if (keywords.isEmpty()) {
            return currentMessage;
        }

        // 取最近的关键词拼接到 query 中（限制数量避免噪音）
        List<String> recentKeywords = keywords.subList(Math.max(0, keywords.size() - 8), keywords.size());
        return currentMessage + " " + String.join(" ", recentKeywords);
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

