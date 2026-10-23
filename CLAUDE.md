# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

ResumeAgent 是一个基于 RAG 的个人简历 AI 助手系统，采用 Spring Boot 3 + Vue3 构建。系统将个人文档（简历、项目文档、博客文章）导入知识库，使用 RAG 检索相关上下文，通过自然语言对话生成准确、可追溯的回答。同时提供岗位匹配分析和面试准备功能。

## 快速上手（新用户必读）

### 1. 环境要求

- **Java 17+**（后端运行）
- **Node.js 18+**（前端构建）
- **Maven 3.8+**（后端依赖管理）
- **OpenAI 兼容 API**（可选，不配置时使用模板回答）

### 2. 配置 LLM

LLM 配置位于 `backend/src/main/resources/application.yml`，通过环境变量注入密钥：

```yaml
resumeagent:
  openai:
    base-url: "${ZHIPU_BASE_URL:}"             # OpenAI 兼容 API 端点
    api-key: "${ZHIPU_API_KEY:}"               # API 密钥
    chat-model: "${ZHIPU_CHAT_MODEL:gpt-4o-mini}"
    temperature: "${ZHIPU_TEMPERATURE:0.2}"
    max-tokens: "${ZHIPU_MAX_TOKENS:2048}"
```

> **安全提示**：密钥一旦随仓库外泄，务必到服务商控制台吊销后换新。

支持的 API 提供商（任何 OpenAI 兼容接口均可）：
- OpenAI（`https://api.openai.com/v1`）
- DeepSeek（`https://api.deepseek.com/v1`）
- 通义千问（`https://dashscope.aliyuncs.com/compatible-mode/v1`）
- 本地模型（Ollama、vLLM 等，填对应地址）

> **不配置 LLM 也能运行**：系统会回退到基于检索片段的模板回答，适合验证流程是否正常。

### 3. 放置你的个人资料

知识库内容有两种来源：

1. **启动自动加载**：`KnowledgeAutoLoader` 在启动时扫描 `backend/src/main/resources/knowledge/` 目录（`resume/`、`projects/`、`blog/` 子目录，支持 `.txt`/`.md`），文件名作为 sourceName、目录名决定 sourceType，自动切片入库。
2. **前端页面上传**：启动后在"知识库"页面上传（`POST /api/rag/upload` 文件解析入库 / `POST /api/rag/documents` 粘贴文本保存，均落盘 `data/knowledge/`）。

支持的资料类型：
- **简历文件**（PDF / Markdown / 纯文本）
- **项目文档**（Markdown / 文本）
- **技术博客**（Markdown / 纯文本）
- **GitHub 项目说明**（README.md 内容）

解析后的内容会被切片、向量化存入内存向量存储，并**落盘持久化**（`data/knowledge/`）：内置文件重启自动加载，页面上传的文档同样重启自动重新入库。

> 新增项目文档只需在 `resources/knowledge/projects/` 放置文件即可：检索路由（项目名匹配、关键词抽取）全部基于知识库元数据动态生成，无需修改代码。

### 4. 启动项目

```bash
# 后端
cd backend && mvn spring-boot:run

# 前端（另开终端）
cd frontend && npm install && npm run dev
```

访问 `http://localhost:5173` 开始使用。

## 常用命令

### 后端 (Java 17, Maven)
```bash
# 运行后端服务器 (端口 8080)
cd backend && mvn spring-boot:run

# 运行所有测试
cd backend && mvn test

# 运行单个测试类
cd backend && mvn test -Dtest=ResumeAgentSmokeTest

# 运行单个测试方法
cd backend && mvn test -Dtest=ResumeAgentSmokeTest#shouldSearchRelevantKnowledge

# 构建 JAR
cd backend && mvn package
```

### 前端 (Node.js, Vite)
```bash
# 安装依赖
cd frontend && npm install

# 运行开发服务器 (端口 5173, 代理 /api 到 localhost:8080)
cd frontend && npm run dev

# 生产环境构建
cd frontend && npm run build

# 预览生产构建
cd frontend && npm run preview
```

## 架构

### 后端结构 (`backend/src/main/java/com/resumeagent/`)

