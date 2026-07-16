# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指导。

## 项目概述

ResumeAgent 是一个基于 RAG 的个人简历 AI 助手系统，采用 Spring Boot 3 + Vue3 构建。系统将个人文档（简历、项目文档、博客文章）导入知识库，使用 RAG 检索相关上下文，通过自然语言对话生成准确、可追溯的回答。同时提供岗位匹配分析和面试准备功能。

## 快速上手（新用户必读）

### 1. 环境要求

- **Java 17+**（后端运行）
- **Node.js 18+**（前端构建）
- **Maven 3.8+**（后端依赖管理）
- **OpenAI 兼容 API**（可选，不配置时使用模板回答）

### 2. 配置 LLM

编辑 `backend/src/main/resources/application.yml`，填入你的 LLM 配置：

```yaml
resumeagent:
  openai:
    base-url: "https://your-api-endpoint/v1"   # 你的 OpenAI 兼容 API 地址
    api-key: "sk-xxxx"                          # 你的 API 密钥
    chat-model: gpt-4o-mini                     # 模型名称
    temperature: 0.2
    max-tokens: 800
```

支持的 API 提供商（任何 OpenAI 兼容接口均可）：
- OpenAI（`https://api.openai.com/v1`）
- DeepSeek（`https://api.deepseek.com/v1`）
- 通义千问（`https://dashscope.aliyuncs.com/compatible-mode/v1`）
- 本地模型（Ollama、vLLM 等，填对应地址）

> **不配置 LLM 也能运行**：系统会回退到基于检索片段的模板回答，适合验证流程是否正常。

### 3. 放置你的个人资料

知识库内容通过 **前端页面上传**，无需手动放置文件。启动后在"知识库"页面上传即可。

支持的资料类型：
- **简历文件**（PDF / Word / 纯文本）
- **项目文档**（Markdown / 文本）
- **技术博客**（Markdown / HTML / 文本）
- **GitHub 项目说明**（README.md 内容）

上传后的文件会被解析、切片、向量化，存入内存向量存储。**重启后端需要重新上传**（当前为内存存储）。

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

# 运行测试
cd backend && mvn test

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
- `ChatController` (`/api/chat`) - 问答接口，支持 SSE 流式输出
- `AgentController` (`/api/agent`) - Agent 分析接口
  - `/api/agent/analyze` - 岗位匹配分析
  - `/api/agent/project-query` - 项目查询
  - `/api/agent/skill-analysis` - 技能分析
  - `/api/agent/interview-answer` - 面试回答生成
- `RagController` (`/api/rag`) - 知识库导入和搜索
- `AuditController` (`/api/audit`) - 安全日志审计

**服务** - 核心业务逻辑：
- `ChatService` - 编排 RAG 流水线：安全检查 → 知识检索 → 提示词构建 → LLM 生成 → 审计日志。使用 `conversationId` 维护内存中的对话历史。
- `KnowledgeBaseService` - 内存向量存储，支持文本分块（260 字符上限，40 字符重叠）。评分结合 `EmbeddingService` 的余弦相似度和词汇增强。
- `EmbeddingService` - 基于哈希的嵌入（128 维）。非真实嵌入模型，专为本地开发设计，无需外部服务。支持 CJK 和 ASCII 分词及 n-gram。
- `LlmService` - OpenAI 兼容 API 客户端。未配置时返回 `Optional.empty()`，导致回退到 `PromptBuilder` 的模板响应。
- `AgentService` - 基于关键词的岗位匹配分析。提取技术关键词并计算重叠分数。支持 LLM 语义分析，不可用时降级为规则匹配。
- `SafetyService` - 基于正则的 Prompt Injection 检测（中英文模式）和 PII 脱敏（手机号、邮箱、身份证号）。
- `PromptBuilder` - 为 LLM 构建系统/用户提示词，包含对话历史和检索到的上下文。
- `AuditService` - 内存审计日志，记录所有聊天/Agent 交互。

**配置** - `ResumeAgentProperties`（`application.yml` 中 `resumeagent.*` 前缀）：
- `resumeagent.openai.*` - LLM 配置（base-url, api-key, chat-model, temperature, max-tokens）
- `resumeagent.security.*` - 安全标志（block-on-injection, redact-sensitive）
- `resumeagent.audit.enabled` - 审计日志开关

### 前端结构 (`frontend/src/`)

**页面**（`App.vue` 中的标签页）：
- `ChatPanel.vue` - 聊天界面，支持普通和 SSE 流式模式
- `ProjectQueryPanel.vue` - 项目查询界面
- `SkillAnalysisPanel.vue` - 技能分析界面
- `JobMatchPanel.vue` - 岗位匹配分析表单
- `InterviewPanel.vue` - 面试助手界面
- `AuditPanel.vue` - 安全审计日志查看器（默认隐藏，仅管理员可用）

**API 层**（`api/`）：
- `http.ts` - Axios 实例，基础 URL 为 `/api`
- `chat.ts`, `agent.ts`, `rag.ts`, `audit.ts` - 类型化 API 客户端

### 数据流

```
用户输入 → SafetyService (注入/PII 检查)
        → KnowledgeBaseService.search() (向量 + 词汇检索)
        → PromptBuilder (系统提示词 + 历史 + 上下文)
        → LlmService (OpenAI 兼容 API，或回退)
        → AuditService (记录交互)
        → 响应 (含引用)
```

## 关键设计决策

- **内存存储**：知识库、对话和审计日志存储在内存中。开发无需数据库。
- **优雅降级**：LLM 未配置时，系统回退到基于检索知识块的模板响应。
- **SSE 流式**：`/api/chat/stream` 端点使用 `SseEmitter` 逐行流式响应，80ms 延迟。
- **开发代理**：Vite 将 `/api` 请求代理到 `localhost:8080`，开发时需同时运行前端和后端。

## 配置

后端配置位于 `backend/src/main/resources/application.yml`，主要设置：

```yaml
resumeagent:
  openai:
    base-url: ""      # OpenAI 兼容 API 端点
    api-key: ""        # API 密钥
    chat-model: gpt-4o-mini
    temperature: 0.2
    max-tokens: 800
  security:
    block-on-injection: true  # 阻止检测到的注入尝试
    redact-sensitive: true    # 脱敏输入中的 PII
  audit:
    enabled: true             # 记录所有交互
```
