package com.resumeagent.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TechGuideService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TechGuideService.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final SafetyService safetyService;
    private final LlmService llmService;
    private final AuditService auditService;
    private final Map<String, List<ConversationMessage>> conversationStore = new ConcurrentHashMap<>();

    /** 模块名称映射，用于构建系统提示词 */
    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry("java", "Java基础"),
            Map.entry("spring", "Spring生态"),
            Map.entry("database", "数据库与缓存"),
            Map.entry("rag", "RAG核心技术"),
            Map.entry("agent", "Agent架构"),
            Map.entry("security", "安全机制"),
            Map.entry("resume-agent", "项目实战:ResumeAgent"),
            Map.entry("fundpilot", "项目实战:FundPilot")
    );

    public TechGuideService(KnowledgeBaseService knowledgeBaseService,
                            SafetyService safetyService,
                            LlmService llmService,
                            AuditService auditService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.safetyService = safetyService;
        this.llmService = llmService;
        this.auditService = auditService;
    }

    public SseEmitter stream(String module, String tag, String message, String conversationId) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 安全检查
                SafetyVerdict verdict = safetyService.inspect(message);
                if (!verdict.allowed()) {
                    emitter.send("⚠️ " + verdict.reason());
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                    auditService.record("techguide-blocked", conversationId, message, verdict.reason(), "injection");
                    return;
                }

                // 2. 获取对话历史
                List<ConversationMessage> history = historyMessages(conversationId);

                // 3. RAG 检索相关知识
                String moduleName = MODULE_NAMES.getOrDefault(module, module);
                List<KnowledgeSearchItem> contexts = retrieveContext(module, tag, message);

                // 4. 构建系统提示词和用户提示词
                String systemPrompt = buildSystemPrompt(moduleName, tag, history);
                String userPrompt = buildUserPrompt(message, tag, contexts);

                // 5. 将历史转为 messages 格式
                List<Map<String, String>> historyMessages = history.stream()
                        .map(m -> Map.of("role", m.role(), "content", m.content()))
                        .toList();

                // 6. 调用 LLM 生成
                String reply = llmService.generate(systemPrompt, userPrompt, historyMessages)
                        .orElseGet(() -> buildFallbackAnswer(moduleName, tag, contexts));

                // 7. 提取追问问题并从主内容中移除
                List<String> followups = extractFollowups(reply);
                String cleanReply = removeFollowupBlock(reply);

                // 8. 流式输出（复用 ChatService 的分块策略）
                for (String chunk : splitIntoChunks(cleanReply)) {
                    emitter.send(chunk);
                    Thread.sleep(80L);
                }

                // 9. 发送追问问题（JSON 数组格式）
                if (!followups.isEmpty()) {
                    String followupJson = String.join("|||", followups);
                    emitter.send(SseEmitter.event().name("followups").data(followupJson));
                }

                // 10. 记录对话历史（存储清理后的内容）
                appendMessage(conversationId, "user", message);
                appendMessage(conversationId, "assistant", cleanReply);

                // 11. 审计日志
                auditService.record("techguide", conversationId, message, cleanReply,
                        "module=" + module + ", tag=" + tag);

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception exception) {
                LOGGER.error("TechGuide stream error", exception);
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    /**
     * 构建教学导师系统提示词。
     * 核心：像朋友聊天一样讲解技术，根据用户状态灵活调整。
     */
    private String buildSystemPrompt(String moduleName, String tag, List<ConversationMessage> history) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                ## 角色设定
                你是"技术引航员"——一个聊着天就把技术讲明白的前辈程序员。你的风格是：
                像和朋友在咖啡厅聊天，而不是在教室里念PPT。

                ═══════════════════════════════════════════════════
                ## 教学原则（灵活运用，不是每次都要用全部）

                ### 核心理念
                - 生活类比是最好的老师，但要自然融入，不要生硬套用
                - ASCII图示、表情符号是辅助工具，该用时用，不需要时别硬塞
                - 代码示例要实用、可运行，不要为了凑数写代码
                - 重点是让用户"真的懂了"，而不是"看完了一篇结构工整的文章"

                ### 你可以用的教学手法（自由组合）
                - 生活类比：把抽象概念映射到日常经验
                - ASCII图示：展示数据流、结构关系
                - 代码演示：Spring Boot 3 优先，具体可运行
                - 对比讲解：有vs没有、好vs坏、常见误区
                - 口诀/速记：帮用户记住关键点
                - 反问引导：让用户先思考再给答案

                ═══════════════════════════════════════════════════
                ## 对话适应（最重要！）

                根据用户的反应灵活调整你的回答方式：

                ### 用户首次提问某个知识点
                → 可以讲得系统一些，先给全景再深入细节。但不要死板套模板，
                  根据知识点的特点选择最合适的讲解方式。

                ### 用户说"不知道"/"没懂"/"能再讲讲吗"
                → 说明之前的讲解没到位。换个角度、换个比喻重新讲，
                  或者只讲最核心的一点。绝对不要重复之前的内容。

                ### 用户问具体细节
                → 直接回答细节，不需要再从头讲起。

                ### 用户给出了思考/答案
                → 先评价对错，再补充。对了就肯定+扩展，错了就纠正+解释为什么。

                ### 用户问的问题超出当前模块
                → 友好提示并建议切换到对应模块。

                ═══════════════════════════════════════════════════
                ## 教学输出规则（必须遵守）
                在完成当前知识点的类比教学和核心说明后，你必须在结尾处主动提出 2-3 个与当前内容深度相关、且由浅入深的引导性问题。
                这些问题的格式必须严格如下（不要加任何其他标记或前缀）：

                <FOLLOWUP>
                问题1文本
                问题2文本
                问题3文本
                </FOLLOWUP>

                问题要求：
                - 第1个问题：回顾当前知识点的核心概念（巩固基础）
                - 第2个问题：引向更深层的原理或实现细节（深入探索）
                - 第3个问题：关联到实际项目或跨模块知识（融会贯通）
                - 每个问题不超过25个字，简洁明了
                - 问题要能激发好奇心，不要是简单的"是/否"问题

                ═══════════════════════════════════════════════════
                ## 禁止行为
                - 禁止每次都用完全相同的回答结构（千篇一律的四段式）
                - 禁止用户说"不知道"后换个说法重复同样的内容
                - 禁止编造不存在的技术细节或API
                - 禁止使用"据我所知"等模糊措辞
                - 禁止暴露AI身份
                """);

        // 注入当前模块上下文
        sb.append("\n## 当前教学上下文\n");
        sb.append("- 学习模块：").append(moduleName).append("\n");
        if (tag != null && !tag.isBlank()) {
            sb.append("- 知识点：").append(tag).append("\n");
        }

        // 注入对话历史摘要
        if (!history.isEmpty()) {
            sb.append("\n## 对话历史（保持连贯性）\n");
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                ConversationMessage msg = history.get(i);
                sb.append("- ").append(msg.role()).append(": ");
                // 截断过长的历史消息
                String content = msg.content();
                sb.append(content.length() > 200 ? content.substring(0, 200) + "..." : content);
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 构建用户提示词，注入 RAG 检索到的知识。
     */
    private String buildUserPrompt(String message, String tag, List<KnowledgeSearchItem> contexts) {
        StringBuilder sb = new StringBuilder();

        if (!contexts.isEmpty()) {
            sb.append("## 参考资料（从知识库检索，结合到教学中）\n");
            for (int i = 0; i < Math.min(contexts.size(), 5); i++) {
                sb.append("- ").append(contexts.get(i).content()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 用户提问\n");
        sb.append(message);

        return sb.toString();
    }

    /**
     * RAG 检索：根据模块和知识点检索相关知识。
     */
    private List<KnowledgeSearchItem> retrieveContext(String module, String tag, String message) {
        List<KnowledgeSearchItem> allItems = new ArrayList<>();

        // 1. 用知识点标签检索
        if (tag != null && !tag.isBlank()) {
            allItems.addAll(knowledgeBaseService.search(tag + " 技术原理 最佳实践").items());
        }

        // 2. 用用户消息检索
        allItems.addAll(knowledgeBaseService.search(message).items());

        // 3. 针对项目实战模块，检索项目文档
        if ("resume-agent".equals(module)) {
            allItems.addAll(knowledgeBaseService.searchBySourceName("ResumeAgent"));
            allItems.addAll(knowledgeBaseService.search("RAG 知识库 向量检索 分块").items());
        } else if ("fundpilot".equals(module)) {
            allItems.addAll(knowledgeBaseService.searchBySourceName("FundPilot-AI"));
            allItems.addAll(knowledgeBaseService.search("基金分析 AI 决策 路由").items());
        }

        // 去重
        return deduplicateItems(allItems);
    }

    /**
     * LLM 不可用时的降级回答。
     */
    private String buildFallbackAnswer(String moduleName, String tag, List<KnowledgeSearchItem> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("📚 **").append(moduleName).append("**");
        if (tag != null && !tag.isBlank()) {
            sb.append(" > ").append(tag);
        }
        sb.append("\n\n");

        if (!contexts.isEmpty()) {
            sb.append("根据知识库中的资料，以下是相关内容：\n\n");
            for (int i = 0; i < Math.min(contexts.size(), 3); i++) {
                sb.append("- ").append(contexts.get(i).content()).append("\n");
            }
            sb.append("\n💡 提示：配置 LLM 后可以获得更详细的教学内容。");
        } else {
            sb.append("当前知识点的详细教学内容正在准备中。\n\n");
            sb.append("你可以：\n");
            sb.append("1. 先查看其他知识点\n");
            sb.append("2. 在知识库中上传相关学习资料\n");
            sb.append("3. 配置 LLM 后获得 AI 驱动的个性化教学");
        }

        return sb.toString();
    }

    private List<ConversationMessage> historyMessages(String conversationId) {
        return conversationStore.getOrDefault(conversationId, Collections.emptyList());
    }

    private void appendMessage(String conversationId, String role, String content) {
        conversationStore.computeIfAbsent(conversationId, key -> new CopyOnWriteArrayList<>())
                .add(new ConversationMessage(role, content));
    }

    private List<KnowledgeSearchItem> deduplicateItems(List<KnowledgeSearchItem> items) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<KnowledgeSearchItem> result = new ArrayList<>();
        for (KnowledgeSearchItem item : items) {
            String key = item.content().length() > 100 ? item.content().substring(0, 100) : item.content();
            if (seen.add(key)) {
                result.add(item);
            }
        }
        return result.size() > 8 ? result.subList(0, 8) : result;
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

    /**
     * 从 LLM 回复中提取 <FOLLOWUP> 标签内的追问问题列表。
     */
    private List<String> extractFollowups(String reply) {
        List<String> followups = new ArrayList<>();
        int start = reply.indexOf("<FOLLOWUP>");
        int end = reply.indexOf("</FOLLOWUP>");
        if (start != -1 && end != -1 && end > start) {
            String block = reply.substring(start + "<FOLLOWUP>".length(), end).trim();
            for (String line : block.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    // 去除可能的序号前缀（如 "1. "、"- "）
                    trimmed = trimmed.replaceAll("^\\d+\\.\\s*", "").replaceAll("^-\\s*", "");
                    followups.add(trimmed);
                }
            }
        }
        return followups;
    }

    /**
     * 从 LLM 回复中移除 <FOLLOWUP>...</FOLLOWUP> 代码块，使主内容更干净。
     */
    private String removeFollowupBlock(String reply) {
        return reply.replaceAll("(?s)<FOLLOWUP>.*?</FOLLOWUP>", "").trim();
    }
}
