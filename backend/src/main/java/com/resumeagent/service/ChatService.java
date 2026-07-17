package com.resumeagent.service;

import com.resumeagent.dto.ChatRequest;
import com.resumeagent.dto.ChatResponse;
import com.resumeagent.dto.ConversationMessageResponse;
import com.resumeagent.dto.KnowledgeSearchItem;
import com.resumeagent.model.ConversationMessage;
import com.resumeagent.model.SafetyVerdict;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 问题类型枚举，用于驱动不同的检索策略和回答策略 */
    private enum QuestionType {
        SELF_INTRODUCTION,  // 自我介绍："介绍一下你自己"、"你是谁"
        PROJECT,            // 项目深挖："说说你的项目"、"项目中遇到的难点"
        TECHNICAL,          // 技术问题："你擅长什么技术"、"说说 Spring Boot"
        BEHAVIORAL,         // 行为问题："遇到过什么困难"、"如何团队协作"
        GREETING,           // 打招呼："你好"、"hello"
        GENERAL             // 通用问题
    }

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
            // RAG 模式：根据问题类型进行多轮检索，构建上下文
            QuestionType type = classifyQuestion(request.message());
            contexts = retrieveContext(request.message(), history, type);
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
     * 根据问题类型进行多轮 RAG 检索，合并去重后返回。
     * 不同类型的问题使用不同的搜索策略，提高召回率和相关性。
     */
    private List<KnowledgeSearchItem> retrieveContext(String message, List<ConversationMessage> history, QuestionType type) {
        List<KnowledgeSearchItem> allItems = new ArrayList<>();
        String contextualQuery = buildContextualQuery(message, history);

        switch (type) {
            case SELF_INTRODUCTION -> {
                // 自我介绍：先精确查找个人信息，再广泛搜索简历相关内容
                allItems.addAll(knowledgeBaseService.searchBySourceName("个人信息"));
                allItems.addAll(knowledgeBaseService.search(contextualQuery + " 简历 教育背景 技能 实习经历").items());
                allItems.addAll(knowledgeBaseService.search("项目 技术栈 亮点 成果").items());
            }
            case PROJECT -> {
                // 项目类：搜索项目详情、技术栈、实现难点
                allItems.addAll(knowledgeBaseService.search(contextualQuery + " 项目 技术栈 实现").items());
                allItems.addAll(knowledgeBaseService.search(contextualQuery + " 难点 解决方案 优化").items());
            }
            case TECHNICAL -> {
                // 技术类：搜索技术关键词 + 项目中的实际应用
                allItems.addAll(knowledgeBaseService.search(contextualQuery + " 项目 应用 实践").items());
                allItems.addAll(knowledgeBaseService.search(contextualQuery + " 技术栈 架构").items());
            }
            case BEHAVIORAL -> {
                // 行为类：搜索项目经历、挑战、解决方案
                allItems.addAll(knowledgeBaseService.search(contextualQuery + " 项目 挑战 解决").items());
                allItems.addAll(knowledgeBaseService.search(contextualQuery + " 团队 协作 成果").items());
            }
            default -> {
                // 通用问题：标准搜索
                allItems.addAll(knowledgeBaseService.search(contextualQuery).items());
            }
        }

        // 去重：按 content 去重，保留顺序
        return deduplicateItems(allItems);
    }

    /**
     * 问题类型分类。
     * 基于关键词匹配识别问题类型，用于驱动不同的检索和回答策略。
     */
    private QuestionType classifyQuestion(String msg) {
        String lower = msg.toLowerCase();

        // 自我介绍
        if (msg.contains("介绍自己") || msg.contains("自我介绍") || msg.contains("你是谁")
                || msg.contains("简单介绍") || msg.contains("介绍一下你") || msg.contains("做个介绍")
                || msg.contains("你叫什么") || msg.contains("你的名字") || msg.contains("关于你")) {
            return QuestionType.SELF_INTRODUCTION;
        }

        // 打招呼
        if (msg.matches("^(你好|hello|hi|hey|嗨|您好|hi there).{0,5}$")
                || msg.length() <= 6 && (msg.contains("你好") || msg.toLowerCase().contains("hello"))) {
            return QuestionType.GREETING;
        }

        // 项目类
        if (msg.contains("项目") || msg.contains("作品") || msg.contains("系统")
                || msg.contains("平台") || msg.contains("应用") || msg.contains("开发过")) {
            return QuestionType.PROJECT;
        }

        // 技术类
        if (msg.contains("技术") || msg.contains("栈") || msg.contains("框架")
                || msg.contains("擅长") || msg.contains("熟悉") || msg.contains("了解")
                || msg.contains("spring") || msg.contains("java") || msg.contains("vue")
                || msg.contains("数据库") || msg.contains("docker") || msg.contains("微服务")
                || msg.contains("算法") || msg.contains("设计模式") || msg.contains("架构")) {
            return QuestionType.TECHNICAL;
        }

        // 行为类
        if (msg.contains("困难") || msg.contains("挑战") || msg.contains("难点")
                || msg.contains("团队") || msg.contains("协作") || msg.contains("沟通")
                || msg.contains("失败") || msg.contains("成功") || msg.contains("收获")
                || msg.contains("成长") || msg.contains("印象最深") || msg.contains("最自豪")) {
            return QuestionType.BEHAVIORAL;
        }

        return QuestionType.GENERAL;
    }

    /**
     * 知识库结果去重，按 content 文本去重，保留首次出现的顺序。
     */
    private List<KnowledgeSearchItem> deduplicateItems(List<KnowledgeSearchItem> items) {
        Set<String> seen = new LinkedHashSet<>();
        List<KnowledgeSearchItem> result = new ArrayList<>();
        for (KnowledgeSearchItem item : items) {
            // 用 content 前 100 字符作为去重 key，避免完全相同的内容重复
            String key = item.content().length() > 100 ? item.content().substring(0, 100) : item.content();
            if (seen.add(key)) {
                result.add(item);
            }
        }
        // 限制总量，避免上下文过长
        return result.size() > 10 ? result.subList(0, 10) : result;
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

