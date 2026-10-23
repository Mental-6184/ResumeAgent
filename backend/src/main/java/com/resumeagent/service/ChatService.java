package com.resumeagent.service;

import com.resumeagent.config.ResumeAgentProperties;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final SafetyService safetyService;
    private final PromptBuilder promptBuilder;
    private final LlmService llmService;
    private final AuditService auditService;
    private final ProfileService profileService;
    private final ConversationStore conversationStore;
    /** conversationId → 对话启用时的档案代数（档案切换后旧历史作废，避免旧身份污染回答） */
    private final Map<String, Long> conversationProfileGeneration = new ConcurrentHashMap<>();
    private final ResumeAgentProperties properties;
    private final Executor streamExecutor;

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
                       AuditService auditService,
                       ProfileService profileService) {
        this(knowledgeBaseService, safetyService, promptBuilder, llmService, auditService,
                profileService, new ResumeAgentProperties(), new ConversationStore(new ResumeAgentProperties()), Runnable::run);
    }

    @Autowired
    public ChatService(KnowledgeBaseService knowledgeBaseService,
                       SafetyService safetyService,
                       PromptBuilder promptBuilder,
                       LlmService llmService,
                       AuditService auditService,
                       ProfileService profileService,
                       ResumeAgentProperties properties,
                       ConversationStore conversationStore,
                       @Qualifier("streamTaskExecutor") Executor streamExecutor) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.safetyService = safetyService;
        this.promptBuilder = promptBuilder;
        this.llmService = llmService;
        this.auditService = auditService;
        this.profileService = profileService;
        this.properties = properties;
        this.conversationStore = conversationStore;
        this.streamExecutor = streamExecutor;
    }

    public ChatResponse chat(ChatRequest request) {
        Prepared prepared = prepare(request);
        if (!prepared.verdict().allowed()) {
            handleBlocked(request, prepared.verdict());
            return new ChatResponse(request.conversationId(), prepared.verdict().reason(), List.of());
        }

        String reply;
        if (prepared.ragMode() && (prepared.contexts().isEmpty() || isGreetingWithoutProfile(prepared))) {
            // 无知识命中 / 无档案的问候：不调用 LLM，直接确定性回复（避免幻觉）
            reply = safeReply(request, prepared);
        } else {
            reply = llmService.generate(prepared.systemPrompt(), prepared.userPrompt(), prepared.historyMessages())
                    .orElseGet(() -> prepared.ragMode()
                            ? promptBuilder.buildFallbackChatAnswer(request.message(), prepared.contexts())
                            : "抱歉，LLM 服务未配置。请在 application.yml 中配置 resumeagent.openai 相关参数。");

            // 模拟面试模式下，检查回答长度，如果太短则重新请求
            // （仅非流式路径：流式输出后内容已发给用户，无法重试）
            if (prepared.ragMode() && reply.length() < MIN_ANSWER_LENGTH) {
                String retryPrompt = prepared.userPrompt() + "\n\n[系统提示：你的回答太短了，必须达到400字以上。请重新回答，展开更多细节。]";
                Optional<String> retryReply = llmService.generate(prepared.systemPrompt(), retryPrompt, prepared.historyMessages());
                if (retryReply.isPresent() && retryReply.get().length() >= reply.length()) {
                    reply = retryReply.get();
                }
            }
        }

        finish(request, prepared, reply);
        return new ChatResponse(request.conversationId(), reply, prepared.ragMode() ? buildCitations(prepared.contexts()) : List.of());
    }

    public SseEmitter stream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(properties.getStream().getTimeoutMs());
        AtomicReference<Future<?>> taskRef = new AtomicReference<>();
        Runnable cancelTask = () -> {
            Future<?> task = taskRef.get();
            if (task != null) {
                task.cancel(true);
            }
        };
        emitter.onCompletion(cancelTask);
        emitter.onTimeout(() -> {
            cancelTask.run();
            emitter.complete();
        });
        emitter.onError(error -> cancelTask.run());
        Future<?> task = CompletableFuture.runAsync(() -> {
            try {
                streamChat(request,
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (Exception exception) {
                                throw new CompletionException(exception);
                            }
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (Exception exception) {
                                throw new CompletionException(exception);
                            }
                        });
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        }, streamExecutor);
        taskRef.set(task);
        return emitter;
    }

    /**
     * 真流式问答：LLM 增量 delta 实时转发（不再是"整段生成完再假流式"）。
     * 输出与完成信号以回调注入，便于单元测试。
     */
    public void streamChat(ChatRequest request, Consumer<String> send, Runnable onDone) {
        Prepared prepared = prepare(request);
        if (!prepared.verdict().allowed()) {
            handleBlocked(request, prepared.verdict());
            send.accept(prepared.verdict().reason());
            onDone.run();
            return;
        }

        if (prepared.ragMode() && (prepared.contexts().isEmpty() || isGreetingWithoutProfile(prepared))) {
            // 无知识命中 / 无档案的问候：不调用 LLM，直接推确定性回复（防幻觉）
            String safe = safeReply(request, prepared);
            send.accept(safe);
            finish(request, prepared, safe);
            onDone.run();
            return;
        }

        StringBuilder streamedReply = new StringBuilder();
        Optional<String> generated = llmService.generateStream(
                prepared.systemPrompt(), prepared.userPrompt(), prepared.historyMessages(),
                delta -> {
                    streamedReply.append(delta);
                    send.accept(delta);
                });

        String reply;
        if (generated.isPresent()) {
            reply = generated.get();
        } else {
            // LLM 不可用：补发降级回答
            reply = prepared.ragMode()
                    ? promptBuilder.buildFallbackChatAnswer(request.message(), prepared.contexts())
                    : "抱歉，LLM 服务未配置。请在 application.yml 中配置 resumeagent.openai 相关参数。";
            send.accept(reply);
        }

        finish(request, prepared, reply);
        onDone.run();
    }

    /**
     * 安全检查 + 问题分类 + 多路检索 + 提示词构建。
     * 流式与非流式路径共用，保证两条路径行为一致。
     */
    private Prepared prepare(ChatRequest request) {
        SafetyVerdict verdict = safetyService.inspect(request.message());
        if (!verdict.allowed()) {
            return new Prepared(verdict, false, false, List.of(), "", "", List.of());
        }

        List<ConversationMessage> history = historyMessages(request.conversationId());
        final boolean isRagMode = !"llm".equals(request.mode());

        final List<KnowledgeSearchItem> contexts;
        String systemPrompt;
        String userPrompt;
        boolean greeting = false;

        if (isRagMode) {
            // RAG 模式：根据问题类型进行多轮检索，构建上下文
            QuestionType type = classifyQuestion(request.message(), history);
            greeting = type == QuestionType.GREETING;
            contexts = retrieveContext(request.message(), history, type);
            systemPrompt = promptBuilder.buildDynamicSystemPrompt(contexts, history.size());
            userPrompt = promptBuilder.buildChatUserPrompt(request.message(), history, contexts);
        } else {
            // 纯 LLM 模式：直接对话，不检索知识库
            contexts = List.of();
            systemPrompt = promptBuilder.buildLlmSystemPrompt();
            userPrompt = request.message();
        }

        if (request.jobTitle() != null && !request.jobTitle().isBlank()) {
            userPrompt = "目标岗位：" + request.jobTitle().trim() + "\n\n" + userPrompt;
        }

        List<Map<String, String>> historyMessages = history.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList();
        return new Prepared(verdict, isRagMode, greeting, contexts, systemPrompt, userPrompt, historyMessages);
    }

    /** 问候类问题且没有用户档案：走确定性简短问候，不给 LLM 编造自我介绍的机会 */
    private boolean isGreetingWithoutProfile(Prepared prepared) {
        return prepared.ragMode() && prepared.greeting() && prepared.contexts().stream()
                .noneMatch(item -> KnowledgeBaseService.PROFILE_SOURCE_NAME.equals(item.sourceName()));
    }

    /** 无需调用 LLM 的安全回复（无知识/无档案的问候场景） */
    private String safeReply(ChatRequest request, Prepared prepared) {
        return isGreetingWithoutProfile(prepared)
                ? promptBuilder.buildGreetingAnswer()
                : promptBuilder.buildFallbackChatAnswer(request.message(), prepared.contexts());
    }

    private void handleBlocked(ChatRequest request, SafetyVerdict verdict) {
        auditService.record("chat-blocked", request.conversationId(), verdict.sanitizedText(), verdict.reason(), "injection");
        appendMessage(request.conversationId(), "user", verdict.sanitizedText());
        appendMessage(request.conversationId(), "assistant", verdict.reason());
    }

    private void finish(ChatRequest request, Prepared prepared, String reply) {
        appendMessage(request.conversationId(), "user", prepared.verdict().sanitizedText());
        appendMessage(request.conversationId(), "assistant", reply);
        auditService.record(prepared.ragMode() ? "chat" : "chat-llm",
                request.conversationId(), prepared.verdict().sanitizedText(), reply, prepared.verdict().reason());
    }

    /** 一次问答的全部预处理结果 */
    private record Prepared(SafetyVerdict verdict,
                            boolean ragMode,
                            boolean greeting,
                            List<KnowledgeSearchItem> contexts,
                            String systemPrompt,
                            String userPrompt,
                            List<Map<String, String>> historyMessages) {
    }

    public List<ConversationMessageResponse> history(String conversationId) {
        return conversationStore.history("chat", conversationId).stream()
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
        // 档案切换检测：旧历史携带旧身份会污染新档案的回答，代数变化时清空该对话的历史
        long currentGeneration = profileService.generation();
        Long recorded = conversationProfileGeneration.putIfAbsent(conversationId, currentGeneration);
        if (recorded != null && recorded.longValue() != currentGeneration) {
            conversationStore.clear("chat", conversationId);
            conversationProfileGeneration.remove(conversationId);
            conversationProfileGeneration.put(conversationId, currentGeneration);
            return List.of();
        }

        return conversationStore.history("chat", conversationId);
    }

    private void appendMessage(String conversationId, String role, String content) {
        conversationStore.append("chat", conversationId, role, content);
    }

    public int activeConversationCount() {
        return conversationStore.activeCount();
    }

    /**
     * 根据问题类型进行多轮 RAG 检索，合并去重后返回。
     * 不同类型的问题使用不同的搜索策略，提高召回率和相关性。
     */
    private List<KnowledgeSearchItem> retrieveContext(String message, List<ConversationMessage> history, QuestionType type) {
        List<KnowledgeSearchItem> allItems = new ArrayList<>();
        String contextualQuery = buildContextualQuery(message, history);

        // 用户档案（激活的模板）永远最优先注入：所有回答以用户亲手填写/上传的信息为事实来源
        allItems.addAll(knowledgeBaseService.searchBySourceName(KnowledgeBaseService.PROFILE_SOURCE_NAME));

        switch (type) {
            case SELF_INTRODUCTION -> {
                // 自我介绍：先精确查找简历类资料，再广泛搜索简历相关内容
                allItems.addAll(knowledgeBaseService.searchBySourceType("resume", 8));
                allItems.addAll(knowledgeBaseService.search(contextualQuery + " 简历 教育背景 技能 实习经历").items());
                allItems.addAll(knowledgeBaseService.search("项目 技术栈 亮点 成果").items());
            }
            case PROJECT -> {
                // 项目类：简历概览 + 查询中被提及的项目文档 + 相关性检索
                // 项目名来自知识库元数据动态匹配，新增/删除项目文档无需修改代码
                allItems.addAll(knowledgeBaseService.searchBySourceType("resume", 6));
                allItems.addAll(searchMentionedSources(contextualQuery));
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
     * 动态文档名匹配：查询文本（含上下文扩展）命中知识库 sourceName 时精确检索该文档。
     * sourceName 列表完全来自知识库元数据，替代原先写死的项目名检索。
     */
    private List<KnowledgeSearchItem> searchMentionedSources(String query) {
        List<KnowledgeSearchItem> items = new ArrayList<>();
        String normalizedQuery = query.toLowerCase();
        for (String sourceName : knowledgeBaseService.sourceNames()) {
            if (matchesSourceName(normalizedQuery, sourceName)) {
                items.addAll(knowledgeBaseService.searchBySourceName(sourceName));
            }
        }
        return items;
    }

    private boolean matchesSourceName(String normalizedQuery, String sourceName) {
        String normalizedName = sourceName.toLowerCase();
        if (normalizedName.isEmpty()) {
            return false;
        }
        // 完整文档名（含中文名）被提及时命中
        if (normalizedQuery.contains(normalizedName)) {
            return true;
        }
        // 名称中的 ASCII 词干（如 "fundpilot"、"sec-sonar"）被提及时也算命中
        for (String stem : normalizedName.split("[^a-z0-9]+")) {
            if (stem.length() >= 3 && normalizedQuery.contains(stem)) {
                return true;
            }
        }
        return false;
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

        // 项目类（仅用通用词判断，项目专属名词不硬编码进分类逻辑）
        String lowerMsg = msg.toLowerCase();
        if (msg.contains("项目") || msg.contains("作品") || msg.contains("系统")
                || msg.contains("平台") || msg.contains("应用") || msg.contains("开发过")
                || msg.contains("智能体")
                || lowerMsg.contains("agent") || lowerMsg.contains("rag")) {
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
        if (msg.contains("项目") || msg.contains("作品") || msg.contains("系统")) {
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
     * 基于知识库词频（IDF）动态抽取：越有区分度的词权重越高，
     * 不再维护硬编码关键词表，知识库内容变化时自动适应。
     */
    private List<String> extractKeywords(String text) {
        return knowledgeBaseService.extractKeywords(text, 6);
    }
}

