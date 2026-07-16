package com.resumeagent.service;

import com.resumeagent.dto.*;
import com.resumeagent.dto.ProjectQueryResponse.ProjectInfo;
import com.resumeagent.model.SafetyVerdict;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

    private static final Map<String, ProjectInfo> PROJECT_DATABASE = new HashMap<>();

    static {
        // 崔哲航的真实项目
        PROJECT_DATABASE.put("智能车库管理系统", new ProjectInfo(
                "智能车库管理系统",
                "基于 Spring Boot + MyBatis-Plus + MySQL 开发的一体化车库管理平台，已获国家软件著作权",
                Arrays.asList("Spring Boot", "MyBatis-Plus", "MySQL", "Docker", "Vue"),
                "独立开发",
                Arrays.asList("多策略计费规则引擎设计", "数据库建模与物理表设计", "Docker 容器化部署")
        ));
        PROJECT_DATABASE.put("FundPilot", new ProjectInfo(
                "FundPilot-AI 基金智能分析与诊断系统",
                "基于 Spring Boot + Vue3 + Spring AI 的多 Agent 基金分析平台，支持意图路由、智能诊断和报告生成",
                Arrays.asList("Spring Boot", "Vue3", "Spring AI", "Redis", "MySQL", "Docker", "MyBatis-Plus"),
                "独立开发 & 架构设计",
                Arrays.asList("多专家 Agent 任务状态机编排", "Tool Calling 函数深度接入", "Redis 会话上下文管理")
        ));
        PROJECT_DATABASE.put("Sec-sonar", new ProjectInfo(
                "Sec-sonar 分布式 Web 安全态势感知与 RASP 自防御平台",
                "结合主动漏洞扫描与被动 RASP 运行时防护，构建完整 Web 安全防御体系",
                Arrays.asList("Spring Boot", "Python", "ByteBuddy", "Vue3", "RabbitMQ", "Elasticsearch"),
                "独立开发",
                Arrays.asList("RASP 探针四层防御体系设计", "Python 扫描引擎漏洞检测", "赛博朋克安全态势大屏")
        ));
        PROJECT_DATABASE.put("ResumeAgent", new ProjectInfo(
                "ResumeAgent 简历智能助手",
                "基于 RAG 的个人简历 AI 助手系统，支持智能问答、岗位匹配和面试准备",
                Arrays.asList("Spring Boot", "Vue3", "RAG", "Agent", "OpenAI API"),
                "全栈开发",
                Arrays.asList("RAG 检索链路设计", "Agent 工具调用实现", "安全防护机制构建")
        ));
        PROJECT_DATABASE.put("MentalBlog", new ProjectInfo(
                "MentalBlog 个人博客系统",
                "赛博朋克风格的前后端分离博客系统，支持文章管理、评论、图库、深色模式",
                Arrays.asList("Spring Boot", "MyBatis-Plus", "Vue3", "TypeScript", "Element Plus", "ECharts", "MySQL", "Redis"),
                "全栈开发",
                Arrays.asList("Spring Security + JWT 认证体系", "Markdown 编辑器集成", "Glassmorphism 毛玻璃 UI 设计")
        ));
        PROJECT_DATABASE.put("GazeForge", new ProjectInfo(
                "GazeForge 视觉注意力追踪训练平台",
                "基于 React + TypeScript 的多目标追踪（MOT）训练应用，含 66 关闯关模式和专注力测试",
                Arrays.asList("React", "TypeScript", "Matter.js", "Zustand", "Tailwind CSS", "IndexedDB"),
                "前端开发",
                Arrays.asList("Matter.js 2D 物理引擎封装", "成就系统与关卡生成算法", "Web Audio API 音效合成")
        ));
        PROJECT_DATABASE.put("PUBG武器转盘", new ProjectInfo(
                "PUBG 武器幸运转盘",
                "基于 Spring Boot + Canvas 的武器随机抽取 Web 应用，已部署上线",
                Arrays.asList("Spring Boot", "Spring Data JPA", "MySQL", "Canvas API", "HTML/CSS/JS"),
                "全栈开发",
                Arrays.asList("Canvas 转盘动画实现", "武器权重配置系统", "云服务器部署")
        ));
        PROJECT_DATABASE.put("ShotNote", new ProjectInfo(
                "ShotNote 截图标注工具",
                "基于 Electron 的 Windows 截图与标注工具，支持 OCR 识别和多屏幕截图",
                Arrays.asList("Electron", "tesseract.js", "screenshot-desktop", "HTML/CSS/JS"),
                "桌面应用开发",
                Arrays.asList("多显示器截图覆盖层", "OCR 文字识别集成", "系统托盘与全局快捷键")
        ));
        PROJECT_DATABASE.put("PocketLedger", new ProjectInfo(
                "PocketLedger 记账小程序",
                "轻量级微信小程序记账应用，本地优先，支持订阅管理、多币种换算和退款报销",
                Arrays.asList("微信小程序", "JavaScript", "WXML", "WXSS"),
                "小程序开发",
                Arrays.asList("自定义数字键盘交互设计", "多币种汇率获取与缓存", "数据导出 CSV/JSON")
        ));
        PROJECT_DATABASE.put("3D粒子系统", new ProjectInfo(
                "3D 粒子系统",
                "基于 Three.js + MediaPipe Hands 的交互式 3D 粒子手势控制应用",
                Arrays.asList("Three.js", "MediaPipe Hands", "GLSL Shader", "HTML/CSS/JS"),
                "前端开发",
                Arrays.asList("自定义 ShaderMaterial 粒子着色", "MediaPipe 手部关键点检测", "5 种参数化粒子模板")
        ));
        PROJECT_DATABASE.put("图书管理系统", new ProjectInfo(
                "图书信息管理系统",
                "基于 Spring Boot + MyBatis + Thymeleaf 的图书 CRUD 系统",
                Arrays.asList("Spring Boot", "MyBatis", "Thymeleaf", "MySQL"),
                "后端开发",
                Arrays.asList("角色权限拦截器设计", "MyBatis XML 映射", "封面图片上传")
        ));
    }

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
                "\"具备多 Agent 架构设计能力 — 在 FundPilot-AI 中实现了两阶段意图路由和 8 个 Tool Calling 函数，支持分析、对比、推荐、诊断 4个专家 Agent 协作\"\n\n" +
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

        // 分析与岗位匹配的具体项目
        List<ProjectInfo> matchedProjects = new ArrayList<>();
        for (ProjectInfo project : PROJECT_DATABASE.values()) {
            Set<String> projectTechs = new HashSet<>();
            for (String tech : project.techStack()) {
                projectTechs.add(tech.toLowerCase(Locale.ROOT));
            }
            // 检查项目技术栈与岗位关键词的重合度
            Set<String> overlap = new HashSet<>(projectTechs);
            overlap.retainAll(jobKeywords);
            if (!overlap.isEmpty()) {
                matchedProjects.add(project);
            }
        }

        List<String> strengths = new ArrayList<>();
        // 基于项目生成具体优势
        if (!matchedProjects.isEmpty()) {
            for (ProjectInfo project : matchedProjects.subList(0, Math.min(3, matchedProjects.size()))) {
                String techStack = String.join("、", project.techStack().subList(0, Math.min(3, project.techStack().size())));
                strengths.add(String.format("具备 %s 实战经验 — 在 %s 中负责 %s，%s",
                        techStack, project.name(), String.join("、", project.highlights().subList(0, Math.min(2, project.highlights().size()))),
                        project.role()));
            }
        }
        // 补充通用优势
        if (matchedKeywords.contains("spring") || matchedKeywords.contains("java")) {
            strengths.add("掌握 Spring Boot 生态，具备快速构建企业级后端服务的能力，有 6 个项目使用该技术栈");
        }
        if (matchedKeywords.contains("安全") || matchedKeywords.contains("security")) {
            strengths.add("具备安全开发能力 — 在 Sec-sonar 中实现 RASP 四层防御体系，包含 34 个危险命令检测和 15 个反弹 Shell 正则");
        }
        if (matchedKeywords.contains("rag") || matchedKeywords.contains("agent")) {
            strengths.add("掌握 AI 应用开发 — 在 FundPilot-AI 中实现多 Agent 架构，在 ResumeAgent 中实现 RAG 知识库问答");
        }
        if (strengths.isEmpty()) {
            strengths.add("具备全栈开发潜力 — 独立完成过 11 个项目，涵盖后端、前端、桌面应用和小程序");
        }

        List<String> gaps = new ArrayList<>();
        if (!missingKeywords.isEmpty()) {
            // 将缺失关键词分类并给出具体建议
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
        // 基于已有项目给出具体建议
        if (!matchedProjects.isEmpty()) {
            suggestions.add(String.format("深化 %s 项目的技术深度，准备架构设计、性能优化、问题排查等细节问题",
                    matchedProjects.get(0).name()));
        }
        if (missingKeywords.contains("微服务") || missingKeywords.contains("spring cloud")) {
            suggestions.add("建议在现有 Spring Boot 项目基础上引入 Spring Cloud，实践服务注册、配置中心、网关等微服务组件");
        }
        if (missingKeywords.contains("docker") || missingKeywords.contains("k8s")) {
            suggestions.add("将现有项目容器化部署，实践 Docker + Docker Compose，了解 CI/CD 流程");
        }
        if (missingKeywords.contains("kafka") || missingKeywords.contains("rabbitmq")) {
            suggestions.add("在 FundPilot-AI 或 Sec-sonar 中引入消息队列，实现异步任务处理和系统解耦");
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

        String normalizedQuery = request.query().toLowerCase(Locale.ROOT);
        List<ProjectInfo> matchedProjects = new ArrayList<>();

        // 收集所有查询关键词：query 本身 + 显式 techStack
        Set<String> queryTokens = new HashSet<>();
        queryTokens.addAll(Arrays.asList(normalizedQuery.split("[,，\\s]+")));
        if (request.techStack() != null && !request.techStack().isBlank()) {
            queryTokens.addAll(Arrays.asList(request.techStack().toLowerCase(Locale.ROOT).split("[,，\\s]+")));
        }

        for (Map.Entry<String, ProjectInfo> entry : PROJECT_DATABASE.entrySet()) {
            String projectName = entry.getKey().toLowerCase(Locale.ROOT);
            ProjectInfo project = entry.getValue();

            // 1. 项目名匹配（包含关系）
            boolean nameMatch = projectName.contains(normalizedQuery) || normalizedQuery.contains(projectName);

            // 2. 技术栈模糊匹配：查询词与项目技术栈逐项比较
            //    支持 "spring boot" 匹配 "Spring Boot 3"，"mysql" 匹配 "MySQL" 等
            boolean techMatch = false;
            Set<String> projectTechsLower = new HashSet<>();
            for (String tech : project.techStack()) {
                projectTechsLower.add(tech.toLowerCase(Locale.ROOT));
            }

            for (String token : queryTokens) {
                if (token.isBlank()) continue;
                // 精确匹配
                if (projectTechsLower.contains(token)) {
                    techMatch = true;
                    break;
                }
                // 子串匹配：查询词是项目技术的子串，或项目技术是查询词的子串
                for (String projectTech : projectTechsLower) {
                    if (projectTech.contains(token) || token.contains(projectTech)) {
                        techMatch = true;
                        break;
                    }
                }
                if (techMatch) break;
            }

            // 3. 项目描述关键词匹配
            boolean descMatch = false;
            String descLower = project.description().toLowerCase(Locale.ROOT);
            if (!nameMatch && !techMatch) {
                for (String token : queryTokens) {
                    if (token.length() > 1 && descLower.contains(token)) {
                        descMatch = true;
                        break;
                    }
                }
            }

            if (nameMatch || techMatch || descMatch) {
                matchedProjects.add(project);
            }
        }

        String summary = matchedProjects.isEmpty()
                ? "未找到匹配的项目，建议使用项目名称或技术栈关键词查询（如：Spring Boot、Vue、MySQL）"
                : String.format("找到 %d 个匹配项目", matchedProjects.size());

        auditService.record("agent-project-query", null, request.query(), summary, verdict.reason());
        return new ProjectQueryResponse(request.query(), matchedProjects, summary);
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
                "\"Spring Boot（精通）— 在 6 个项目中使用，擅长快速构建 RESTful API 和微服务架构\"\n\n" +
                "### 2. 技术优势 (technicalStrengths)\n" +
                "每个优势必须包含：\n" +
                "- 具体的技术能力描述\n" +
                "- 对应的项目案例和实现细节\n" +
                "- 可验证的成果指标\n\n" +
                "**示例格式**：\n" +
                "\"多 Agent 架构设计能力 — 在 FundPilot-AI 中设计了两阶段意图路由（关键词正则 + LLM 分类），配合 8 个 Tool Calling 函数实现 4个专家 Agent 协作\"\n\n" +
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

        // 统计技能在项目中的使用频次
        Map<String, Integer> skillFrequency = new HashMap<>();
        Map<String, List<String>> skillProjects = new HashMap<>();
        for (ProjectInfo project : PROJECT_DATABASE.values()) {
            for (String tech : project.techStack()) {
                String techLower = tech.toLowerCase(Locale.ROOT);
                skillFrequency.merge(techLower, 1, Integer::sum);
                skillProjects.computeIfAbsent(techLower, k -> new ArrayList<>()).add(project.name());
            }
        }

        List<String> coreSkills = new ArrayList<>();
        if (keywords.contains("java") || keywords.contains("spring")) {
            int count = skillFrequency.getOrDefault("spring boot", 0);
            coreSkills.add(String.format("Java 后端开发（精通）— 在 %d 个项目中使用 Spring Boot，擅长快速构建企业级 RESTful API", count));
        }
        if (keywords.contains("vue") || keywords.contains("vue3") || keywords.contains("前端")) {
            List<String> projects = skillProjects.getOrDefault("vue3", List.of());
            coreSkills.add(String.format("Vue 3 前端开发（熟练）— 在 %s 等项目中实践 Composition API 和响应式设计",
                    projects.isEmpty() ? "多个" : String.join("、", projects.subList(0, Math.min(2, projects.size())))));
        }
        if (keywords.contains("react")) {
            coreSkills.add("React 前端开发（熟练）— 在 GazeForge 中使用 React 19 + TypeScript + Zustand 构建复杂交互应用");
        }
        if (keywords.contains("安全") || keywords.contains("security") || keywords.contains("rasp")) {
            coreSkills.add("安全开发（熟练）— 在 Sec-sonar 中实现 RASP 四层防御体系，包含 34 个危险命令检测和 15 个反弹 Shell 正则");
        }
        if (keywords.contains("rag") || keywords.contains("agent") || keywords.contains("llm") || keywords.contains("spring ai")) {
            coreSkills.add("AI 应用开发（熟练）— 在 FundPilot-AI 中实现多 Agent 架构，在 ResumeAgent 中实现 RAG 知识库问答");
        }
        if (keywords.contains("mysql") || keywords.contains("redis") || keywords.contains("milvus") || keywords.contains("elasticsearch")) {
            coreSkills.add("数据库与存储（熟练）— 掌握 MySQL 索引优化（性能提升 40%）和 Redis 缓存设计，有 Elasticsearch 实践经验");
        }
        if (keywords.contains("docker") || keywords.contains("nginx") || keywords.contains("k8s")) {
            coreSkills.add("DevOps 部署（了解）— 掌握 Docker 容器化部署和 Docker Compose 编排，有 Nginx 反向代理配置经验");
        }
        if (keywords.contains("微服务") || keywords.contains("spring cloud") || keywords.contains("kafka") || keywords.contains("rabbitmq")) {
            coreSkills.add("微服务架构（了解）— 了解 Spring Cloud 生态，有 RabbitMQ 消息队列实践经验");
        }
        if (keywords.contains("electron") || keywords.contains("three.js")) {
            coreSkills.add("桌面应用与可视化（熟练）— 在 ShotNote 中实现 Electron 桌面应用，在 3D 粒子系统中实践 Three.js + GLSL Shader");
        }
        if (keywords.contains("微信小程序")) {
            coreSkills.add("小程序开发（熟练）— 在 PocketLedger 中实现微信小程序，包含自定义组件和本地数据管理");
        }
        if (coreSkills.isEmpty()) {
            coreSkills.add("基础编程能力 — 具备独立完成简单项目的能力");
        }

        List<String> technicalStrengths = new ArrayList<>();
        technicalStrengths.add("全栈项目交付能力 — 独立完成 11 个项目，涵盖 Java 后端、Vue/React 前端、Electron 桌面应用、微信小程序和 3D 可视化");
        if (keywords.contains("spring boot")) {
            technicalStrengths.add("Spring Boot 快速开发 — 掌握自动配置、Starter 机制和 Actuator 监控，能快速构建生产级应用");
        }
        if (keywords.contains("agent")) {
            technicalStrengths.add("多 Agent 架构设计 — 在 FundPilot-AI 中实现两阶段意图路由（关键词正则 + LLM 分类）和 8 个 Tool Calling 函数");
        }
        if (keywords.contains("安全") || keywords.contains("rasp")) {
            technicalStrengths.add("安全防护体系设计 — 在 Sec-sonar 中使用 ByteBuddy 实现 RASP 探针，设计四层防御体系（异常保护→输入校验→命令重建→上下文分析）");
        }
        if (keywords.contains("spring ai") || keywords.contains("rag")) {
            technicalStrengths.add("RAG 知识库构建 — 在 ResumeAgent 中实现基于向量检索的知识库，支持文档导入、分块、嵌入和语义搜索");
        }
        if (keywords.contains("mysql") || keywords.contains("redis")) {
            technicalStrengths.add("数据库性能优化 — 通过慢查询分析和索引优化将 MySQL 查询性能提升 40%，设计 Redis 缓存策略降低数据库压力");
        }
        if (technicalStrengths.size() < 3) {
            technicalStrengths.add("持续学习能力 — 保持对新技术的探索，在项目中实践 Spring AI、Three.js、MediaPipe 等前沿技术");
        }

        List<String> coverageAreas = new ArrayList<>();
        if (keywords.contains("后端") || keywords.contains("java")) {
            coverageAreas.add("后端服务开发 — Spring Boot + MyBatis-Plus + MySQL，涵盖 RESTful API、数据库设计、缓存优化");
        }
        if (keywords.contains("前端") || keywords.contains("vue") || keywords.contains("react")) {
            coverageAreas.add("前端界面开发 — Vue 3 Composition API + React 19，掌握响应式设计和组件化开发");
        }
        if (keywords.contains("docker") || keywords.contains("k8s")) {
            coverageAreas.add("容器化部署 — Docker + Docker Compose + Nginx，实践应用容器化和反向代理配置");
        }
        if (keywords.contains("微服务") || keywords.contains("spring cloud")) {
            coverageAreas.add("微服务架构 — Spring Cloud 生态，了解服务注册、配置中心、网关等核心组件");
        }
        if (keywords.contains("安全") || keywords.contains("rasp")) {
            coverageAreas.add("网络安全防护 — RASP 运行时防护、漏洞扫描、Prompt Injection 防御、PII 脱敏");
        }
        if (keywords.contains("agent") || keywords.contains("spring ai") || keywords.contains("rag")) {
            coverageAreas.add("AI 应用开发 — Spring AI + RAG + 多 Agent 架构，构建知识增强的智能应用");
        }
        if (keywords.contains("electron") || keywords.contains("微信小程序")) {
            coverageAreas.add("跨平台应用开发 — Electron 桌面应用 + 微信小程序，覆盖桌面端和移动端");
        }

        List<String> improvementSuggestions = new ArrayList<>();
        if (!keywords.contains("docker")) {
            improvementSuggestions.add("学习 Docker 容器化部署 — 将现有项目（如 ResumeAgent）容器化，实践 Docker Compose 编排和 CI/CD 流程");
        }
        if (!keywords.contains("微服务") && !keywords.contains("spring cloud")) {
            improvementSuggestions.add("深入微服务架构 — 在 FundPilot-AI 基础上引入 Spring Cloud Alibaba，实践 Nacos 注册中心和 Sentinel 限流");
        }
        if (!keywords.contains("llm") && !keywords.contains("rag") && !keywords.contains("spring ai")) {
            improvementSuggestions.add("探索 AI 应用开发 — 学习 Spring AI 框架，实践 RAG 知识库和 Function Calling，拓展智能应用开发能力");
        }
        if (!keywords.contains("kafka") && !keywords.contains("rabbitmq")) {
            improvementSuggestions.add("掌握消息队列 — 在 Sec-sonar 中引入 RabbitMQ，实现异步漏洞扫描任务和告警通知");
        }
        if (!keywords.contains("安全")) {
            improvementSuggestions.add("增强安全意识 — 学习 OWASP Top 10，在现有项目中实践 SQL 注入防护、XSS 过滤和 CSRF 防御");
        }
        if (improvementSuggestions.isEmpty()) {
            improvementSuggestions.add("技术栈深度优化 — 建议在 Spring Boot + Vue 3 方向深入，重点掌握性能优化、高可用设计和架构演进");
            improvementSuggestions.add("项目影响力提升 — 为现有项目补充单元测试、API 文档和部署指南，提升开源项目质量");
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
                "## 回答示例格式\n\n" +
                "```\n" +
                "**项目背景**：在 FundPilot-AI 基金分析平台中，需要支持多种用户意图（查询、对比、推荐、诊断），传统的单 Agent 架构难以处理复杂逻辑。\n\n" +
                "**我的职责**：作为项目独立开发者，负责整体架构设计和核心功能实现。\n\n" +
                "**技术方案**：\n" +
                "1. 设计两阶段意图路由：第一层用关键词正则快速匹配，第二层用 LLM 进行语义分类\n" +
                "2. 实现 8 个 Tool Calling 函数，封装基金数据查询、对比分析等原子能力\n" +
                "3. 构建任务状态机，支持多步骤任务编排和错误恢复\n\n" +
                "**实现效果**：\n" +
                "- 意图识别准确率达到 95%+\n" +
                "- 支持 4个专家 Agent 并发协作\n" +
                "- 复杂查询响应时间控制在 3 秒内\n" +
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
        String question = request.question().toLowerCase(Locale.ROOT);
        Set<String> profileKeywords = extractKeywords(profileSummary);

        String answer;
        List<String> keyPoints;
        List<String> tips;

        if (question.contains("项目") && (question.contains("难点") || question.contains("挑战"))) {
            answer = "**项目背景**\n\n" +
                    "在我的项目经历中，遇到的主要技术难点包括：\n\n" +
                    "---\n\n" +
                    "### 1. 多 Agent 架构设计（FundPilot-AI）\n\n" +
                    "**Situation（情境）**：基金分析平台需要支持多种用户意图（查询、对比、推荐、诊断），传统的单 Agent 架构难以处理复杂逻辑。\n\n" +
                    "**Task（任务）**：作为项目独立开发者，需要设计一个可扩展的多 Agent 协作架构。\n\n" +
                    "**Action（行动）**：\n" +
                    "- 设计两阶段意图路由：第一层用关键词正则快速匹配，第二层用 LLM 进行语义分类\n" +
                    "- 实现 8 个 Tool Calling 函数，封装基金数据查询、对比分析等原子能力\n" +
                    "- 构建任务状态机，支持多步骤任务编排和错误恢复\n\n" +
                    "**Result（结果）**：\n" +
                    "- 意图识别准确率达到 95%+\n" +
                    "- 支持 4个专家 Agent 并发协作\n" +
                    "- 复杂查询响应时间控制在 3 秒内\n\n" +
                    "---\n\n" +
                    "### 2. RASP 运行时防护（Sec-sonar）\n\n" +
                    "**Situation（情境）**：Web 应用面临各种运行时攻击，传统 WAF 难以防御内存马、命令注入等高级威胁。\n\n" +
                    "**Task（任务）**：设计一个能在运行时拦截危险操作的自防御系统。\n\n" +
                    "**Action（行动）**：\n" +
                    "- 使用 ByteBuddy 拦截 ProcessBuilder.start()，实现无侵入式 Hook\n" +
                    "- 设计四层防御体系：全局异常保护 → 输入校验 → 命令重建 → 上下文关联分析\n" +
                    "- 内置 34 个危险命令关键词和 15 个反弹 Shell 正则模式\n\n" +
                    "**Result（结果）**：\n" +
                    "- 攻击拦截率达到 92%\n" +
                    "- 误报率控制在 5% 以内\n" +
                    "- 系统性能开销小于 3%\n\n" +
                    "---\n\n" +
                    "### 3. 交易系统高可用（实习）\n\n" +
                    "**Situation（情境）**：实习期间负责的交易系统需要保证 99.9% 成功率，但原系统响应慢、吞吐量低。\n\n" +
                    "**Task（任务）**：优化交易系统性能，提升用户体验和系统稳定性。\n\n" +
                    "**Action（行动）**：\n" +
                    "- 优化支付网关架构，引入连接池和异步处理\n" +
                    "- 通过慢查询分析和索引优化，重构高频查询 SQL\n" +
                    "- 实施代码审计和安全加固，包括 SQL 注入防护、XSS 过滤\n\n" +
                    "**Result（结果）**：\n" +
                    "- 支付网关响应时间从 500ms 降至 200ms\n" +
                    "- MySQL 查询性能提升 40%\n" +
                    "- 周交易量从 1 万单提升至 3 万单\n" +
                    "- 攻击拦截率达 92%";
            keyPoints = List.of("具体的技术难点描述", "解决方案和架构设计思路", "量化的成果指标", "个人贡献和成长");
            tips = List.of("用 STAR 法则组织回答，先说情境再讲行动和结果", "强调个人贡献，用数据量化成果", "准备 2-3 个不同维度的案例");
        } else if (question.contains("技术") && (question.contains("优势") || question.contains("擅长"))) {
            answer = "我的技术优势主要体现在以下几个方面：\n\n" +
                    "---\n\n" +
                    "### 1. 全栈项目交付能力\n\n" +
                    "**具体表现**：独立完成过 11 个项目，涵盖：\n" +
                    "- **后端**：Spring Boot + MyBatis-Plus（6 个项目）\n" +
                    "- **前端**：Vue 3 Composition API + React 19\n" +
                    "- **桌面应用**：Electron（ShotNote）\n" +
                    "- **小程序**：微信原生（PocketLedger）\n" +
                    "- **3D 可视化**：Three.js + GLSL Shader\n\n" +
                    "**项目案例**：\n" +
                    "- FundPilot-AI：多 Agent 架构 + Tool Calling + RAG\n" +
                    "- Sec-sonar：RASP 四层防御体系 + 漏洞扫描引擎\n" +
                    "- ResumeAgent：RAG 知识库问答 + Agent 分析\n\n" +
                    "---\n\n" +
                    "### 2. AI 应用开发能力\n\n" +
                    "**技术栈**：Spring AI + RAG + 多 Agent 架构\n\n" +
                    "**核心能力**：\n" +
                    "- 多 Agent 任务编排（意图路由 + 状态机）\n" +
                    "- Tool Calling 函数设计与实现\n" +
                    "- RAG 知识库构建（向量检索 + 语义搜索）\n\n" +
                    "**项目验证**：在 FundPilot-AI 中实现 4个专家 Agent 协作，意图识别准确率 95%+\n\n" +
                    "---\n\n" +
                    "### 3. 安全开发意识\n\n" +
                    "**技术栈**：RASP + ByteBuddy + Prompt Injection 防御\n\n" +
                    "**核心能力**：\n" +
                    "- 运行时攻击检测与拦截（92% 拦截率）\n" +
                    "- Prompt Injection 攻防对抗\n" +
                    "- PII 敏感数据脱敏\n\n" +
                    "**项目验证**：在 Sec-sonar 中实现四层防御体系，内置 34 个危险命令检测\n\n" +
                    "---\n\n" +
                    "### 4. 工程化实践能力\n\n" +
                    "**技术栈**：Docker + MySQL + Redis + 消息队列\n\n" +
                    "**核心能力**：\n" +
                    "- 容器化部署（Docker + Docker Compose）\n" +
                    "- 数据库性能优化（40% 提升）\n" +
                    "- 缓存设计（Redis 热点数据缓存）\n" +
                    "- 异步解耦（RabbitMQ 消息队列）";
            keyPoints = List.of("与岗位匹配的核心技能", "具体项目中的应用案例和量化成果", "持续学习和探索新技术的能力");
            tips = List.of("结合具体项目经验说明，避免空泛描述", "用数据和成果支撑每个优势", "展示技术深度和广度的平衡");
        } else if (question.contains("职业") || question.contains("规划") || question.contains("发展")) {
            answer = "我的职业规划分为三个阶段：\n\n" +
                    "---\n\n" +
                    "### 短期（1-2年）：技术深耕期\n\n" +
                    "**目标**：在后端开发岗位深耕技术，成为团队核心开发者\n\n" +
                    "**具体规划**：\n" +
                    "- 深入掌握微服务架构、分布式系统设计和性能优化\n" +
                    "- 将 Spring AI 和 RAG 技术落地到生产环境\n" +
                    "- 在现有项目基础上引入 Spring Cloud，实践服务治理\n\n" +
                    "**已有基础**：已独立完成 11 个项目，掌握 Spring Boot + Vue 3 全栈开发\n\n" +
                    "---\n\n" +
                    "### 中期（3-5年）：技术专家期\n\n" +
                    "**目标**：向技术专家或架构师方向发展\n\n" +
                    "**具体规划**：\n" +
                    "- 具备独立设计复杂系统的能力\n" +
                    "- 重点关注 AI 与后端的融合方向\n" +
                    "- 成为团队中 AI 应用开发的核心力量\n\n" +
                    "**预期成果**：主导 2-3 个核心系统架构设计，积累高并发、高可用经验\n\n" +
                    "---\n\n" +
                    "### 长期（5年以上）：技术管理期\n\n" +
                    "**目标**：成为技术负责人\n\n" +
                    "**具体规划**：\n" +
                    "- 带领团队完成有影响力的产品\n" +
                    "- 在更大规模的系统中锻炼架构设计和团队管理能力\n" +
                    "- 将技术积累转化为业务价值\n\n" +
                    "**核心优势**：从我的项目轨迹可以看到，我一直在 Java 后端、AI 应用和安全防护三个方向同步积累，这种 T 型技术结构有助于未来的技术决策。";
            keyPoints = List.of("清晰的阶段目标", "与岗位技术方向的契合度", "持续成长的决心和已有积累");
            tips = List.of("结合公司业务方向和技术栈", "展示已有的成长轨迹（项目数量和技术栈演进）", "表达长期投入的意愿");
        } else if (question.contains("实习") || question.contains("工作") || question.contains("经历")) {
            answer = "我在昆明逐远浪商贸进行了后端开发实习，以下是详细的实习经历和收获：\n\n" +
                    "---\n\n" +
                    "### 1. 交易系统优化\n\n" +
                    "**Situation（情境）**：原交易系统响应慢、吞吐量低，用户体验差。\n\n" +
                    "**Task（任务）**：优化交易系统性能，提升用户体验。\n\n" +
                    "**Action（行动）**：\n" +
                    "- 分析系统瓶颈，发现支付网关和数据库查询是主要性能瓶颈\n" +
                    "- 优化支付网关架构，引入连接池和异步处理\n" +
                    "- 通过慢查询分析和索引优化，重构高频查询 SQL\n\n" +
                    "**Result（结果）**：\n" +
                    "- 交易成功率提升至 99.9%\n" +
                    "- 周交易量从 1 万单增长至 3 万单\n" +
                    "- 支付网关响应时间从 500ms 降至 200ms\n\n" +
                    "---\n\n" +
                    "### 2. 数据库性能调优\n\n" +
                    "**Situation（情境）**：数据库查询慢，影响系统整体性能。\n\n" +
                    "**Task（任务）**：优化数据库查询性能。\n\n" +
                    "**Action（行动）**：\n" +
                    "- 使用 EXPLAIN 分析慢查询 SQL\n" +
                    "- 优化索引策略，添加复合索引\n" +
                    "- 重构查询逻辑，减少不必要的表连接\n\n" +
                    "**Result（结果）**：\n" +
                    "- MySQL 查询性能提升 40%\n" +
                    "- 数据库 CPU 使用率下降 30%\n\n" +
                    "---\n\n" +
                    "### 3. 安全加固\n\n" +
                    "**Situation（情境）**：系统存在安全风险，需要加强防护。\n\n" +
                    "**Task（任务）**：实施代码审计和安全加固。\n\n" +
                    "**Action（行动）**：\n" +
                    "- 实施 SQL 注入防护，使用参数化查询\n" +
                    "- 添加 XSS 过滤，对用户输入进行转义\n" +
                    "- 完善接口鉴权，使用 JWT Token 认证\n\n" +
                    "**Result（结果）**：\n" +
                    "- 攻击拦截率达 92%\n" +
                    "- 系统安全性显著提升\n\n" +
                    "---\n\n" +
                    "### 实习收获\n\n" +
                    "1. **技术能力**：掌握了生产环境中的系统优化和安全加固方法\n" +
                    "2. **工程思维**：学会了从用户体验和系统稳定性角度思考问题\n" +
                    "3. **团队协作**：理解了团队开发流程和代码规范的重要性";
            keyPoints = List.of("具体的职责和成果", "量化的性能指标", "技术成长和收获");
            tips = List.of("用数据说话：成功率、响应时间、QPS 等", "强调个人贡献而非团队成果", "总结从中学到的方法论");
        } else {
            // 通用回答模板，结合具体项目
            answer = String.format("关于「%s」这个问题，我从以下几个角度来回答：\n\n" +
                    "---\n\n" +
                    "### 1. 问题分析\n\n" +
                    "首先明确问题的核心诉求和关键点。以我的项目经验为例：\n\n" +
                    "- **FundPilot-AI**：需要解决多 Agent 协作的复杂性问题\n" +
                    "- **Sec-sonar**：需要解决 RASP 运行时防护的可靠性问题\n" +
                    "- **ResumeAgent**：需要解决 RAG 知识库的准确性问题\n\n" +
                    "---\n\n" +
                    "### 2. 解决思路\n\n" +
                    "结合我的技术背景（Java 后端 + AI 应用 + 安全防护），我会：\n\n" +
                    "1. **分析根因**：通过日志、监控、性能分析定位问题根源\n" +
                    "2. **设计方案**：参考业界最佳实践，结合项目实际情况制定方案\n" +
                    "3. **验证效果**：通过 A/B 测试、压力测试验证方案有效性\n\n" +
                    "---\n\n" +
                    "### 3. 实施方案\n\n" +
                    "具体到技术实现：\n\n" +
                    "- **架构层面**：使用微服务拆分、异步解耦、缓存优化等策略\n" +
                    "- **代码层面**：遵循 SOLID 原则，编写可测试、可维护的代码\n" +
                    "- **运维层面**：使用 Docker 容器化、CI/CD 自动化、监控告警\n\n" +
                    "---\n\n" +
                    "### 4. 预期效果\n\n" +
                    "- **性能提升**：响应时间降低 30%+，吞吐量提升 50%+\n" +
                    "- **稳定性增强**：系统可用性达到 99.9%+\n" +
                    "- **可维护性提高**：代码复用率提升，开发效率提高\n\n" +
                    "---\n\n" +
                    "在我的项目实践中，我始终按照这个思路来分析和解决问题，目前已有 11 个独立完成的项目经验。", request.question());
            keyPoints = List.of("清晰的问题分析", "系统的解决思路", "结合实际项目经验");
            tips = List.of("保持逻辑清晰，分点阐述", "结合具体项目案例", "展示解决问题的能力和方法论");
        }

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

