package com.resumeagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.dto.KnowledgeSearchItem;
import com.resumeagent.dto.MatchReportRequest;
import com.resumeagent.dto.MatchReportResponse;
import com.resumeagent.dto.MatchReportResponse.DimensionScore;
import com.resumeagent.dto.MatchReportResponse.RequirementMatch;
import com.resumeagent.dto.MatchReportResponse.ResumeSuggestion;
import com.resumeagent.model.SafetyVerdict;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 简历 / JD 深度匹配报告：
 * 逐条拆解 JD 要求 → 与用户档案（+ 项目资料）比对 → 输出总分、维度分、
 * 逐条匹配结论与简历修改建议。
 *
 * 双路径：LLM 语义分析（JSON 结构化输出）；LLM 未配置时关键词降级分析。
 * 事实依据始终来自知识库（用户档案优先），不使用任何硬编码个人信息。
 */
@Service
public class MatchReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchReportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SafetyService safetyService;
    private final AuditService auditService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final LlmService llmService;

    public MatchReportService(SafetyService safetyService,
                              AuditService auditService,
                              KnowledgeBaseService knowledgeBaseService,
                              LlmService llmService) {
        this.safetyService = safetyService;
        this.auditService = auditService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.llmService = llmService;
    }

    public MatchReportResponse analyze(MatchReportRequest request) {
        String mergedInput = request.jobTitle() + "\n" + request.jobDescription();
        SafetyVerdict verdict = safetyService.inspect(mergedInput);
        if (!verdict.allowed()) {
            auditService.record("match-report-blocked", null, verdict.sanitizedText(), verdict.reason(), "injection");
            return new MatchReportResponse(request.jobTitle(), 0,
                    List.of(), List.of(), List.of(), "输入包含高风险指令，已拒绝分析", true);
        }

        String profileText = collectEvidence(request);

        Optional<String> llmResult = llmService.generate(buildSystemPrompt(), buildUserPrompt(request, profileText));
        if (llmResult.isPresent()) {
            try {
                MatchReportResponse parsed = parseLlmResponse(request.jobTitle(), llmResult.get());
                auditService.record("match-report", null, request.jobTitle(),
                        String.valueOf(parsed.overallScore()), "llm");
                return parsed;
            } catch (Exception e) {
                LOGGER.warn("匹配报告 JSON 解析失败，转降级分析", e);
            }
        }

        MatchReportResponse fallback = fallbackAnalyze(request, profileText);
        auditService.record("match-report", null, request.jobTitle(),
                String.valueOf(fallback.overallScore()), "degraded");
        return fallback;
    }

    // ===== 事实依据：用户档案优先 + 相关项目资料 =====

    private String collectEvidence(MatchReportRequest request) {
        StringBuilder sb = new StringBuilder();
        List<KnowledgeSearchItem> profile = knowledgeBaseService
                .searchBySourceName(KnowledgeBaseService.PROFILE_SOURCE_NAME);
        for (KnowledgeSearchItem item : profile) {
            sb.append(item.content()).append('\n');
        }
        List<KnowledgeSearchItem> related = knowledgeBaseService
                .search(request.jobTitle() + " " + request.jobDescription() + " 项目 技术栈").items();
        for (KnowledgeSearchItem item : related) {
            sb.append(item.content()).append('\n');
        }
        return sb.toString().trim();
    }

    // ===== LLM 路径 =====

    private String buildSystemPrompt() {
        return """
                你是资深技术招聘顾问。根据求职者的档案资料与目标岗位 JD，输出深度匹配报告。
                事实依据只能来自给出的档案资料，不得编造经历。

                ## 输出要求（严格 JSON，不要输出其它内容）
                ```json
                {
                  "overallScore": 0-100,
                  "dimensions": [
                    {"name": "技术栈", "score": 0-100, "comment": "一句话点评"},
                    {"name": "项目经验", "score": 0-100, "comment": "一句话点评"},
                    {"name": "岗位契合", "score": 0-100, "comment": "一句话点评"},
                    {"name": "表达呈现", "score": 0-100, "comment": "一句话点评"}
                  ],
                  "requirementMatches": [
                    {"requirement": "JD 中的一条要求", "status": "满足|部分|缺失",
                     "evidence": "档案中对应的证据（缺失则写'档案中未见相关描述'）",
                     "advice": "针对该条的补齐建议"}
                  ],
                  "resumeSuggestions": [
                    {"original": "简历/档案中的原文片段", "suggestion": "改写建议", "reason": "为什么这样改"}
                  ],
                  "summary": "总体评价（80字内）"
                }
                ```
                - requirementMatches：把 JD 拆成 5-10 条独立要求，逐条评估
                - resumeSuggestions：3-5 条具体到字句的简历修改建议
                """;
    }

    private String buildUserPrompt(MatchReportRequest request, String profileText) {
        return "目标岗位：" + request.jobTitle() + "\n\n岗位描述（JD）：\n" + request.jobDescription()
                + "\n\n求职者档案资料：\n" + (profileText.isBlank() ? "（暂无档案资料）" : profileText);
    }

    private MatchReportResponse parseLlmResponse(String jobTitle, String llmOutput) throws Exception {
        JsonNode root = MAPPER.readTree(extractJson(llmOutput));
        int overallScore = clampScore(root.path("overallScore").asInt(50));

        List<DimensionScore> dimensions = new ArrayList<>();
        for (JsonNode node : root.path("dimensions")) {
            dimensions.add(new DimensionScore(
                    node.path("name").asText("维度"),
                    clampScore(node.path("score").asInt(50)),
                    node.path("comment").asText("")));
        }

        List<RequirementMatch> matches = new ArrayList<>();
        for (JsonNode node : root.path("requirementMatches")) {
            matches.add(new RequirementMatch(
                    node.path("requirement").asText(""),
                    normalizeStatus(node.path("status").asText("部分")),
                    node.path("evidence").asText(""),
                    node.path("advice").asText("")));
        }

        List<ResumeSuggestion> suggestions = new ArrayList<>();
        for (JsonNode node : root.path("resumeSuggestions")) {
            suggestions.add(new ResumeSuggestion(
                    node.path("original").asText(""),
                    node.path("suggestion").asText(""),
                    node.path("reason").asText("")));
        }

        return new MatchReportResponse(jobTitle, overallScore,
                dimensions.isEmpty() ? defaultDimensions(overallScore) : dimensions,
                matches, suggestions,
                root.path("summary").asText("基于档案与 JD 的匹配分析"), false);
    }

    // ===== 降级路径：关键词逐条匹配 =====

    private MatchReportResponse fallbackAnalyze(MatchReportRequest request, String profileText) {
        List<String> requirements = splitRequirements(request.jobDescription());
        String normalizedProfile = normalize(profileText);
        Set<String> profileTokens = new LinkedHashSet<>(KnowledgeBaseService.tokensOf(normalizedProfile));

        List<RequirementMatch> matches = new ArrayList<>();
        List<String> missingTerms = new ArrayList<>();
        int scoreSum = 0;
        for (String requirement : requirements) {
            Set<String> reqTokens = requirementTokens(requirement);
            int hit = 0;
            List<String> matched = new ArrayList<>();
            List<String> missingDisplay = new ArrayList<>();
            for (String token : reqTokens) {
                if (profileTokens.contains(token) || normalizedProfile.contains(token)) {
                    hit++;
                    if (isDisplayTerm(token)) {
                        matched.add(token);
                    }
                } else if (isDisplayTerm(token)) {
                    missingDisplay.add(token);
                    missingTerms.add(token);
                }
            }
            int ratio = reqTokens.isEmpty() ? 100 : (hit * 100) / reqTokens.size();
            String status = ratio >= 60 ? "满足" : ratio >= 20 ? "部分" : "缺失";
            String evidence = matched.isEmpty() ? "档案中未见相关描述"
                    : "档案中包含：" + String.join("、", matched.subList(0, Math.min(5, matched.size())));
            String advice = switch (status) {
                case "满足" -> "在面试中结合项目细节展开，突出量化成果";
                case "部分" -> "补充与 " + String.join("、", pick(missingDisplay, 3)) + " 相关的项目细节或学习经历";
                default -> "缺少该条要求的直接证据，建议补充 " + String.join("、", pick(missingDisplay, 3)) + " 相关描述后再投递";
            };
            matches.add(new RequirementMatch(requirement, status, evidence, advice));
            scoreSum += ratio;
        }

        int overallScore = requirements.isEmpty() ? 50 : clampScore(scoreSum / requirements.size());

        // 维度分：基于档案完备度与关键词覆盖
        boolean hasProfile = !profileText.isBlank();
        int techScore = clampScore(overallScore);
        int projectScore = clampScore(hasProfile && profileText.contains("项目") ? overallScore : overallScore / 2);
        int fitScore = clampScore(normalize(profileText).contains(normalize(request.jobTitle())) ? 90 : overallScore);
        int presentScore = hasProfile && profileText.length() > 200 ? 80 : 40;
        List<DimensionScore> dimensions = List.of(
                new DimensionScore("技术栈", techScore, "基于 JD 关键词与档案技能的覆盖度"),
                new DimensionScore("项目经验", projectScore, "基于档案中项目经历的充实程度"),
                new DimensionScore("岗位契合", fitScore, "目标岗位与档案求职意向/经历方向的一致性"),
                new DimensionScore("表达呈现", presentScore, "档案内容的完整度与可展示性"));

        List<ResumeSuggestion> suggestions = buildFallbackSuggestions(profileText, missingTerms);

        String summary = hasProfile
                ? "共拆解 " + requirements.size() + " 条 JD 要求，匹配度 " + overallScore
                + "%。建议重点补齐「缺失/部分」条目对应的证据。"
                : "尚未填写档案：请先在「我的档案」填写网申信息或上传简历，报告将更准确。";

        return new MatchReportResponse(request.jobTitle(), overallScore,
                dimensions, matches, suggestions, summary, true);
    }

    private List<ResumeSuggestion> buildFallbackSuggestions(String profileText, List<String> missingTerms) {
        List<ResumeSuggestion> suggestions = new ArrayList<>();
        if (profileText.isBlank()) {
            suggestions.add(new ResumeSuggestion("（暂无档案）",
                    "在「我的档案」中填写网申信息并上传简历", "所有分析的事实来源是你的档案，填写后报告才有依据"));
            return suggestions;
        }
        List<String> topMissing = pick(missingTerms, 5);
        if (!topMissing.isEmpty()) {
            suggestions.add(new ResumeSuggestion("技能特长部分",
                    "补充与 " + String.join("、", topMissing) + " 相关的掌握程度与使用场景",
                    "这些是 JD 中出现但档案未覆盖的关键词，补充后可显著提升初筛命中率"));
        }
        if (!profileText.contains("倍") && !profileText.contains("%") && !profileText.matches(".*\\d+.*")) {
            suggestions.add(new ResumeSuggestion("项目/实习经历描述",
                    "为每段经历补充量化成果（性能提升、准确率、规模等数字）",
                    "量化数字是筛选官判断项目含金量的第一依据"));
        }
        if (profileText.length() < 300) {
            suggestions.add(new ResumeSuggestion("整体内容",
                    "扩写项目经历：背景 → 你的职责 → 技术方案 → 成果",
                    "当前档案信息偏少，按 STAR 结构扩写后匹配分析与面试问答都会更扎实"));
        }
        return suggestions;
    }

    // ===== 工具方法 =====

    private List<String> splitRequirements(String jobDescription) {
        List<String> result = new ArrayList<>();
        for (String part : jobDescription.split("[\\n;；。、,，]")) {
            String trimmed = part.trim();
            if (trimmed.length() >= 4 && trimmed.length() <= 80) {
                result.add(trimmed);
            }
            if (result.size() >= 10) {
                break;
            }
        }
        if (result.isEmpty() && !jobDescription.isBlank()) {
            result.add(jobDescription.trim());
        }
        return result;
    }

    /** 要求条目的有效词元（去掉低信息量的虚词/单字） */
    private Set<String> requirementTokens(String requirement) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : KnowledgeBaseService.tokensOf(normalize(requirement))) {
            if (token.length() >= 2 && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** 适合展示给人看的词元：完整词/技术名词（过滤 CJK bigram 碎片噪音） */
    private boolean isDisplayTerm(String token) {
        if (token.length() >= 3) {
            return true;
        }
        // 纯 ASCII 技术词（java、go 等）保留
        return token.chars().allMatch(ch -> ch < 128) && token.length() >= 2;
    }

    private static final Set<String> STOPWORDS = Set.of(
            "熟悉", "了解", "掌握", "优先", "具备", "能够", "负责", "相关", "经验", "工作",
            "良好", "能力", "以上", "学历", "要求", "岗位", "职责", "开发", "支持");

    private List<String> pick(List<String> source, int count) {
        List<String> picked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String item : source) {
            if (seen.add(item)) {
                picked.add(item);
            }
            if (picked.size() >= count) {
                break;
            }
        }
        return picked;
    }

    private String normalizeStatus(String status) {
        if (status.contains("满")) return "满足";
        if (status.contains("缺")) return "缺失";
        return "部分";
    }

    private List<DimensionScore> defaultDimensions(int overallScore) {
        return List.of(
                new DimensionScore("技术栈", overallScore, ""),
                new DimensionScore("项目经验", overallScore, ""),
                new DimensionScore("岗位契合", overallScore, ""),
                new DimensionScore("表达呈现", overallScore, ""));
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("LLM 输出中未找到 JSON");
        }
        return text.substring(start, end + 1);
    }
}
