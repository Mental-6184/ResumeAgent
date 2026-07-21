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
import java.util.Optional;
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

    /** 对话历史上限：超过此数量时只保留最近 N 条，避免 LLM 上下文溢出 */
    private static final int MAX_HISTORY_MESSAGES = 20;

    /** 模拟面试回答最低字数要求（字符数，不是字数） */
    private static final int MIN_ANSWER_LENGTH = 200;

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
            QuestionType type = classifyQuestion(request.message(), history);
            contexts = retrieveContext(request.message(), history, type);
            systemPrompt = promptBuilder.buildDynamicSystemPrompt(contexts, history.size());
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

            // 模拟面试模式下，检查回答长度，如果太短则重新请求
            if (isRagMode && reply.length() < MIN_ANSWER_LENGTH) {
                String retryPrompt = userPrompt + "\n\n[系统提示：你的回答太短了，必须达到400字以上。请重新回答，展开更多细节。]";
                Optional<String> retryReply = llmService.generate(systemPrompt, retryPrompt, historyMessages);
                if (retryReply.isPresent() && retryReply.get().length() >= reply.length()) {
                    reply = retryReply.get();
                }
            }
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
        List<ConversationMessage> all = conversationStore.getOrDefault(conversationId, Collections.emptyList());
        if (all.size() <= MAX_HISTORY_MESSAGES) {
            return all;
        }
        // 滑动窗口：丢弃最早的消息，保留最近的 MAX_HISTORY_MESSAGES 条
        return all.subList(all.size() - MAX_HISTORY_MESSAGES, all.size());
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
                // 项目类：优先检索重点项目，再补充其他项目
                // 1. 优先检索简历中的项目概览（含所有项目的结构化摘要）
                allItems.addAll(knowledgeBaseService.searchBySourceName("个人信息"));
                // 2. 补充检索 projects 目录下的详细项目文档
                allItems.addAll(knowledgeBaseService.searchBySourceName("FundPilot-AI"));
                allItems.addAll(knowledgeBaseService.searchBySourceName("ResumeAgent"));
                // 3. 补充检索其他项目（技术栈、实现难点）
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
     * 支持追问上下文继承：当检测到追问时，继承上一轮的问题类型。
     */
    private QuestionType classifyQuestion(String msg, List<ConversationMessage> history) {
        // 1. 检测追问：短消息 + 有历史 + 包含指代词或追问模式 → 继承上一轮类型
        if (!history.isEmpty() && isFollowUp(msg)) {
            QuestionType previousType = findPreviousQuestionType(history);
            if (previousType != null) {
                return previousType;
            }
        }

        // 2. 基于关键词的常规分类
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
                || msg.contains("平台") || msg.contains("应用") || msg.contains("开发过")
                || msg.contains("agent") || msg.contains("rag") || msg.contains("智能体")
                || msg.contains("基金") || msg.contains("漏洞") || msg.contains("扫描")
                || msg.contains("车库") || msg.contains("车辆") || msg.contains("博客")
                || msg.contains("resume") || msg.contains("fundpilot") || msg.contains("sec-sonar")) {
            return QuestionType.PROJECT;
        }

        // 技术类
        if (msg.contains("技术") || msg.contains("栈") || msg.contains("框架")
                || msg.contains("擅长") || msg.contains("熟悉") || msg.contains("了解")
                || msg.contains("spring") || msg.contains("java") || msg.contains("vue")
                || msg.contains("数据库") || msg.contains("docker") || msg.contains("微服务")
                || msg.contains("算法") || msg.contains("设计模式") || msg.contains("架构")
                || msg.contains("redis") || msg.contains("mysql") || msg.contains("kafka")
                || msg.contains("llm") || msg.contains("大模型") || msg.contains("prompt")
                || msg.contains("embedding") || msg.contains("向量") || msg.contains("检索增强")
                || msg.contains("缓存") || msg.contains("索引") || msg.contains("事务")
                || msg.contains("并发") || msg.contains("线程") || msg.contains("jvm")) {
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
     * 检测是否为追问。
     * 追问特征：消息较短、包含指代词或追问模式、且有对话历史。
     */
    private boolean isFollowUp(String msg) {
        // 指代词
        if (msg.contains("这个") || msg.contains("那个") || msg.contains("它")
                || msg.contains("其") || msg.contains("该项目") || msg.contains("该系统")
                || msg.contains("上述") || msg.contains("之前") || msg.contains("刚才")
                || msg.contains("你说的") || msg.contains("你提到的") || msg.contains("上面的")) {
            return true;
        }
        // 追问模式：短消息（<=15字）且以常见追问词开头
        if (msg.length() <= 15) {
            String trimmed = msg.trim();
            if (trimmed.startsWith("那") || trimmed.startsWith("怎么") || trimmed.startsWith("为什么")
                    || trimmed.startsWith("然后呢") || trimmed.startsWith("还有呢") || trimmed.startsWith("继续")
                    || trimmed.startsWith("具体") || trimmed.startsWith("详细") || trimmed.startsWith("展开")
                    || trimmed.startsWith("能不") || trimmed.startsWith("有没有")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从对话历史中找到上一轮的问题类型。
     * 通过解析系统提示中的检索上下文来推断。
     */
    private QuestionType findPreviousQuestionType(List<ConversationMessage> history) {
        // 从最近的用户消息推断上一轮类型
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) {
                String lastUserMsg = history.get(i).content();
                // 递归分类（不传 history，避免无限递归）
                QuestionType inferred = classifyQuestionInternal(lastUserMsg);
                if (inferred != QuestionType.GENERAL && inferred != QuestionType.GREETING) {
                    return inferred;
                }
            }
        }
        return null;
    }

    /**
     * 纯关键词分类（不含追问检测），供内部调用避免递归。
     */
    private QuestionType classifyQuestionInternal(String msg) {
        if (msg.contains("介绍自己") || msg.contains("自我介绍") || msg.contains("你是谁")) {
            return QuestionType.SELF_INTRODUCTION;
        }
        if (msg.contains("项目") || msg.contains("作品") || msg.contains("系统") || msg.contains("agent")
                || msg.contains("rag") || msg.contains("基金") || msg.contains("车库")) {
            return QuestionType.PROJECT;
        }
        if (msg.contains("技术") || msg.contains("框架") || msg.contains("spring") || msg.contains("java")
                || msg.contains("数据库") || msg.contains("docker") || msg.contains("redis")) {
            return QuestionType.TECHNICAL;
        }
        if (msg.contains("困难") || msg.contains("挑战") || msg.contains("团队") || msg.contains("协作")) {
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
     * 策略：从最近对话中提取项目名和技术关键词，拼接到当前 query 中。
     */
    private String buildContextualQuery(String currentMessage, List<ConversationMessage> history) {
        if (history.isEmpty()) {
            return currentMessage;
        }

        // 从最近 4 条消息中提取关键词
        int start = Math.max(0, history.size() - 4);
        List<String> keywords = new ArrayList<>();
        for (int i = start; i < history.size(); i++) {
            String content = history.get(i).content();
            keywords.addAll(extractKeywords(content));
        }

        if (keywords.isEmpty()) {
            return currentMessage;
        }

        // 去重并限制数量，避免噪音
        List<String> unique = keywords.stream().distinct().limit(6).toList();
        return currentMessage + " " + String.join(" ", unique);
    }

    /**
     * 从文本中提取有价值的检索关键词（项目名、技术栈、核心概念）。
     * 过滤掉虚词和低信息量词汇。
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        // 已知的高价值关键词（项目名 + 技术栈）
        String[] knownTerms = {
                "ResumeAgent", "FundPilot", "Sec-Sonar", "GazeForge", "MentalBlog",
                "Spring Boot", "Spring Cloud", "Spring AI", "MyBatis-Plus",
                "Vue", "Redis", "MySQL", "Kafka", "Docker", "Nginx", "Nmap",
                "RAG", "Agent", "Tool Calling", "SSE", "Prompt",
                "交易系统", "支付网关", "车库管理", "漏洞扫描", "基金分析",
                "面试", "岗位匹配", "技能分析", "知识库", "向量"
        };
        String lower = text.toLowerCase();
        for (String term : knownTerms) {
            if (lower.contains(term.toLowerCase())) {
                keywords.add(term);
            }
        }
        return keywords;
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

