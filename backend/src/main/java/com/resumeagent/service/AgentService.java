package com.resumeagent.service;

import com.resumeagent.dto.*;
import com.resumeagent.dto.ProjectQueryResponse.ProjectInfo;
import com.resumeagent.model.SafetyVerdict;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final SafetyService safetyService;
    private final AuditService auditService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final LlmService llmService;
    private final PromptBuilder promptBuilder;

    /** 常见技术栈关键词，用于从知识库文本中提取 */
    private static final String[] KNOWN_TECH_KEYWORDS = {
            "Spring Boot", "Spring AI", "Spring Cloud", "Spring Security", "Spring Data JPA",
            "MyBatis", "MyBatis-Plus", "Vue", "Vue3", "React", "TypeScript", "JavaScript",
            "MySQL", "Redis", "MongoDB", "Elasticsearch", "Milvus",
            "Docker", "Kubernetes", "Nginx", "RabbitMQ", "Kafka",
            "Three.js", "MediaPipe", "GLSL", "Electron", "Canvas",
            "Thymeleaf", "Element Plus", "ECharts", "Tailwind CSS", "Zustand",
            "ByteBuddy", "RAG", "LLM", "OpenAI", "JWT",
            "微信小程序", "WXML", "WXSS", "Matter.js",
            "tesseract.js", "IndexedDB", "HTML", "CSS"
    };

    public AgentService(SafetyService safetyService, AuditService auditService,
                        KnowledgeBaseService knowledgeBaseService,
                        LlmService llmService, PromptBuilder promptBuilder) {
        this.safetyService = safetyService;
        this.auditService = auditService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
    }

    public JobAnalyzeResponse analyze(JobAnalyzeRequest request) {
        String profileSummary = getProfileFromKnowledgeBase();
        String mergedInput = request.jobTitle() + "\n" + request.jobDescription() + "\n" + profileSummary;
        SafetyVerdict verdict = safetyService.inspect(mergedInput);
        if (!verdict.allowed()) {
            auditService.record("agent-blocked", null, verdict.sanitizedText(), verdict.reason(), "injection");
            return new JobAnalyzeResponse(request.jobTitle(), 0, List.of("输入包含高风险指令，已拒绝分析"), List.of(verdict.reason()), List.of("移除注入式内容后重新提交"));
        }

        // 优先使用 LLM 进行语义分析
        Optional<String> llmResult = llmService.generate(
                "你是一个资深技术猎头和职业顾问，擅长分析候选人与岗位的匹配度。" +
                "请根据求职者的个人背景和目标岗位，进行深入的匹配度分析。\n\n" +
                "## 分析维度\n\n" +
                "### 1. 技术栈匹配度 (matchScore)\n" +
                "- 对比岗位要求的技术栈与求职者掌握的技术栈\n" +
                "- 评估技术深度匹配（了解/熟练/精通）\n" +
                "- 计算匹配度分数（0-100）\n\n" +
                "### 2. 核心优势 (strengths)\n" +
                "每个优势必须包含：\n" +
                "- 具体的技术能力点\n" +
                "- 对应的项目名称和实现细节\n" +
                "- 量化成果（如有）\n\n" +
                "**示例格式**：\n" +
                "\"具备 [技术能力] — 在 [项目名] 中实现了 [具体功能]，[量化成果]\"\n\n" +
                "### 3. 能力差距 (gaps)\n" +
                "每个差距必须包含：\n" +
                "- 岗位要求的具体能力\n" +
                "- 当前缺少的原因\n" +
                "- 弥补差距的具体路径\n\n" +
                "### 4. 提升建议 (suggestions)\n" +
                "每个建议必须：\n" +
                "- 结合求职者现有项目基础\n" +
                "- 给出具体的学习/实践方向\n" +
                "- 预期收益说明\n\n" +
                "## 输出要求\n" +
                "- 涉及求职者时用第一人称「我」表述\n" +
                "- 每个 strengths/gaps/suggestions 条目控制在 50-100字\n" +
                "- strengths 至少 3 条，gaps 2-3 条，suggestions 3-4 条\n" +
                "- 严格返回 JSON，不要添加其他内容\n\n" +
                "```json\n" +
                "{\"matchScore\": 0-100, \"strengths\": [\"具体优势1\"], \"gaps\": [\"具体不足1\"], \"suggestions\": [\"可操作建议1\"]}\n" +
                "```",
                "目标岗位：" + request.jobTitle() + "\n\n" +
                "岗位描述：\n" + request.jobDescription() + "\n\n" +
                "求职者背景：\n" + profileSummary
        );

        if (llmResult.isPresent()) {
            return parseJobAnalyzeResponse(request.jobTitle(), llmResult.get());
        }

        // LLM 不可用时，降级为关键词匹配
        return fallbackAnalyze(request, profileSummary, verdict);
    }

    private JobAnalyzeResponse parseJobAnalyzeResponse(String jobTitle, String llmOutput) {
        try {
            String json = extractJson(llmOutput);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            int score = root.has("matchScore") ? root.get("matchScore").asInt(50) : 50;
            List<String> strengths = parseJsonArray(root, "strengths");
            List<String> gaps = parseJsonArray(root, "gaps");
            List<String> suggestions = parseJsonArray(root, "suggestions");
            return new JobAnalyzeResponse(jobTitle, score,
                    strengths.isEmpty() ? List.of("LLM 未返回优势分析") : strengths,
                    gaps.isEmpty() ? List.of("未发现明显短板") : gaps,
                    suggestions.isEmpty() ? List.of("建议进一步了解岗位需求") : suggestions);
        } catch (Exception e) {
            return new JobAnalyzeResponse(jobTitle, 50,
                    List.of("LLM 返回了非结构化结果，已降级处理"),
                    List.of("无法解析详细分析"),
                    List.of("请重新尝试分析"));
        }
    }

    private JobAnalyzeResponse fallbackAnalyze(JobAnalyzeRequest request, String profileSummary, SafetyVerdict verdict) {
        Set<String> jobKeywords = extractKeywords(request.jobTitle() + " " + request.jobDescription());
        Set<String> profileKeywords = extractKeywords(profileSummary);

        Set<String> matchedKeywords = new HashSet<>(jobKeywords);
        matchedKeywords.retainAll(profileKeywords);

        Set<String> missingKeywords = new HashSet<>(jobKeywords);
        missingKeywords.removeAll(profileKeywords);

        int denominator = Math.max(jobKeywords.size(), 1);
        int matchScore = (int) Math.round((matchedKeywords.size() * 100.0) / denominator);

        // 从知识库检索相关项目
        var projectSearchResult = knowledgeBaseService.search(request.jobTitle() + " " + request.jobDescription() + " 项目");
        List<ProjectInfo> matchedProjects = buildProjectsFromKnowledgeBase(projectSearchResult.items());

        List<String> strengths = new ArrayList<>();
        // 基于知识库检索到的项目生成优势
        if (!matchedProjects.isEmpty()) {
            for (ProjectInfo project : matchedProjects.subList(0, Math.min(3, matchedProjects.size()))) {
                String techStr = project.techStack().isEmpty() ? "相关技术"
                        : String.join("、", project.techStack().subList(0, Math.min(3, project.techStack().size())));
                String highlights = project.highlights().isEmpty() ? ""
                        : "，擅长" + String.join("、", project.highlights().subList(0, Math.min(2, project.highlights().size())));
                strengths.add(String.format("具备 %s 实战经验 — 在 %s 中%s", techStr, project.name(), highlights));
            }
        }
        if (strengths.isEmpty()) {
            // 基于 profileSummary 中的关键词生成通用优势
            if (profileKeywords.contains("spring") || profileKeywords.contains("java")) {
                strengths.add("掌握 Java 后端开发和 Spring Boot 生态，具备构建企业级服务的能力");
            }
            if (profileKeywords.contains("rag") || profileKeywords.contains("agent")) {
                strengths.add("具备 AI 应用开发能力，有 RAG 和 Agent 相关实践经验");
            }
            if (strengths.isEmpty()) {
                strengths.add("具备全栈开发潜力，有多个独立完成的项目经验");
            }
        }

        List<String> gaps = new ArrayList<>();
        if (!missingKeywords.isEmpty()) {
            Set<String> techGaps = new HashSet<>();
            Set<String> conceptGaps = new HashSet<>();
            for (String keyword : missingKeywords) {
                if (keyword.matches(".*[a-z].*") || keyword.contains("java") || keyword.contains("spring")) {
                    techGaps.add(keyword);
                } else {
                    conceptGaps.add(keyword);
                }
            }
            if (!techGaps.isEmpty()) {
                gaps.add("技术栈差距：缺少 " + String.join("、", techGaps) + " 的项目实践，建议通过小型项目快速上手");
            }
            if (!conceptGaps.isEmpty()) {
                gaps.add("能力维度差距：在 " + String.join("、", conceptGaps) + " 方面需要加强，建议结合实际项目深入理解");
            }
        } else {
            gaps.add("技术栈匹配度高，需重点准备项目细节和架构设计类问题");
            gaps.add("建议深入研究岗位业务场景，准备相关的技术方案和优化案例");
        }

        List<String> suggestions = new ArrayList<>();
        if (!matchedProjects.isEmpty()) {
            suggestions.add(String.format("深化 %s 项目的技术深度，准备架构设计、性能优化、问题排查等细节问题",
                    matchedProjects.get(0).name()));
        }
        suggestions.add("针对岗位要求准备 2-3 个技术难点案例，使用 STAR 法则组织，包含具体的问题描述、解决方案和量化成果");

        auditService.record("agent-analyze", null, verdict.sanitizedText(), String.valueOf(matchScore), verdict.reason());
        return new JobAnalyzeResponse(request.jobTitle(), matchScore, strengths, gaps, suggestions);
    }

    public ProjectQueryResponse queryProjects(ProjectQueryRequest request) {
        SafetyVerdict verdict = safetyService.inspect(request.query());
        if (!verdict.allowed()) {
            auditService.record("agent-blocked", null, verdict.sanitizedText(), verdict.reason(), "injection");
            return new ProjectQueryResponse(request.query(), List.of(), "输入包含高风险指令，已拒绝查询");
        }

        // 从知识库中检索项目相关资料
        String searchQuery = request.query();
        if (request.techStack() != null && !request.techStack().isBlank()) {
            searchQuery += " " + request.techStack();
        }
        var searchResult = knowledgeBaseService.search(searchQuery + " 项目");

        List<ProjectInfo> matchedProjects = buildProjectsFromKnowledgeBase(searchResult.items());

        String summary = matchedProjects.isEmpty()
                ? "知识库中未找到匹配的项目，请先导入项目文档或使用其他关键词查询"
                : String.format("找到 %d 个匹配项目", matchedProjects.size());

        auditService.record("agent-project-query", null, request.query(), summary, verdict.reason());
        return new ProjectQueryResponse(request.query(), matchedProjects, summary);
    }

    /**
     * 从知识库检索结果中构建结构化的项目信息列表。
     * 按 sourceName 分组，提取技术栈和项目亮点。
     */
    private List<ProjectInfo> buildProjectsFromKnowledgeBase(List<KnowledgeSearchItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        // 按 sourceName 分组
        Map<String, List<KnowledgeSearchItem>> grouped = new LinkedHashMap<>();
        for (KnowledgeSearchItem item : items) {
            grouped.computeIfAbsent(item.sourceName(), k -> new ArrayList<>()).add(item);
        }

        List<ProjectInfo> projects = new ArrayList<>();
        for (Map.Entry<String, List<KnowledgeSearchItem>> entry : grouped.entrySet()) {
            String sourceName = entry.getKey();
            List<KnowledgeSearchItem> chunks = entry.getValue();

            // 合并所有 chunk 内容
            StringBuilder fullContent = new StringBuilder();
            for (KnowledgeSearchItem chunk : chunks) {
                if (fullContent.length() > 0) fullContent.append("\n");
                fullContent.append(chunk.content());
            }
            String content = fullContent.toString();

            // 提取技术栈
            List<String> techStack = extractTechFromText(content);

            // 提取亮点（取前几条有意义的句子）
            List<String> highlights = extractHighlights(content);

            // 描述取第一段或前200字
            String description = extractDescription(content);

            String role = content.contains("独立") ? "独立开发" : "项目开发";

            projects.add(new ProjectInfo(sourceName, description, techStack, role, highlights));
        }

        return projects;
    }

    /** 从文本中提取已知技术栈关键词 */
    private List<String> extractTechFromText(String text) {
        List<String> found = new ArrayList<>();
        for (String tech : KNOWN_TECH_KEYWORDS) {
            if (text.toLowerCase(Locale.ROOT).contains(tech.toLowerCase(Locale.ROOT))) {
                found.add(tech);
            }
        }
        return found;
    }

    /** 从文本中提取项目亮点（以"-"或数字开头的条目） */
    private List<String> extractHighlights(String text) {
        List<String> highlights = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^[\\-•*]\\s+.+") || trimmed.matches("^\\d+[.、].+")) {
                String highlight = trimmed.replaceFirst("^[\\-•*\\d.、]+\\s*", "").trim();
                if (highlight.length() >= 4 && highlight.length() <= 80) {
                    highlights.add(highlight);
                }
            }
        }
        return highlights.isEmpty() ? List.of() : highlights.subList(0, Math.min(5, highlights.size()));
    }

    /** 从文本中提取描述（第一段非空内容） */
    private String extractDescription(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() >= 10) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
            }
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    public SkillAnalysisResponse analyzeSkills(SkillAnalysisRequest request) {
        String profileSummary = getProfileFromKnowledgeBase();
        SafetyVerdict verdict = safetyService.inspect(profileSummary);
        if (!verdict.allowed()) {
            auditService.record("agent-blocked", null, verdict.sanitizedText(), verdict.reason(), "injection");
            return new SkillAnalysisResponse(List.of(), List.of(), List.of(), List.of("输入包含高风险指令，已拒绝分析"), "安全检查未通过");
        }

        // 优先使用 LLM 进行语义分析
        Optional<String> llmResult = llmService.generate(
                "你是一个资深技术面试官和职业顾问，擅长评估开发者的技术能力图谱。" +
                "请根据以下个人资料，进行全面的技术能力分析。\n\n" +
                "## 分析维度\n\n" +
                "### 1. 核心技能 (coreSkills)\n" +
                "每个技能必须包含：\n" +
                "- 技能名称和熟练程度（了解/熟练/精通）\n" +
                "- 使用该技能的项目数量\n" +
                "- 典型应用场景\n\n" +
                "**示例格式**：\n" +
                "\"[技术名]（[熟练度]）— 在 [N] 个项目中使用，擅长 [应用场景]\"\n\n" +
                "### 2. 技术优势 (technicalStrengths)\n" +
                "每个优势必须包含：\n" +
                "- 具体的技术能力描述\n" +
                "- 对应的项目案例和实现细节\n" +
                "- 可验证的成果指标\n\n" +
                "**示例格式**：\n" +
                "\"[能力描述] — 在 [项目名] 中 [具体实现]，[量化成果]\"\n\n" +
                "### 3. 技术覆盖面 (coverageAreas)\n" +
                "按领域分类，每个领域说明：\n" +
                "- 涵盖的具体技术\n" +
                "- 项目应用情况\n" +
                "- 技术深度评估\n\n" +
                "### 4. 进阶建议 (improvementSuggestions)\n" +
                "每个建议必须：\n" +
                "- 基于现有技能基础\n" +
                "- 给出具体的学习路径\n" +
                "- 说明预期收益和应用场景\n\n" +
                "## 输出要求\n" +
                "- 涉及求职者时用第一人称「我」表述\n" +
                "- 每个条目控制在 50-80字，要有具体项目支撑\n" +
                "- coreSkills 至少 5 项，technicalStrengths 4-5 项\n" +
                "- 严格返回 JSON，不要添加其他内容\n\n" +
                "```json\n" +
                "{\"coreSkills\": [\"技能1\"], \"technicalStrengths\": [\"优势1\"], \"coverageAreas\": [\"领域1\"], \"improvementSuggestions\": [\"建议1\"]}\n" +
                "```",
                "个人资料：\n" + profileSummary
        );

        if (llmResult.isPresent()) {
            return parseSkillAnalysisResponse(llmResult.get());
        }

        // LLM 不可用时，降级为关键词分析
        return fallbackAnalyzeSkills(profileSummary, verdict);
    }

    private SkillAnalysisResponse parseSkillAnalysisResponse(String llmOutput) {
        try {
            String json = extractJson(llmOutput);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            List<String> coreSkills = parseJsonArray(root, "coreSkills");
            List<String> technicalStrengths = parseJsonArray(root, "technicalStrengths");
            List<String> coverageAreas = parseJsonArray(root, "coverageAreas");
            List<String> improvementSuggestions = parseJsonArray(root, "improvementSuggestions");
            String summary = String.format("核心技能 %d 项，技术优势 %d 项，覆盖面 %d 个领域",
                    coreSkills.size(), technicalStrengths.size(), coverageAreas.size());
            auditService.record("agent-skill-analysis", null, "(LLM)", summary, "llm");
            return new SkillAnalysisResponse(
                    coreSkills.isEmpty() ? List.of("基础编程能力") : coreSkills,
                    technicalStrengths.isEmpty() ? List.of("具备基础技术能力") : technicalStrengths,
                    coverageAreas,
                    improvementSuggestions.isEmpty() ? List.of("建议深入某个方向形成技术壁垒") : improvementSuggestions,
                    summary);
        } catch (Exception e) {
            return new SkillAnalysisResponse(
                    List.of("基础编程能力"),
                    List.of("LLM 返回了非结构化结果"),
                    List.of(),
                    List.of("请重新尝试分析"),
                    "LLM 返回解析失败");
        }
    }

    private SkillAnalysisResponse fallbackAnalyzeSkills(String profileSummary, SafetyVerdict verdict) {
        Set<String> keywords = extractKeywords(profileSummary);

        // 从知识库检索项目资料，统计技能频次
        var projectSearchResult = knowledgeBaseService.search("项目 技术栈 技能");
        List<ProjectInfo> projects = buildProjectsFromKnowledgeBase(projectSearchResult.items());

        Map<String, Integer> skillFrequency = new HashMap<>();
        Map<String, List<String>> skillProjects = new HashMap<>();
        for (ProjectInfo project : projects) {
            for (String tech : project.techStack()) {
                String techLower = tech.toLowerCase(Locale.ROOT);
                skillFrequency.merge(techLower, 1, Integer::sum);
                skillProjects.computeIfAbsent(techLower, k -> new ArrayList<>()).add(project.name());
            }
        }

        List<String> coreSkills = new ArrayList<>();
        if (keywords.contains("java") || keywords.contains("spring")) {
            int count = skillFrequency.getOrDefault("spring boot", 0);
            coreSkills.add(String.format("Java 后端开发（精通）— 在 %d 个项目中使用 Spring Boot，擅长快速构建企业级 RESTful API", Math.max(count, 1)));
        }
        if (keywords.contains("vue") || keywords.contains("vue3") || keywords.contains("前端")) {
            List<String> projNames = skillProjects.getOrDefault("vue3", List.of());
            coreSkills.add(String.format("Vue 3 前端开发（熟练）— 在 %s 等项目中实践 Composition API 和响应式设计",
                    projNames.isEmpty() ? "多个" : String.join("、", projNames.subList(0, Math.min(2, projNames.size())))));
        }
        if (keywords.contains("react")) {
            coreSkills.add("React 前端开发（熟练）— 使用 React + TypeScript 构建复杂交互应用");
        }
        if (keywords.contains("安全") || keywords.contains("security") || keywords.contains("rasp")) {
            coreSkills.add("安全开发（熟练）— 有 RASP 运行时防护和漏洞扫描实践经验");
        }
        if (keywords.contains("rag") || keywords.contains("agent") || keywords.contains("llm") || keywords.contains("spring ai")) {
            coreSkills.add("AI 应用开发（熟练）— 有 RAG 知识库和多 Agent 架构的实践经验");
        }
        if (keywords.contains("mysql") || keywords.contains("redis") || keywords.contains("milvus") || keywords.contains("elasticsearch")) {
            coreSkills.add("数据库与存储（熟练）— 掌握 MySQL 索引优化和 Redis 缓存设计，有多种数据库实践经验");
        }
        if (keywords.contains("docker") || keywords.contains("nginx") || keywords.contains("k8s")) {
            coreSkills.add("DevOps 部署（了解）— 掌握 Docker 容器化部署和 Docker Compose 编排");
        }
        if (keywords.contains("微服务") || keywords.contains("spring cloud") || keywords.contains("kafka") || keywords.contains("rabbitmq")) {
            coreSkills.add("微服务架构（了解）— 了解 Spring Cloud 生态和消息队列");
        }
        if (keywords.contains("electron") || keywords.contains("three.js")) {
            coreSkills.add("桌面应用与可视化（熟练）— 有 Electron 桌面应用和 Three.js 3D 可视化经验");
        }
        if (keywords.contains("微信小程序")) {
            coreSkills.add("小程序开发（熟练）— 有微信小程序开发经验，包含自定义组件和本地数据管理");
        }
        if (coreSkills.isEmpty()) {
            coreSkills.add("基础编程能力 — 具备独立完成项目的能力");
        }

        List<String> technicalStrengths = new ArrayList<>();
        if (!projects.isEmpty()) {
            technicalStrengths.add(String.format("全栈项目交付能力 — 有 %d 个独立完成的项目，涵盖后端、前端、桌面应用和小程序", projects.size()));
        }
        if (keywords.contains("spring boot")) {
            technicalStrengths.add("Spring Boot 快速开发 — 掌握自动配置、Starter 机制和 Actuator 监控，能快速构建生产级应用");
        }
        if (keywords.contains("agent") || keywords.contains("rag")) {
            technicalStrengths.add("AI 应用架构设计 — 有多 Agent 任务编排和 RAG 知识库构建的实践经验");
        }
        if (keywords.contains("安全") || keywords.contains("rasp")) {
            technicalStrengths.add("安全防护体系设计 — 有运行时攻击检测与拦截的实践经验");
        }
        if (keywords.contains("mysql") || keywords.contains("redis")) {
            technicalStrengths.add("数据库性能优化 — 通过索引优化和缓存设计提升系统性能");
        }
        if (technicalStrengths.size() < 3) {
            technicalStrengths.add("持续学习能力 — 保持对新技术的探索，在项目中实践前沿技术");
        }

        List<String> coverageAreas = new ArrayList<>();
        if (keywords.contains("后端") || keywords.contains("java")) {
            coverageAreas.add("后端服务开发 — Spring Boot + MyBatis + MySQL，涵盖 RESTful API、数据库设计、缓存优化");
        }
        if (keywords.contains("前端") || keywords.contains("vue") || keywords.contains("react")) {
            coverageAreas.add("前端界面开发 — Vue 3 / React，掌握响应式设计和组件化开发");
        }
        if (keywords.contains("docker") || keywords.contains("k8s")) {
            coverageAreas.add("容器化部署 — Docker + Docker Compose + Nginx，实践应用容器化和反向代理配置");
        }
        if (keywords.contains("安全") || keywords.contains("rasp")) {
            coverageAreas.add("网络安全防护 — RASP 运行时防护、漏洞扫描、安全防御");
        }
        if (keywords.contains("agent") || keywords.contains("spring ai") || keywords.contains("rag")) {
            coverageAreas.add("AI 应用开发 — RAG + 多 Agent 架构，构建知识增强的智能应用");
        }

        List<String> improvementSuggestions = new ArrayList<>();
        if (!keywords.contains("docker")) {
            improvementSuggestions.add("学习 Docker 容器化部署 — 将现有项目容器化，实践 Docker Compose 编排和 CI/CD 流程");
        }
        if (!keywords.contains("微服务") && !keywords.contains("spring cloud")) {
            improvementSuggestions.add("深入微服务架构 — 学习 Spring Cloud 生态，实践服务注册、配置中心、网关等组件");
        }
        if (!keywords.contains("llm") && !keywords.contains("rag") && !keywords.contains("spring ai")) {
            improvementSuggestions.add("探索 AI 应用开发 — 学习 RAG 知识库和 Function Calling，拓展智能应用开发能力");
        }
        if (!keywords.contains("kafka") && !keywords.contains("rabbitmq")) {
            improvementSuggestions.add("掌握消息队列 — 学习 RabbitMQ 或 Kafka，实现异步任务处理和系统解耦");
        }
        if (improvementSuggestions.isEmpty()) {
            improvementSuggestions.add("技术栈深度优化 — 重点掌握性能优化、高可用设计和架构演进");
            improvementSuggestions.add("项目影响力提升 — 为现有项目补充单元测试、API 文档和部署指南");
        }

        String summary = String.format("核心技能 %d 项，技术优势 %d 项，覆盖面 %d 个领域",
                coreSkills.size(), technicalStrengths.size(), coverageAreas.size());

        auditService.record("agent-skill-analysis", null, verdict.sanitizedText(), summary, verdict.reason());
        return new SkillAnalysisResponse(coreSkills, technicalStrengths, coverageAreas, improvementSuggestions, summary);
    }

    public InterviewAnswerResponse generateInterviewAnswer(InterviewAnswerRequest request) {
        String profileSummary = getProfileFromKnowledgeBase();
        String mergedInput = request.question() + "\n" + (request.jobTitle() != null ? request.jobTitle() : "") + "\n" + profileSummary;
        SafetyVerdict verdict = safetyService.inspect(mergedInput);
        if (!verdict.allowed()) {
            auditService.record("agent-blocked", null, verdict.sanitizedText(), verdict.reason(), "injection");
            return new InterviewAnswerResponse(request.question(), "输入包含高风险指令，已拒绝生成", List.of(), List.of());
        }

        // 优先使用 LLM 生成个性化面试回答
        String jobContext = request.jobTitle() != null && !request.jobTitle().isBlank()
                ? "目标岗位：" + request.jobTitle() + "\n\n" : "";
        Optional<String> llmResult = llmService.generate(
                "你是一个经验丰富的面试教练，擅长帮助候选人准备技术面试。" +
                "请根据求职者的个人资料，针对面试问题生成一个高质量、个性化的回答。\n\n" +
                "## 回答结构（STAR法则）\n\n" +
                "### Situation（情境）\n" +
                "- 描述项目背景和业务场景\n" +
                "- 说明技术挑战的来源\n\n" +
                "### Task（任务）\n" +
                "- 明确你的具体职责\n" +
                "- 说明需要解决的核心问题\n\n" +
                "### Action（行动）\n" +
                "- 详细描述技术方案选型过程\n" +
                "- 说明具体实现步骤和技术细节\n" +
                "- 包含架构设计、关键代码、优化策略\n\n" +
                "### Result（结果）\n" +
                "- 量化成果（性能提升百分比、QPS、响应时间等）\n" +
                "- 业务价值（用户体验改善、成本降低等）\n" +
                "- 个人成长和技术沉淀\n\n" +
                "## 回答格式示例\n\n" +
                "```\n" +
                "**项目背景**：[简述项目要解决的问题和业务场景]\n\n" +
                "**我的职责**：[说明你在项目中的具体角色和负责模块]\n\n" +
                "**技术方案**：\n" +
                "1. [方案1：技术选型 + 实现细节]\n" +
                "2. [方案2：技术选型 + 实现细节]\n\n" +
                "**实现效果**：\n" +
                "- [量化指标1：如准确率、响应时间等]\n" +
                "- [量化指标2：如性能提升、用户量等]\n" +
                "```\n\n" +
                "## 输出要求\n\n" +
                "- 全文使用第一人称「我」表述\n" +
                "- 必须基于求职者的真实项目经历，不编造\n" +
                "- answer 控制在 500-800 字，重点突出、逻辑清晰\n" +
                "- 必须包含具体的技术栈名称、实现细节和量化数据\n" +
                "- keyPoints 提炼 4-5 个面试官最关注的核心要点\n" +
                "- tips 给出 3-4 个回答此类问题的实用技巧\n\n" +
                "严格返回 JSON，不要添加其他内容：\n" +
                "```json\n" +
                "{\"answer\": \"完整回答\", \"keyPoints\": [\"核心要点\"], \"tips\": [\"回答技巧\"]}\n" +
                "```",
                jobContext +
                "面试问题：" + request.question() + "\n\n" +
                "求职者资料：\n" + profileSummary
        );

        if (llmResult.isPresent()) {
            return parseInterviewAnswerResponse(request.question(), llmResult.get());
        }

        // LLM 不可用时，降级为模板回答
        return fallbackInterviewAnswer(request, profileSummary, verdict);
    }

    private InterviewAnswerResponse parseInterviewAnswerResponse(String question, String llmOutput) {
        try {
            String json = extractJson(llmOutput);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            String answer = root.has("answer") ? root.get("answer").asText() : "LLM 未返回回答内容";
            List<String> keyPoints = parseJsonArray(root, "keyPoints");
            List<String> tips = parseJsonArray(root, "tips");
            auditService.record("agent-interview", null, question,
                    answer.substring(0, Math.min(100, answer.length())), "llm");
            return new InterviewAnswerResponse(question, answer,
                    keyPoints.isEmpty() ? List.of("清晰的问题分析", "系统的解决思路", "实际经验的支撑") : keyPoints,
                    tips.isEmpty() ? List.of("保持逻辑清晰", "结合具体案例", "展示解决问题的能力") : tips);
        } catch (Exception e) {
            return new InterviewAnswerResponse(question,
                    "LLM 返回了非结构化结果，请重新尝试",
                    List.of("清晰的问题分析", "系统的解决思路", "实际经验的支撑"),
                    List.of("保持逻辑清晰", "结合具体案例", "展示解决问题的能力"));
        }
    }

    private InterviewAnswerResponse fallbackInterviewAnswer(InterviewAnswerRequest request, String profileSummary, SafetyVerdict verdict) {
        // 从知识库检索与问题相关的项目资料
        var searchResult = knowledgeBaseService.search(request.question() + " 项目 经历");
        List<ProjectInfo> projects = buildProjectsFromKnowledgeBase(searchResult.items());

        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append("关于「").append(request.question()).append("」，基于知识库中的资料整理如下：\n\n");

        if (!projects.isEmpty()) {
            answerBuilder.append("---\n\n");
            for (int i = 0; i < Math.min(3, projects.size()); i++) {
                ProjectInfo project = projects.get(i);
                answerBuilder.append("### ").append(i + 1).append(". ").append(project.name()).append("\n\n");
                answerBuilder.append("**项目简介**：").append(project.description()).append("\n\n");
                if (!project.techStack().isEmpty()) {
                    answerBuilder.append("**技术栈**：").append(String.join("、", project.techStack())).append("\n\n");
                }
                if (!project.highlights().isEmpty()) {
                    answerBuilder.append("**项目亮点**：\n");
                    for (String highlight : project.highlights()) {
                        answerBuilder.append("- ").append(highlight).append("\n");
                    }
                    answerBuilder.append("\n");
                }
                answerBuilder.append("---\n\n");
            }
        } else {
            answerBuilder.append("知识库中暂未找到与该问题直接相关的项目资料。\n\n");
            answerBuilder.append("建议：\n");
            answerBuilder.append("- 先导入更多个人项目文档到知识库\n");
            answerBuilder.append("- 尝试用更具体的关键词提问\n");
        }

        answerBuilder.append("\n**提示**：以上回答基于知识库检索结果生成（LLM 未启用）。配置 LLM 后可获得更个性化、更有深度的面试回答。");

        List<String> keyPoints = List.of("基于真实项目经历回答", "包含具体的技术栈和实现细节", "用数据量化成果", "展示解决问题的思路");
        List<String> tips = List.of(
                "用 STAR 法则组织回答：先说情境，再讲任务、行动和结果",
                "强调个人贡献，用数据量化成果",
                "准备 2-3 个不同维度的案例",
                "配置 LLM 后可获得更智能的个性化回答"
        );

        String answer = answerBuilder.toString();
        auditService.record("agent-interview", null, request.question(), answer.substring(0, Math.min(100, answer.length())), verdict.reason());
        return new InterviewAnswerResponse(request.question(), answer, keyPoints, tips);
    }

    private String getProfileFromKnowledgeBase() {
        var searchResult = knowledgeBaseService.search("简历 个人简介 技能 项目经验 教育背景");
        if (searchResult.items().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var item : searchResult.items()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(item.content());
        }
        return sb.toString();
    }

    private Set<String> extractKeywords(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> keywords = new HashSet<>();
        String[] candidates = {
                "java", "spring", "spring boot", "spring cloud", "spring ai", "vue", "vue3",
                "typescript", "mysql", "redis", "milvus", "rag", "agent", "security", "安全",
                "后端", "前端", "微服务", "docker", "nginx", "llm", "embedding",
                "kafka", "nacos", "mybatis", "mybatis-plus", "quartz", "nmap",
                "rabbitmq", "elasticsearch", "bytebuddy", "rasp",
                "react", "matter.js", "three.js", "electron", "微信小程序",
                "owasp", "jwt", "多线程", "jvm", "k8s",
                "基金", "漏洞扫描", "车库管理", "交易系统", "博客", "记账"
        };
        for (String candidate : candidates) {
            if (normalized.contains(candidate)) {
                keywords.add(candidate);
            }
        }
        return keywords;
    }

    /**
     * 从 LLM 输出中提取 JSON 字符串，兼容 markdown 代码块包裹的情况
     */
    private String extractJson(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        // 处理 ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        // 直接找 JSON 对象
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * 从 JsonNode 中解析字符串数组字段
     */
    private List<String> parseJsonArray(com.fasterxml.jackson.databind.JsonNode root, String field) {
        List<String> result = new ArrayList<>();
        if (root.has(field) && root.get(field).isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode node : root.get(field)) {
                result.add(node.asText());
            }
        }
        return result;
    }

    /**
     * 生成计算机编程相关的话语（名人名言、技术洞察、幽默笑话）
     */
    public TechQuoteResponse generateTechQuote() {
        // 优先使用 LLM 生成
        Optional<String> llmResult = llmService.generate(
                "你是一个计算机科学专家和技术作家，擅长生成关于计算机编程的精彩话语。",
                "请随机选择一种话语类型，生成一条关于计算机编程的话语：\n\n" +
                "1. 名人名言 - 计算机科学领域的名人名言，如 Linus Torvalds、Donald Knuth、Alan Turing、Steve Jobs 等\n" +
                "2. 技术洞察 - 关于编程、软件工程、架构设计的深刻见解\n" +
                "3. 幽默笑话 - 程序员才懂的技术幽默，如关于 bug、调试、需求变更的笑话\n" +
                "4. 编程哲学 - 关于代码质量、简洁设计、工程思维的哲学思考\n\n" +
                "要求：\n" +
                "- 话语要简洁有力，控制在 50 字以内\n" +
                "- 如果是名人名言，必须准确，不能编造\n" +
                "- 如果是幽默笑话，要能让程序员会心一笑\n\n" +
                "请严格使用以下JSON格式输出：\n" +
                "{\"quote\": \"话语内容\", \"author\": \"作者或来源\", \"category\": \"类别\"}\n\n" +
                "category 只能是 名人名言、技术洞察、幽默笑话、编程哲学 之一，只输出JSON。"
        );

        if (llmResult.isPresent()) {
            return parseTechQuoteResponse(llmResult.get());
        }

        // LLM 不可用时，返回预设话语
        return getFallbackTechQuote();
    }

    private TechQuoteResponse parseTechQuoteResponse(String llmOutput) {
        try {
            String json = extractJson(llmOutput);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            String quote = root.has("quote") ? root.get("quote").asText() : "代码是写给人看的，顺便让机器执行。";
            String author = root.has("author") ? root.get("author").asText() : "Harold Abelson";
            String category = root.has("category") ? root.get("category").asText() : "编程哲学";
            return new TechQuoteResponse(quote, author, category);
        } catch (Exception e) {
            return getFallbackTechQuote();
        }
    }

    private TechQuoteResponse getFallbackTechQuote() {
        List<TechQuoteResponse> quotes = List.of(
                new TechQuoteResponse("程序必须是写给人阅读的，顺便让机器执行。", "Harold Abelson", "名人名言"),
                new TechQuoteResponse("过早优化是万恶之源。", "Donald Knuth", "名人名言"),
                new TechQuoteResponse("世界上只有两种语言：被人骂的和没人用的。", "Bjarne Stroustrup", "幽默笑话"),
                new TechQuoteResponse("调试比写代码难两倍。所以如果你写代码的时候尽可能聪明，那你调试的时候就该绝望了。", "Brian Kernighan", "幽默笑话"),
                new TechQuoteResponse("简单是可靠的前提。", "Edsger W. Dijkstra", "编程哲学"),
                new TechQuoteResponse("没有银弹。", "Fred Brooks", "技术洞察"),
                new TechQuoteResponse("首先，要求计算机科学家解决一个简单的问题。然后，他们会发现真正的问题是什么。", "Alan Kay", "技术洞察"),
                new TechQuoteResponse("任何足够先进的 Bug，都和魔法无异。", "佚名", "幽默笑话"),
                new TechQuoteResponse("代码写得像诗一样，读起来像散文一样。", "Robert C. Martin", "编程哲学"),
                new TechQuoteResponse("需求变更不是问题，问题是需求变更的时候你还没写完。", "佚名", "幽默笑话")
        );
        int index = (int) (System.currentTimeMillis() % quotes.size());
        return quotes.get(index);
    }
}

