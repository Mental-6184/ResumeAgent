package com.resumeagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.dto.InterviewReviewRequest;
import com.resumeagent.dto.InterviewReviewResponse;
import com.resumeagent.dto.InterviewReviewResponse.QuestionReview;
import com.resumeagent.dto.InterviewReviewResponse.ReviewDimension;
import com.resumeagent.dto.InterviewReviewSummary;
import com.resumeagent.model.SafetyVerdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 模拟面试复盘：对一场面试的问答记录做结构化评估
 * （内容深度/技术扎实度/表达结构/岗位匹配 + 亮点/改进 + 逐题点评），并存档供对比。
 *
 * 双路径：LLM 语义复盘；LLM 未配置时基于回答长度/结构标记/关键词覆盖的统计复盘。
 */
@Service
public class InterviewReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterviewReviewService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SafetyService safetyService;
    private final AuditService auditService;
    private final LlmService llmService;
    private final Path storageDir;

    @org.springframework.beans.factory.annotation.Autowired
    public InterviewReviewService(SafetyService safetyService,
                                  AuditService auditService,
                                  LlmService llmService) {
        this(safetyService, auditService, llmService, DataDirs.resolve().resolve("reviews"));
    }

    public InterviewReviewService(SafetyService safetyService,
                                  AuditService auditService,
                                  LlmService llmService,
                                  Path storageDir) {
        this.safetyService = safetyService;
        this.auditService = auditService;
        this.llmService = llmService;
        this.storageDir = storageDir;
    }

    /** 生成复盘并存档 */
    public InterviewReviewResponse review(InterviewReviewRequest request) {
        List<InterviewReviewRequest.ReviewMessage> messages = request.messages().stream()
                .filter(message -> message != null && message.role() != null && message.content() != null
                        && !message.content().isBlank())
                .toList();
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("面试记录为空");
        }

        StringBuilder all = new StringBuilder();
        messages.forEach(message -> all.append(message.content()).append('\n'));
        SafetyVerdict verdict = safetyService.inspect(all.toString());
        if (!verdict.allowed()) {
            auditService.record("review-blocked", null, verdict.sanitizedText(), verdict.reason(), "injection");
            throw new IllegalArgumentException("输入包含高风险指令，已拒绝分析");
        }

        String title = request.sessionTitle() == null || request.sessionTitle().isBlank()
                ? "面试复盘 " + java.time.LocalDateTime.now().toString().replace('T', ' ').substring(0, 16)
                : request.sessionTitle().trim();
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();

        InterviewReviewResponse response = llmService.generate(buildSystemPrompt(), buildUserPrompt(request, messages))
                .flatMap(output -> parseReview(id, title, createdAt, output))
                .orElseGet(() -> fallbackReview(id, title, createdAt, request.jobTitle(), messages));

        persist(response);
        auditService.record("interview-review", id, title, String.valueOf(response.overallScore()),
                response.degraded() ? "degraded" : "llm");
        return response;
    }

    public List<InterviewReviewSummary> list() {
        List<InterviewReviewSummary> summaries = new ArrayList<>();
        for (InterviewReviewResponse review : loadAll()) {
            summaries.add(new InterviewReviewSummary(review.id(), review.title(), review.createdAt(), review.overallScore()));
        }
        summaries.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
        return summaries;
    }

    public Optional<InterviewReviewResponse> get(String id) {
        Path file = storageDir.resolve(id + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.toFile(), InterviewReviewResponse.class));
        } catch (IOException e) {
            LOGGER.warn("读取复盘失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean delete(String id) {
        try {
            return Files.deleteIfExists(storageDir.resolve(id + ".json"));
        } catch (IOException e) {
            LOGGER.warn("删除复盘失败: {}", e.getMessage());
            return false;
        }
    }

    // ===== LLM 路径 =====

    private String buildSystemPrompt() {
        return """
                你是资深面试教练。基于一场模拟面试的问答记录，给出客观、具体的复盘报告。
                评估只能依据记录内容，不要编造。

                ## 输出要求（严格 JSON，不要输出其它内容）
                ```json
                {
                  "overallScore": 0-100,
                  "dimensions": [
                    {"name": "内容深度", "score": 0-100, "comment": "一句话点评"},
                    {"name": "技术扎实度", "score": 0-100, "comment": "一句话点评"},
                    {"name": "表达结构", "score": 0-100, "comment": "一句话点评"},
                    {"name": "岗位匹配", "score": 0-100, "comment": "一句话点评"}
                  ],
                  "highlights": ["回答中的亮点（引用原话要点）"],
                  "improvements": ["具体可操作的改进项"],
                  "perQuestion": [
                    {"question": "面试官的问题", "score": 0-100, "comment": "该题表现点评", "improvement": "这题怎么答更好"}
                  ],
                  "summary": "总体复盘（100字内）"
                }
                ```
                - highlights 2-4 条，improvements 3-5 条，perQuestion 覆盖每个面试官问题
                """;
    }

    private String buildUserPrompt(InterviewReviewRequest request,
                                   List<InterviewReviewRequest.ReviewMessage> messages) {
        StringBuilder sb = new StringBuilder();
        if (request.jobTitle() != null && !request.jobTitle().isBlank()) {
            sb.append("目标岗位：").append(request.jobTitle()).append("\n\n");
        }
        sb.append("面试记录：\n");
        for (InterviewReviewRequest.ReviewMessage message : messages) {
            sb.append("user".equals(message.role()) ? "面试官：" : "候选人：")
                    .append(message.content()).append('\n');
        }
        return sb.toString();
    }

    private Optional<InterviewReviewResponse> parseReview(String id, String title, long createdAt, String output) {
        try {
            String json = output.substring(output.indexOf('{'), output.lastIndexOf('}') + 1);
            JsonNode root = MAPPER.readTree(json);
            int overall = clamp(root.path("overallScore").asInt(50));

            List<ReviewDimension> dimensions = new ArrayList<>();
            for (JsonNode node : root.path("dimensions")) {
                dimensions.add(new ReviewDimension(node.path("name").asText("维度"),
                        clamp(node.path("score").asInt(50)), node.path("comment").asText("")));
            }
            List<String> highlights = textArray(root, "highlights");
            List<String> improvements = textArray(root, "improvements");
            List<QuestionReview> perQuestion = new ArrayList<>();
            for (JsonNode node : root.path("perQuestion")) {
                perQuestion.add(new QuestionReview(node.path("question").asText(""),
                        clamp(node.path("score").asInt(50)),
                        node.path("comment").asText(""),
                        node.path("improvement").asText("")));
            }

            return Optional.of(new InterviewReviewResponse(id, title, createdAt, overall,
                    dimensions.isEmpty() ? defaultDimensions(overall) : dimensions,
                    highlights, improvements, perQuestion,
                    root.path("summary").asText("面试复盘"), false));
        } catch (Exception e) {
            LOGGER.warn("复盘 JSON 解析失败，转统计降级", e);
            return Optional.empty();
        }
    }

    // ===== 降级路径：统计复盘 =====

    private InterviewReviewResponse fallbackReview(String id, String title, long createdAt,
                                                   String jobTitle,
                                                   List<InterviewReviewRequest.ReviewMessage> messages) {
        List<QuestionReview> perQuestion = new ArrayList<>();
        int scoreSum = 0;
        int answerCount = 0;
        int structured = 0;

        String pendingQuestion = null;
        StringBuilder answerBuilder = new StringBuilder();
        for (InterviewReviewRequest.ReviewMessage message : messages) {
            if ("user".equals(message.role())) {
                if (pendingQuestion != null) {
                    scoreSum += scoreAnswer(answerBuilder.toString());
                    answerCount++;
                }
                pendingQuestion = message.content();
                answerBuilder.setLength(0);
            } else {
                answerBuilder.append(message.content()).append('\n');
            }
        }
        if (pendingQuestion != null) {
            String answer = answerBuilder.toString().trim();
            scoreSum += scoreAnswer(answer);
            answerCount++;
        }

        // 逐题重放，生成点评
        pendingQuestion = null;
        answerBuilder.setLength(0);
        for (InterviewReviewRequest.ReviewMessage message : messages) {
            if ("user".equals(message.role())) {
                if (pendingQuestion != null) {
                    String answer = answerBuilder.toString().trim();
                    perQuestion.add(questionReview(pendingQuestion, answer));
                    if (isStructured(answer)) {
                        structured++;
                    }
                }
                pendingQuestion = message.content();
                answerBuilder.setLength(0);
            } else {
                answerBuilder.append(message.content()).append('\n');
            }
        }
        if (pendingQuestion != null) {
            String answer = answerBuilder.toString().trim();
            perQuestion.add(questionReview(pendingQuestion, answer));
            if (isStructured(answer)) {
                structured++;
            }
        }

        int overall = answerCount == 0 ? 50 : clamp(scoreSum / answerCount);
        int depthScore = overall;
        int techScore = clamp(overall - 5);
        int structureScore = perQuestion.isEmpty() ? 50
                : clamp((structured * 100) / perQuestion.size());
        int fitScore = clamp((jobTitle == null || jobTitle.isBlank()) ? overall : overall + 5);

        List<String> highlights = new ArrayList<>();
        if (structured > 0) {
            highlights.add("多数回答有分点/序号结构，层次清晰（" + structured + "/" + perQuestion.size() + " 题）");
        }
        highlights.add("完成了一场完整的模拟面试（共 " + perQuestion.size() + " 个问题），坚持答完本身就是有效训练");

        List<String> improvements = new ArrayList<>();
        if (structureScore < 70) {
            improvements.add("增强回答结构：用「总-分-总」或 STAR（情境-任务-行动-结果）组织每段回答");
        }
        improvements.add("为关键结论补充量化数字（性能、规模、准确率），提升可信度");
        improvements.add("技术类回答先说结论与原理，再结合项目实例展开");

        String summary = "本场共 " + perQuestion.size() + " 题，整体表现 " + overall
                + " 分。重点改进方向：回答结构化与量化成果。";

        return new InterviewReviewResponse(id, title, createdAt, overall,
                List.of(new ReviewDimension("内容深度", depthScore, "基于回答篇幅与展开程度"),
                        new ReviewDimension("技术扎实度", techScore, "基于回答中技术要点的覆盖"),
                        new ReviewDimension("表达结构", structureScore, "基于分点/序号等结构化表达占比"),
                        new ReviewDimension("岗位匹配", fitScore, "基于目标岗位与回答方向的一致性")),
                highlights, improvements, perQuestion, summary, true);
    }

    private QuestionReview questionReview(String question, String answer) {
        int score = scoreAnswer(answer);
        String comment = answer.length() < 100 ? "回答偏短，展开不足"
                : isStructured(answer) ? "回答有结构、有展开" : "内容尚可，结构可以更清晰";
        String improvement = answer.length() < 100
                ? "按「观点-依据-例子-总结」四步展开到 200 字以上"
                : "保留结构，补充具体数据与项目细节";
        return new QuestionReview(question, score, comment, improvement);
    }

    /** 单题评分：篇幅 + 结构标记 + 量化数字 */
    private int scoreAnswer(String answer) {
        int score = 40;
        if (answer.length() >= 100) score += 15;
        if (answer.length() >= 200) score += 15;
        if (answer.length() >= 400) score += 10;
        if (isStructured(answer)) score += 10;
        if (answer.matches(".*\\d+.*")) score += 10;
        return clamp(score);
    }

    private boolean isStructured(String answer) {
        return answer.contains("首先") || answer.contains("其次") || answer.contains("第一")
                || answer.matches("(?s).*\\n\\s*[-*•]\\s.*") || answer.matches("(?s).*\\n\\s*\\d+[.、)].*");
    }

    private List<InterviewReviewResponse> loadAll() {
        if (!Files.isDirectory(storageDir)) {
            return List.of();
        }
        List<InterviewReviewResponse> reviews = new ArrayList<>();
        try (Stream<Path> files = Files.list(storageDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            reviews.add(MAPPER.readValue(path.toFile(), InterviewReviewResponse.class));
                        } catch (IOException e) {
                            LOGGER.warn("读取复盘失败: {}", e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("扫描复盘目录失败: {}", e.getMessage());
        }
        return reviews;
    }

    private void persist(InterviewReviewResponse response) {
        try {
            Files.createDirectories(storageDir);
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(storageDir.resolve(response.id() + ".json").toFile(), response);
        } catch (IOException e) {
            LOGGER.warn("持久化复盘失败: {}", e.getMessage());
        }
    }

    private List<String> textArray(JsonNode root, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : root.path(field)) {
            if (!node.asText().isBlank()) {
                values.add(node.asText());
            }
        }
        return values;
    }

    private List<ReviewDimension> defaultDimensions(int overall) {
        return List.of(new ReviewDimension("内容深度", overall, ""),
                new ReviewDimension("技术扎实度", overall, ""),
                new ReviewDimension("表达结构", overall, ""),
                new ReviewDimension("岗位匹配", overall, ""));
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