**控制器** - REST API 端点：
- `ChatController` (`/api/chat`) - 问答接口，支持 POST JSON SSE 真流式输出（`/api/chat/stream`）
- `AgentController` (`/api/agent`) - Agent 分析接口
  - `/api/agent/analyze` - 岗位匹配分析（快速）
  - `/api/agent/match-report` - 简历/JD 深度匹配报告（逐条 JD 要求 + 维度分 + 简历修改建议）
  - `/api/agent/project-query` - 项目查询
  - `/api/agent/skill-analysis` - 技能分析
  - `/api/agent/interview-answer` - 面试回答生成
  - `/api/agent/tech-quote` - 每日技术语录
  - `/api/agent/interview-reviews` - 模拟面试复盘生成/存档/对比（POST/GET/DELETE）
- `ExportController` (`/api/export`) - 导出简历优化版/面试问答集与复盘（PDF 或 Markdown，`X-Export-Format` 头反映实际格式）
- `TechGuideController` (`/api/techguide/stream`) - 技术学习导师，POST JSON SSE 真流式（按 module/tag 分模块教学，带追问建议事件）
- `ProfileController` (`/api/profile`) - 用户档案模板：网申信息填写、简历上传解析（`POST /upload`，PDF/TXT/MD）、多模板 CRUD 与激活切换（`/templates`, `/templates/{id}/activate`, `/active`）、**版本历史与回滚**（`/templates/{id}/versions`, `/versions/{ts}/rollback`）
- `JobController` (`/api/jobs`) - 岗位工作区：岗位 CRUD、投递状态、匹配报告、定制简历建议和复盘关联
- `RagController` (`/api/rag`) - 知识库导入、搜索、**文档管理**（`GET/DELETE /documents`、`POST /upload` 文件上传入库并落盘持久化）
- `AuditController` (`/api/audit/logs`) - 安全日志审计（需要管理员 Bearer Token）
- `SystemController` (`/api/system`) - 系统配置查询（LLM 配置信息）

**服务** - 核心业务逻辑：
- `ChatService` - 编排 RAG 流水线：安全检查 → 问题分类（自我介绍/项目/技术/行为，支持追问类型继承）→ 多路知识检索（含动态文档名匹配）→ 动态提示词构建 → LLM 生成 → 审计日志。流式与非流式共用 `prepare()`；流式路径 `streamChat()` 逐 delta 转发。使用 `conversationId` 维护内存中的对话历史（滑动窗口 20 条）。
- `KnowledgeBaseService` - 内存向量知识库。文本分块（260 字符上限，40 字符重叠）；ingest 预计算归一化文本与 token 集合、批量写入；检索为**倒排索引粗排（含中文 2-gram）+ 向量/词汇精排**的两阶段检索。提供 `sourceNames()`、`searchBySourceType()` 动态元数据入口和基于 IDF 的 `extractKeywords()` 关键词抽取。
- `EmbeddingService` - 基于哈希的嵌入（128 维）。非真实嵌入模型，专为本地开发设计，无需外部服务。支持 CJK 和 ASCII 分词及 n-gram。
- `LlmService` - OpenAI 兼容 API 客户端。`generate()` 非流式；`generateStream()` 真流式（`stream: true` + SSE 增量帧解析，`parseStreamLine()` 可单测），delta 实时回调。未配置时返回 `Optional.empty()`，导致回退到基于检索的模板响应。`isAvailable()` 方法暴露配置状态。
- `AgentService` - 完全基于 RAG 知识库的动态分析引擎。从知识库检索项目资料，自动提取技术栈、项目亮点，生成岗位匹配、技能分析、面试回答。无硬编码数据。
- `TechGuideService` - 技术学习导师。按模块（java/spring/database/rag/agent/security/项目实战）构建导师提示词，流式生成；`<FOLLOWUP>` 追问块由 `FollowupFilter` 从正文中剔除、以独立 SSE 事件下发。
- `FollowupFilter` - 流式 `<FOLLOWUP>` 块过滤器（hold-back 策略处理跨 delta 边界的标签）。
- `KnowledgeAutoLoader` - 启动时自动加载 `classpath:knowledge/` 下的 txt/md 文件入库。
- `ProfileService` - 用户档案模板管理：网申表单（`ProfileForm`）+ 简历文本的多模板 CRUD、激活切换，JSON 持久化到 `data/profiles/`。激活模板以固定 sourceName「我的档案」整体替换入知识库——**所有 RAG 流程因此以用户亲手填写/上传的信息为第一事实来源**。内置档案代数（generation）：激活档案内容变化时递增，供 ChatService 丢弃携带旧身份的对话历史。
- `JobService` - 岗位工作区：岗位 JD 和投递状态 JSON 持久化到 `data/jobs/`，保存匹配报告、岗位专属简历建议，并关联面试复盘。
- `PdfTextExtractor` - PDF 简历文本抽取（PDFBox 3.x）；扫描图片版 PDF 无文本层时提示用户粘贴文本。
- `MatchReportService` - 简历/JD 深度匹配报告：逐条拆解 JD 要求与档案比对（满足/部分/缺失 + 证据 + 建议）、维度分、简历修改建议。LLM JSON 输出 + 关键词降级双路径。
- `InterviewReviewService` - 模拟面试复盘：维度评分（内容深度/技术扎实度/表达结构/岗位匹配）+ 亮点/改进 + 逐题点评，存档 `data/reviews/` 供多场对比。LLM 语义复盘 + 统计降级双路径。
- `ExportService` - 导出简历优化版/面试问答集（PDFBox 生成 PDF，自动探测系统 CJK 字体；无中文字体时降级 Markdown，绝不输出乱码 PDF）。
- `KnowledgeDocumentService` - 知识库手动文档管理：上传/粘贴文档落盘 `data/knowledge/`，启动自动重新入库（重启不丢）；内置文档（classpath:knowledge/）只读。
- `SafetyService` - 基于正则的 Prompt Injection 检测（中英文模式）和 PII 脱敏（手机号、邮箱、身份证号）。
- `PromptBuilder` - 为 LLM 构建动态系统提示词。系统提示词只含角色设定和回答风格指引，RAG 检索结果动态注入作为 LLM 背景知识。
- `AuditService` - 内存审计日志，记录所有聊天/Agent 交互。

**配置** - `ResumeAgentProperties`（`application.yml` 中 `resumeagent.*` 前缀）：
- `resumeagent.openai.*` - LLM 配置（base-url, api-key, chat-model, temperature, max-tokens）
- `resumeagent.security.*` - 安全标志（block-on-injection, redact-sensitive）
- `resumeagent.audit.enabled` - 审计日志开关

### 前端结构 (`frontend/src/`)

**页面**（`App.vue` 中的标签页）：
- `ProfilePanel.vue` - 我的档案：简历上传（PDF/TXT/MD 解析预览）、网申式信息表单、多模板管理与一键切换、**版本历史/回滚**（白色模块化设计）
- `JobWorkspacePanel.vue` - 岗位工作区：岗位看板/列表、投递状态、匹配报告、定制简历建议和复盘关联
- `KnowledgePanel.vue` - 知识库：文件上传（PDF/TXT/MD）+ 粘贴文本保存、文档列表管理（内置/上传标记、删除）
- `ReviewPanel.vue` - 面试复盘：历史成绩对比列表 + 复盘详情（维度/亮点/改进/逐题）+ 导出
- `WelcomePanel.vue` - 欢迎/引导页
- `ChatPanel.vue` - 模拟面试界面，以候选人身份回答面试官问题，使用 fetch ReadableStream 接收 LLM 增量 delta
- `ProjectDetailPanel.vue` - 项目作品集详情页
- `ProjectQueryPanel.vue` - 项目查询界面
- `SkillAnalysisPanel.vue` - 技能分析界面
- `JobMatchPanel.vue` - 岗位匹配：快速匹配 / **深度报告**（逐条 JD 要求、维度分、简历修改建议）双模式，支持导出 PDF/Markdown
- `InterviewPanel.vue` - 面试助手界面
- `TechGuidePanel.vue` - 技术学习导师（SSE 真流式 + `followups` 追问建议事件）
- `AuditPanel.vue` - 安全审计日志查看器（默认隐藏，仅管理员可用）
- `GuideModal.vue`, `ThinkingIndicator.vue` - 使用引导弹窗 / 思考中指示器

**Composables**（`composables/`）：`useChatStorage`（本地聊天记录持久化）、`useSessionManager`（会话管理）、`useMarkdown`（Markdown 渲染）

**API 层**（`api/`）：
- `http.ts` - Axios 实例，基础 URL 为 `/api`
- `agent.ts`, `profile.ts`, `rag.ts`, `export.ts`, `audit.ts` - 类型化 API 客户端（聊天/教学 SSE 由 ChatPanel/TechGuidePanel 直连 fetch）

### 数据流

```
用户输入 → SafetyService (注入/PII 检查)
        → 问题分类 + 多路检索 (倒排索引粗排 → 向量/词汇精排，动态文档名匹配)
        → PromptBuilder (动态系统提示词 + RAG 上下文 + 历史)
        → LlmService (OpenAI 兼容 API 真流式 delta / 或回退)
        → AuditService (记录交互)
        → SSE 逐 delta 转发 (含引用)
```

## 关键设计决策

- **个人资料不硬编码**：所有个人数据均从知识库动态检索。项目名匹配来自知识库 `sourceNames()` 元数据，检索关键词由知识库 IDF 动态抽取，新增/删除文档无需修改代码。系统提示词不含任何预设的项目介绍话术。
- **以用户输入为准**：激活的档案模板（用户亲手填写的网申信息 + 上传的简历）以「我的档案」为 sourceName 最优先注入每次检索；系统提示词中「用户档案」独立成节、声明最高优先级，与其它资料/对话历史冲突时一律以档案为准。档案切换后（generation 变化）自动清空该对话的历史，防止旧身份污染回答。
- **内存存储 + JSON 持久化**：知识库/对话/审计在内存中运行（开发无需数据库）；用户资产落盘 `data/`（`profiles/` 档案与版本、`knowledge/` 上传文档、`reviews/` 复盘存档），重启自动重新加载。
- **优雅降级**：LLM 未配置时，系统回退到基于检索知识块的模板响应；知识库无命中时直接 fallback、不调用 LLM（防幻觉）。
- **SSE 真流式**：`POST /api/chat/stream`、`POST /api/techguide/stream` 接收 JSON 后转发 LLM 的 `stream: true` 增量 delta，前端使用 fetch 读取并支持取消。
- **两阶段检索**：倒排索引粗排（中文 2-gram）+ 向量/词汇精排，检索打分公式保持稳定；ingest 预计算 + 批量写入。
- **开发代理**：Vite 将 `/api` 请求代理到 `localhost:8080`，开发时需同时运行前端和后端。

## 配置

后端配置位于 `backend/src/main/resources/application.yml`，主要设置：

```yaml
resumeagent:
  admin-token: "${RESUME_AGENT_ADMIN_TOKEN:}"
  allowed-origins: "${RESUME_AGENT_ALLOWED_ORIGINS:http://localhost:5173}"
  openai:
    base-url: "${ZHIPU_BASE_URL:}"       # OpenAI 兼容 API 端点
    api-key: "${ZHIPU_API_KEY:}"         # API 密钥
    chat-model: "${ZHIPU_CHAT_MODEL:gpt-4o-mini}"
    temperature: "${ZHIPU_TEMPERATURE:0.2}"
    max-tokens: "${ZHIPU_MAX_TOKENS:2048}"
  security:
    block-on-injection: true  # 阻止检测到的注入尝试
    redact-sensitive: true    # 脱敏输入中的 PII
  audit:
    enabled: true             # 记录所有交互
    max-entries: 2000
  stream:
    timeout-ms: 300000
    max-concurrent: 16
  session:
    max-conversations: 1000
    idle-timeout-minutes: 30
    max-history-messages: 20
  rate-limit:
    enabled: true
    requests-per-minute: 30
  index:
    batch-load: true
    warn-chunk-count: 10000
```
