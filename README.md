<div align="center">

# 🤖 ResumeAgent

### 基于 RAG 的个人简历智能 Agent 系统

**让 AI 成为你的"数字分身"，精准回答关于你的一切**

[![Java](https://img.shields.io/badge/Java-17+-orange?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](#)

<br/>

<p>
<strong>模拟面试</strong> · <strong>RAG 知识库</strong> · <strong>岗位匹配</strong> · <strong>技能分析</strong> · <strong>面试助手</strong> · <strong>安全防护</strong> · <strong>技术引航图</strong>
</p>

🌐 **在线体验**：[https://mentalczh66.xyz](https://mentalczh66.xyz)

</div>

---

## 📖 目录

- [项目简介](#-项目简介)
- [解决什么问题](#-解决什么问题)
- [核心功能](#-核心功能)
  - [模拟面试](#-模拟面试)
  - [RAG 知识库](#-rag-知识库)
  - [Agent 智能分析](#-agent-智能分析)
  - [技术引航图](#-技术引航图)
  - [安全机制](#-安全机制)
  - [SSE 流式对话](#-sse-流式对话)
  - [前端界面](#-前端界面)
- [系统架构](#-系统架构)
- [数据处理流程](#-数据处理流程)
- [模块详解](#-模块详解)
- [API 接口文档](#-api-接口文档)
- [技术栈](#-技术栈)
- [快速开始](#-快速开始)
- [配置说明](#-配置说明)
- [项目结构](#-项目结构)
- [开发计划](#-开发计划)
- [项目亮点](#-项目亮点)
- [简历描述](#-简历描述可直接使用)

---

## ✨ 项目简介

ResumeAgent 是一个基于 **Spring Boot 3 + Vue3 + RAG + Agent** 构建的个人 AI 简历助手系统。

系统将你的简历、项目文档、技术博客、GitHub 项目说明等个人资料导入知识库，通过 **RAG（检索增强生成）** 技术检索相关上下文，结合 LLM 生成**准确、可追溯**的回答。个人资料均从知识库动态检索，不在代码中硬编码，确保回答始终基于最新资料。同时内置 Agent 能力，支持岗位匹配分析、技能图谱总结、面试回答生成等高级功能。

> 🎯 **适用场景**：求职面试准备、个人技术品牌展示、项目经历梳理、能力盘点与职业规划。

![1.png](%E9%A1%B9%E7%9B%AE%E7%85%A7%E7%89%87/1.png)
---

## ❓ 解决什么问题

| 痛点 | ResumeAgent 的方案 |
|:---|:---|
| 大模型不了解你的个人经历 | RAG 知识库动态注入个人资料，回答基于真实数据，无硬编码 |
| 面试前准备耗时，难以全面梳理 | Agent 基于知识库自动生成岗位匹配分析和面试回答 |
| 通用 AI 回答偏泛化、容易编造 | 检索增强 + 引用溯源，降低幻觉 |
| 个人敏感信息容易泄露 | Prompt Injection 检测 + PII 自动脱敏 |
| 缺少一个统一的"个人数字分身"入口 | 一个系统整合问答、分析、面试全流程 |

---

## 🎯 核心功能

### 💬 模拟面试

系统**以你的身份**回答面试官的问题，就像你本人在面试。支持**模拟面试**和**通用对话**两种模式，可随时切换。

**核心机制**：
- AI 不是"帮你介绍简历"，而是**就是你本人**
- 全程第一人称回答，语气自然口语化
- 基于知识库中的真实经历，不编造
- **智能项目讲述策略**：区分列举类和深挖类问题，回答有重点、有层次
- **回答质量保障**：强制要求每次回答 400 字以上，系统提示词 + 后处理双重约束

**项目讲述策略**：
- **列举类问题**（"你做过哪些项目？"）：重点讲述 2 个核心项目，简要提及 4-5 个其他项目
- **深挖类问题**（"介绍一下XXX项目"）：使用 STAR 法则详细展开，包含技术架构、难点和量化成果
- **不能直接照搬文档**：用自然语言重新组织，体现思考和理解
- **准确描述项目**：确保项目描述准确，避免误导面试官

**示例**：

```
面试官: "请介绍一下你自己"
面试官: "你做过哪些项目？"
面试官: "介绍一下你的 FundPilot-AI 项目"
面试官: "你在项目中遇到过什么难点？"
面试官: "你最擅长的技术是什么？"
```
![2.png](%E9%A1%B9%E7%9B%AE%E7%85%A7%E7%89%87/2.png)

回答包含引用来源，可追溯到原始文档片段。

### 🔍 RAG 知识库

将个人资料转换为向量知识库，实现语义级别的精准检索。

**支持的资料类型**：

| 类型 | 格式 | 内容 |
|:---|:---:|:---|
| 📄 简历 | PDF / TXT / MD | 个人基本信息、教育、经历 |
| 📁 项目文档 | Markdown / TXT | 项目架构、技术方案、成果 |
| 📝 技术博客 | Markdown / TXT | 技术文章、学习笔记 |
| 🐙 GitHub 说明 | Markdown | 项目 README、文档 |

**核心流程**：

```mermaid
flowchart LR
    A["📄 简历 / 文档 / 博客"] --> B["文本解析"]
    B --> C["语义切片"]
    C --> D["Embedding 向量化"]
    D --> E["向量存储"]
    E --> F["语义检索"]
    F --> G["🤖 LLM 生成回答"]
```

**技术细节**：
- 文本切片：260 字符上限，40 字符重叠，保证上下文连贯
- 向量化：128 维哈希嵌入（开发阶段，可替换为真实 Embedding 模型）
- 检索策略：余弦相似度 + 词汇增强混合排序，兼顾语义和关键词匹配

### 🤖 Agent 智能分析

Agent 提供四类高级能力，不只是简单问答：

| 能力 | 说明 | 示例问题 |
|:---|:---|:---|
| 🎯 **岗位匹配** | 分析目标岗位与个人能力的匹配度、优势、短板 | "我适合 Java 安全开发岗位吗？" |
| 🔍 **项目查询** | 按项目名称、技术栈、时间线查询经历 | "我做过哪些 Spring Boot 项目？" |
| 📊 **技能分析** | 总结擅长方向、技术深度与覆盖面 | "帮我总结一下我的技术优势和短板" |
| 🎤 **面试回答** | 针对面试问题生成个性化回答 | "如果面试官问你项目难点，怎么回答？" |

### 📌 岗位工作区

将岗位信息、JD、匹配报告、定制简历建议、岗位专属面试和复盘记录集中到同一条岗位记录中，支持看板/列表视图以及收藏、准备、已投递、笔试、面试、Offer 等状态跟踪。岗位数据持久化到 `backend/data/jobs/`，不会覆盖原始档案。

### 🧭 技术引航图

交互式技术学习引导系统，以"AI 导师"角色讲解核心知识点。

**学习模块**：

| 模块 | 内容 | 示例知识点 |
|:---|:---|:---|
| ☕ **Java 基础** | 语言核心与集合框架 | 面向对象、泛型、HashMap、Stream |
| 🌿 **Spring 生态** | 框架核心与 Web 开发 | IoC、AOP、自动配置、Spring Security |
| 🗄️ **数据库与缓存** | SQL、ORM 与缓存策略 | MySQL 索引、Redis 缓存、分库分表 |
| 🔍 **RAG 核心技术** | 检索增强生成全链路 | 文本切片、向量检索、混合排序 |
| 🤖 **Agent 架构** | 服务编排、检索与分析 | 意图路由、上下文管理、RAG 分析 |
| 🔒 **安全机制** | Web 安全与 AI 安全 | Prompt Injection、PII 脱敏 |
| 📄 **实战:ResumeAgent** | 本项目源码精讲 | 项目架构、RAG 流水线、Agent 实现 |
| 📈 **实战:FundPilot** | 基金分析平台精讲 | 技术架构、核心功能、项目亮点 |

**核心特性**：
- 点击知识点标签，自动发起教学对话
- 生活类比 + ASCII 图解 + 记忆口诀 + 代码实战的教学风格
- **主动引导式学习**：AI 回复末尾自动生成 2-3 个可点击的「推荐追问」按钮，层层深入无需手动提问
- 多轮对话上下文保持，支持追问深入
- RAG 检索知识库辅助教学，保证内容准确性
- SSE 流式输出，智能自动滚动（用户翻页时暂停，5 秒后恢复）
- 代码块深色主题 + 语言标签，ASCII 图示等宽渲染
- 清空对话二次确认，防止误触丢失学习记录

### 🛡️ 安全机制

针对 Agent 场景设计的三层安全防护：

| 层级 | 功能 | 实现方式 |
|:---|:---|:---|
| 🛡️ **Prompt Injection 检测** | 识别恶意注入攻击 | 正则匹配中英文攻击模式（"忽略之前指令"、"泄露系统提示词"等） |
| 🔒 **PII 信息脱敏** | 保护个人敏感信息 | 自动识别并脱敏手机号、邮箱、身份证号 |
| 📋 **对话审计日志** | 全链路交互追踪 | 记录用户输入、检索片段和模型输出（管理员 Token 保护） |

### 📡 SSE 流式对话

- 基于 `SseEmitter` 实现服务端推送
- 80ms 间隔逐行输出，前端打字机效果
- 支持中断和超时处理

### ⏳ 思考中提示

LLM 响应等待期间，光标旁显示趣味提示动画，缓解等待焦虑：

```
✦ |  😭 大学生没钱买好模型
```

**提示文案**（每 2 秒自动切换）：

| 图标 | 文案 | 图标 | 文案 |
|:---:|:---|:---:|:---|
| 😭 | 大学生没钱买好模型 | 🍜 | 用爱发电中 |
| 💸 | 白嫖算力中 | 🥲 | 模型在吃泡面 |
| 💀 | GPU在哭穷 | 🤡 | 服务器在省钱 |
| 🫠 | 模型在搬砖赚算力 | 💔 | 穷但努力中 |
| 📉 | 廉价算力加载中 | 🥺 | 免费额度消耗中 |
| 😤 | 学生优惠已用完 | 🎓 | 模型在兼职中 |

**实现机制**：
- 发送消息后立即显示提示，与 LLM 请求并行
- 收到第一个响应数据后立即消失，不影响响应速度
- 纯前端 CSS 动画，零开销

### 🎨 前端界面

欢迎首页采用居中布局，两侧装饰面板展示系统能力信息，中间提供快速入口：

| 区域 | 内容 |
|:---|:---|
| 左侧面板 | 技术栈标签云、RAG 工作流步骤 |
| 中央 Hero | AI-Powered 徽章、产品标题、简介、技术名言 |
| 快速提问 | 编号提问卡片，点击直接发起对话 |
| 功能入口 | 2×2 网格卡片（岗位匹配/面试助手/技能分析/项目查询） |
| 右侧面板 | 核心指标（向量维度/分块上限/流式延迟）、支持格式 |
| 侧边栏底部 | 绿色状态灯，悬停显示 LLM 配置详情 |

聊天页支持 RAG / 通用对话双模式，输入区底部提供模式切换快捷键。

侧边栏底部的绿色状态指示灯支持悬停查看 LLM 配置详情：连接状态、模型名称、API 端点、密钥（脱敏）、温度和最大 Token 数。首次悬停时从后端获取数据，白色卡片风格与主页统一。

### 📖 使用指南

首次访问自动弹出模块化引导弹窗，帮助新用户快速了解系统功能：

| 步骤 | 内容 |
|:---|:---|
| 1. 欢迎 | 整体功能概览 |
| 2. 模拟面试 | 核心功能介绍（简历问答 / 通用对话） |
| 3. 岗位匹配 | 匹配度分析、优势、差距、建议 |
| 4. 面试助手 | STAR 法则回答生成 |
| 5. 技能分析 | 技术栈与能力图谱 |
| 6. 项目查询 | 关键词 / 技术栈检索项目 |
| 7. 开始使用 | 快速上手三步走 |

- 支持底部圆点跳转、上一步/下一步导航
- 侧边栏 `?` 按钮可随时重新打开
- 通过 `localStorage` 记录是否已查看

---

## 🏗️ 系统架构

```mermaid
flowchart TB
    subgraph Frontend["🖥️ 前端层"]
        V["Vue3 + TypeScript<br/>Element Plus"]
    end

    subgraph Backend["⚙️ 后端层"]
        S["Spring Boot 3"]
        A["🤖 Resume Agent"]
        RAG["🔍 RAG 检索引擎"]
        LLM["🧠 LLM Service"]
        SAFE["🛡️ Safety Service"]
        AUDIT["📋 Audit Service"]
    end

    subgraph Storage["💾 存储层"]
        M["内存向量存储"]
        C["对话历史缓存"]
        AL["审计日志"]
    end

    subgraph External["☁️ 外部服务"]
        API["OpenAI 兼容 API"]
    end

    V -->|"REST / SSE"| S
    S --> SAFE
    SAFE --> A
    A --> RAG
    A --> LLM
    RAG --> M
    LLM --> API
    S --> AUDIT
    AUDIT --> AL
    A --> C
```

**架构分层说明**：

| 层级 | 职责 | 关键组件 |
|:---|:---|:---|
| 前端层 | 交互展示、流式渲染、结果可视化 | Vue3、Element Plus、SSE 客户端 |
| 控制器层 | REST API 端点、请求路由 | ChatController、AgentController、RagController、SystemController |
| 服务层 | 核心业务逻辑、RAG 流水线编排 | ChatService、AgentService、KnowledgeBaseService |
| 安全层 | 注入检测、PII 脱敏、审计记录 | SafetyService、AuditService |
| 存储层 | 向量存储、对话缓存、用户资产落盘 | 内存向量存储 + `backend/data/` JSON 持久化（可扩展为 Milvus） |
| 外部层 | LLM 推理能力 | OpenAI 兼容 API |
| 优化层 | 对话质量优化、历史管理、上下文感知 | 滑动窗口、追问继承、阶段感知、关键词提取 |

---

## 📊 数据处理流程

### 问答流程（Chat）

```
用户输入
  │
  ▼
┌─────────────────────┐
│  SafetyService      │  ① 安全检查
│  - 注入检测          │     检测 Prompt Injection
│  - PII 脱敏          │     手机号/邮箱/身份证脱敏
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  ChatService        │  ② 历史管理
│  - 滑动窗口          │     保留最近 20 轮对话
│  - 追问检测          │     指代词/短消息识别
│  - 关键词提取        │     项目名/技术栈提取
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  KnowledgeBase      │  ③ 知识检索
│  - 向量相似度        │     余弦相似度计算
│  - 词汇增强          │     关键词匹配加分
│  - 混合排序          │     综合评分 Top-K
│  - 上下文扩展        │     基于历史关键词扩展检索
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  PromptBuilder      │  ④ 提示词构建
│  - 动态系统提示词    │     角色设定 + RAG 上下文
│  - 对话阶段感知      │     开场/前期/深入/后期
│  - 追问上下文注入    │     自动注入上下文摘要
│  - 检索上下文        │     Top-K 片段作为背景知识
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  LlmService         │  ⑤ LLM 生成
│  - OpenAI 兼容 API   │     流式/普通模式
│  - 降级到模板回答     │     API 未配置时
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  AuditService       │  ⑥ 审计记录
│  - 记录完整交互      │     输入/输出/上下文
└─────────┬───────────┘
          ▼
       返回响应（含引用来源）
```

### 知识库入库流程

```
上传文件
  │
  ▼
文本解析（PDF/Markdown/TXT）
  │
  ▼
文本清洗（去除噪音、规范化）
  │
  ▼
语义切片（260 字符上限，40 字符重叠）
  │
  ▼
Embedding 向量化（128 维哈希嵌入）
  │
  ▼
写入内存向量存储
```

---

## 🧩 模块详解

### 1. ChatService — 问答编排中心

ChatService 是整个系统的**中枢**，负责编排完整的 RAG 流水线：

- 维护基于 `conversationId` 的内存对话历史
- 调用 SafetyService 进行安全检查
- 调用 KnowledgeBaseService 检索相关知识
- 调用 PromptBuilder 构建完整提示词
- 调用 LlmService 生成回答
- 调用 AuditService 记录交互日志

**对话历史管理**：
- 滑动窗口机制：保留最近 20 轮对话（40 条消息），自动丢弃旧历史
- 追问上下文继承：检测追问模式（指代词、短消息），继承上一轮问题类型
- 智能关键词提取：从历史对话中提取项目名、技术栈等高价值关键词
- 上下文感知检索：基于对话历史扩展检索 query，提高召回率

### 2. KnowledgeBaseService — 知识库引擎

内存向量存储，核心能力：

- **文档入库**：解析 → 切片 → 向量化 → 存储
- **混合检索**：EmbeddingService 余弦相似度 + 词汇匹配增强
- **分块策略**：260 字符上限，40 字符重叠，保证语义连贯

### 3. EmbeddingService — 向量化引擎

默认使用基于哈希的嵌入实现（128 维），配置 `ZHIPU_BASE_URL`、`ZHIPU_API_KEY` 和 `ZHIPU_EMBEDDING_MODEL` 后优先调用 OpenAI 兼容的 `/embeddings` 接口：

- 支持 CJK（中日韩）和 ASCII 分词
- 支持 n-gram 特征提取
- 未配置或远程调用失败时自动降级为本地哈希向量，保证零依赖启动
- 向量维度由远程模型决定；切换模型后需要重新启动并重新加载知识库

### 4. LlmService — LLM 调用客户端

OpenAI 兼容 API 客户端：

- 支持任何 OpenAI 兼容接口（OpenAI、DeepSeek、通义千问、Ollama 等）
- 未配置时返回 `Optional.empty()`，触发 PromptBuilder 模板回答
- 可配置模型名称、温度、最大 token 数

### 5. AgentService — 智能分析引擎

完全基于 RAG 知识库的动态分析，无硬编码数据：

- **岗位匹配**：从知识库检索项目资料，提取技术关键词计算匹配度
- **项目查询**：从知识库检索并按来源分组，自动提取技术栈和项目亮点
- **技能分析**：基于知识库内容统计技能频次，生成能力图谱
- **面试回答**：从知识库检索相关项目，结合 STAR 法则生成个性化回答
- **LLM 增强**：配置 LLM 后可进行语义级别的深度分析
- **降级策略**：LLM 不可用时自动降级为基于知识库检索的模板回答

### 6. SafetyService — 安全防护

- **Prompt Injection 检测**：正则匹配中英文攻击模式
- **PII 脱敏**：自动识别手机号（11 位）、邮箱、身份证号（18 位）
- **可配置开关**：`block-on-injection` 和 `redact-sensitive` 独立控制

### 7. PromptBuilder — 提示词工程

为 LLM 构建结构化提示词，完全动态化：

- **动态系统提示词**：角色设定 + 回答风格指引 + RAG 检索结果注入
- **对话历史**：基于 conversationId 的多轮上下文
- **检索上下文**：Top-K 知识片段作为 LLM 背景知识
- **模板回答**：LLM 未配置时的降级方案（基于检索结果拼接）
- **对话阶段感知**：根据面试轮次动态调整回答策略（开场/前期/深入/后期）
- **追问上下文注入**：检测追问时自动注入上下文摘要，帮助 LLM 理解指代关系

---

## 📡 API 接口文档

### 聊天接口

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/chat` | 普通问答，返回完整回答 |
| `POST` | `/api/chat/stream` | JSON 请求的 SSE 流式问答，逐行返回 |
| `GET` | `/api/chat/history` | 获取指定会话的历史消息 |

**请求参数**：

```json
{
  "conversationId": "session-id",
  "message": "介绍一下你的项目经历",
  "mode": "rag",
  "jobTitle": "Java 后端开发工程师"
}
```

`conversationId` 和 `message` 必填，`mode` 可选，缺省为 `rag`；岗位工作区发起岗位面试时可传 `jobTitle`。流式请求使用 `Content-Type: application/json`，响应为 `text/event-stream`。

**响应示例**：

```json
{
  "answer": "我主要参与过以下项目...",
  "citations": [
    { "source": "resume.pdf", "chunk": "..." }
  ],
  "conversationId": "abc-123"
}
```

### Agent 分析接口

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/agent/analyze` | 岗位匹配分析（快速） |
| `POST` | `/api/agent/match-report` | 简历/JD 深度匹配报告（逐条 JD 要求 + 维度分 + 简历修改建议） |
| `POST` | `/api/agent/project-query` | 项目查询 |
| `POST` | `/api/agent/skill-analysis` | 技能分析 |
| `POST` | `/api/agent/interview-answer` | 面试回答生成 |
| `GET` | `/api/agent/tech-quote` | 每日技术语录 |
| `POST` | `/api/agent/interview-reviews` | 生成并存档一场模拟面试复盘 |
| `GET` | `/api/agent/interview-reviews` | 复盘存档列表（多场对比） |
| `GET` | `/api/agent/interview-reviews/{id}` | 单场复盘详情 |
| `DELETE` | `/api/agent/interview-reviews/{id}` | 删除复盘存档 |

**岗位匹配请求示例**：

```json
{
  "jobTitle": "Java 后端开发工程师",
  "jobDescription": "负责微服务架构设计与开发..."
}
```

**岗位匹配响应示例**：

```json
{
  "matchScore": 78,
  "strengths": ["Spring Boot 经验丰富", "有安全开发背景"],
  "gaps": ["缺少分布式系统经验", "K8s 经验不足"],
  "suggestions": ["建议补充微服务治理相关项目"]
}
```

### RAG 接口

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/rag/ingest` | 文本切片入库（调试用） |
| `GET` | `/api/rag/search` | 检索知识片段（调试用） |
| `GET` | `/api/rag/documents` | 文档列表（内置/上传标记） |
| `POST` | `/api/rag/documents` | 粘贴文本保存为文档（落盘持久化） |
| `DELETE` | `/api/rag/documents/{sourceName}` | 删除上传文档（内置文档只读） |
| `POST` | `/api/rag/upload` | 上传文件（PDF/TXT/MD）解析入库并落盘 |

### 用户档案接口

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `GET` | `/api/profile/templates` | 模板列表 |
| `POST` | `/api/profile/templates` | 新建模板（网申表单 + 简历文本） |
| `PUT` | `/api/profile/templates/{id}` | 更新模板 |
| `DELETE` | `/api/profile/templates/{id}` | 删除模板 |
| `POST` | `/api/profile/templates/{id}/activate` | 激活/切换模板（同步为「我的档案」） |
| `GET` | `/api/profile/active` | 当前激活模板 |
| `GET` | `/api/profile/templates/{id}/versions` | 版本历史列表 |
| `GET` | `/api/profile/templates/{id}/versions/{ts}` | 版本快照预览 |
| `POST` | `/api/profile/templates/{id}/versions/{ts}/rollback` | 回滚到指定版本 |
| `POST` | `/api/profile/upload` | 上传简历（PDF/TXT/MD）解析文本 |

### 导出接口

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/export/resume` | 导出简历优化版（PDF/Markdown，`X-Export-Format` 头反映实际格式） |
| `POST` | `/api/export/interview` | 导出面试问答集与复盘（PDF/Markdown） |

### 技术引航图接口

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `POST` | `/api/techguide/stream` | JSON 请求的 SSE 流式教学对话 |

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|:---|:---:|:---:|:---|
| `module` | String | ✅ | 模块 ID（java/spring/database/rag/agent/security/resume-agent/fundpilot） |
| `tag` | String | ❌ | 知识点标签 |
| `message` | String | ✅ | 用户消息 |
| `conversationId` | String | ✅ | 会话 ID，用于维护上下文 |

请求示例：

```json
{
  "module": "java",
  "tag": "HashMap",
  "message": "请解释这个知识点",
  "conversationId": "tech-session-id"
}
```

**SSE 事件类型**：

| 事件 | 数据格式 | 说明 |
|:---|:---|:---|
| `message` | 文本行 | 教学内容逐行推送 |
| `followups` | `问题1\|\|\|问题2\|\|\|问题3` | AI 生成的推荐追问（`\|\|\|` 分隔） |
| `done` | `[DONE]` | 流式输出结束 |

### 岗位工作区接口

岗位工作区将岗位 JD、匹配报告、定制简历建议和面试复盘关联到同一条岗位记录，数据持久化到 `backend/data/jobs/`。

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `GET` | `/api/jobs` | 岗位列表，支持 `status` 和 `q` 筛选 |
| `POST` | `/api/jobs` | 创建岗位 |
| `GET/PUT/DELETE` | `/api/jobs/{id}` | 查看、编辑、删除岗位 |
| `PATCH` | `/api/jobs/{id}/status` | 更新投递状态 |
| `POST` | `/api/jobs/{id}/match-report` | 生成并保存岗位匹配报告 |
| `POST/PUT` | `/api/jobs/{id}/resume-draft` | 生成或保存定制简历建议 |
| `GET/POST/DELETE` | `/api/jobs/{id}/reviews` | 查看、关联、解除面试复盘 |

### 审计接口

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `GET` | `/api/audit/logs` | 获取安全审计日志（需要管理员 Bearer Token） |

### 系统接口

| 方法 | 路径 | 说明 |
|:---:|:---|:---|
| `GET` | `/api/system/llm-config` | 查询 LLM 配置信息（API 密钥脱敏） |
| `GET` | `/api/system/health` | 返回服务、知识库和流式资源健康状态 |

**响应示例**：

```json
{
  "baseUrl": "https://api.openai.com/v1",
  "model": "gpt-4o-mini",
  "apiKey": "***",
  "temperature": 0.2,
  "maxTokens": 800,
  "llmAvailable": true
}
```

---

## 🛠️ 技术栈

<table>
<tr>
<td valign="top" width="50%">

**🖥️ 前端**
- Vue 3 + TypeScript
- Element Plus 组件库
- Axios HTTP 客户端
- SSE 流式通信
- Vite 构建工具
- 响应式布局 + 网格背景装饰
- 侧边栏导航 + 欢迎页设计

</td>
<td valign="top" width="50%">

**⚙️ 后端**
- Java 17
- Spring Boot 3
- Maven 依赖管理
- SseEmitter 流式推送
- 内存向量存储

</td>
</tr>
<tr>
<td valign="top" width="50%">

**🧠 AI 能力**
- LLM 对话生成（OpenAI 兼容）
- RAG 检索增强生成
- Agent 服务编排与分析
- 哈希嵌入（128 维）
- 混合检索排序

</td>
<td valign="top" width="50%">

**🚀 部署**
- Nginx 反向代理 + SSL
- systemd 进程管理
- 阿里云 ECS
- 前后端分离架构

</td>
</tr>
</table>

---

## 🌐 在线体验（部署环境信息）

如果部署环境仍保持在线，可直接访问；该地址和 Nginx/systemd 配置不包含在本地代码仓库中：

🔗 **[https://mentalczh66.xyz](https://mentalczh66.xyz)**

**部署架构**：
- 前端：Vue3 静态文件，由 Nginx 托管
- 后端：Spring Boot 运行于服务器，通过 Nginx 反向代理
- SSL：Let's Encrypt 免费证书，HTTPS 访问
- 服务器：阿里云 ECS + Nginx + systemd

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 | 用途 |
|:---:|:---:|:---|
| ☕ Java | 17+ | 后端运行 |
| 📦 Node.js | 18+ | 前端构建 |
| 🔧 Maven | 3.8+ | 后端依赖管理 |
| 🧠 LLM API | OpenAI 兼容 | AI 生成（可选，不配置也能跑） |

### 1️⃣ 克隆项目

```bash
git clone https://github.com/your-username/ResumeAgent.git
cd ResumeAgent
```

### 2️⃣ 配置 LLM（可选）

通过环境变量配置 LLM（不将密钥写入仓库）：

```yaml
resumeagent:
  openai:
    base-url: "${ZHIPU_BASE_URL:}"
    api-key: "${ZHIPU_API_KEY:}"
    chat-model: "${ZHIPU_CHAT_MODEL:gpt-4o-mini}"
```

也可以在启动前设置 `ZHIPU_BASE_URL`、`ZHIPU_API_KEY`、`ZHIPU_CHAT_MODEL`、`ZHIPU_TEMPERATURE` 和 `ZHIPU_MAX_TOKENS`。

<details>
<summary>📋 支持的 LLM 提供商（点击展开）</summary>

| 提供商 | base-url | 推荐模型 |
|:---|:---|:---|
| OpenAI | `https://api.openai.com/v1` | gpt-4o-mini |
| DeepSeek | `https://api.deepseek.com/v1` | deepseek-chat |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | qwen-turbo |
| Ollama（本地） | `http://localhost:11434/v1` | llama3 / qwen2 |

> 任何 OpenAI 兼容接口均可使用。

</details>

> 💡 **不配置 LLM 也能运行**：系统会回退到基于检索片段的模板回答，适合先验证整体流程。

### 3️⃣ 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端将在 http://localhost:8080 启动。

### 4️⃣ 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端将在 http://localhost:5173 启动，自动代理 `/api` 请求到后端。

### 5️⃣ 建立你的档案

1. 在 **"我的档案"** 标签页上传简历（PDF/TXT/MD 自动解析）并填写网申式信息表单，支持多模板管理、一键切换、版本历史/回滚。
2. 在 **"知识库"** 标签页补充项目文档、技术博客等资料。上传后系统自动完成：

```
文件上传 → 文本解析 → 语义切片 → 向量化 → 入库
```

### 6️⃣ 开始使用

启动后进入欢迎首页，页面两侧展示技术栈标签云和系统能力概览，中间提供快速提问入口和功能卡片导航：

- 💬 **模拟面试** — 以你的身份回答面试官问题，支持模拟面试和通用对话两种模式
- 🎯 **岗位匹配** — 输入目标岗位，分析匹配度与短板
- 🎤 **面试助手** — 输入面试问题，生成个性化回答
- 📊 **技能分析** — 自动梳理技术栈与能力图谱
- 📁 **项目查询 / 项目详情** — 深度检索项目经历
- 🧭 **技术引航图** — 交互式技术学习引导，点击知识点标签即刻开始，AI 自动生成推荐追问带你层层深入
- 📋 **我的档案** — 网申式信息表单 + 简历上传解析，多模板切换、版本历史/回滚
- 📝 **面试复盘** — 模拟面试后生成维度评分与逐题点评，多场成绩对比，可导出 PDF/Markdown
- 🟢 **LLM 状态** — 侧边栏绿点悬停查看当前 LLM 配置

> 💾 **数据持久化**：档案、上传文档、面试复盘均落盘到 `backend/data/`（`profiles/`、`knowledge/`、`reviews/`），重启自动加载，无需重复上传。

---

## ⚙️ 配置说明

后端配置位于 `backend/src/main/resources/application.yml`：

```yaml
resumeagent:
  admin-token: "${RESUME_AGENT_ADMIN_TOKEN:}" # 审计接口管理员 Token，未配置时拒绝访问
  allowed-origins: "${RESUME_AGENT_ALLOWED_ORIGINS:http://localhost:5173}"
  openai:
    base-url: "${ZHIPU_BASE_URL:}"       # OpenAI 兼容 API 端点
    api-key: "${ZHIPU_API_KEY:}"         # API 密钥，不写入仓库
    chat-model: "${ZHIPU_CHAT_MODEL:gpt-4o-mini}"
    temperature: "${ZHIPU_TEMPERATURE:0.2}"
    max-tokens: "${ZHIPU_MAX_TOKENS:2048}"
  security:
    block-on-injection: true  # 检测到注入时直接拒绝
    redact-sensitive: true    # 输入中的 PII 自动脱敏
  audit:
    enabled: true             # 记录所有交互日志
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

| 配置项 | 默认值 | 说明 |
|:---|:---:|:---|
| `openai.base-url` | `""` | API 端点地址，留空则使用模板回答 |
| `openai.api-key` | `""` | API 密钥 |
| `openai.chat-model` | `gpt-4o-mini` | 模型名称 |
| `openai.temperature` | `0.2` | 生成随机性，0 最确定，1 最随机（推荐 0.2-0.4） |
| `openai.max-tokens` | `2048` | 单次最大生成长度 |
| `security.block-on-injection` | `true` | 是否阻止注入攻击 |
| `security.redact-sensitive` | `true` | 是否脱敏 PII 信息 |
| `audit.enabled` | `true` | 是否启用审计日志 |
| `audit.max-entries` | `2000` | 内存中保留的最大审计记录数 |
| `stream.timeout-ms` | `300000` | 单次 SSE 流最大时长 |
| `stream.max-concurrent` | `16` | 最大并发流式请求数 |
| `session.max-conversations` | `1000` | 最大会话数 |
| `session.idle-timeout-minutes` | `30` | 会话空闲过期时间 |
| `session.max-history-messages` | `20` | 单会话历史消息窗口 |
| `rate-limit.requests-per-minute` | `30` | 单客户端每分钟 LLM 请求数 |

推荐通过环境变量配置敏感信息：

```bash
set ZHIPU_BASE_URL=https://open.bigmodel.cn/api/paas/v4
set ZHIPU_API_KEY=your-api-key
set RESUME_AGENT_ADMIN_TOKEN=your-admin-token
set RESUME_AGENT_ALLOWED_ORIGINS=https://your-domain.example
```

审计接口需要管理员请求头：

```http
Authorization: Bearer your-admin-token
```

---

## 📁 项目结构

```
ResumeAgent/
├── backend/                                    # Spring Boot 后端
│   ├── src/main/java/com/resumeagent/
│   │   ├── controller/                         # REST 控制器
│   │   │   ├── ChatController.java             #   聊天问答接口
│   │   │   ├── AgentController.java            #   Agent 分析接口（匹配报告/面试复盘）
│   │   │   ├── ProfileController.java          #   用户档案模板接口（多模板/版本回滚/简历上传）
│   │   │   ├── RagController.java              #   知识库接口（文档管理/文件上传）
│   │   │   ├── ExportController.java           #   导出接口（PDF/Markdown）
│   │   │   ├── AuditController.java            #   审计日志接口
│   │   │   ├── SystemController.java           #   系统配置接口（LLM 信息）
│   │   │   └── TechGuideController.java        #   技术引航图教学接口
│   │   ├── service/                            # 业务逻辑
│   │   │   ├── ChatService.java                #   问答编排（RAG 流水线）
│   │   │   ├── KnowledgeBaseService.java       #   知识库（两阶段检索）
│   │   │   ├── EmbeddingService.java           #   向量化（哈希嵌入）
│   │   │   ├── LlmService.java                 #   LLM 调用客户端（真流式）
│   │   │   ├── AgentService.java               #   Agent 分析引擎
│   │   │   ├── ProfileService.java             #   用户档案模板（JSON 持久化 + 版本）
│   │   │   ├── MatchReportService.java         #   简历/JD 深度匹配报告
│   │   │   ├── InterviewReviewService.java     #   模拟面试复盘（存档对比）
│   │   │   ├── ExportService.java              #   PDF/Markdown 导出（CJK 字体探测）
│   │   │   ├── KnowledgeDocumentService.java   #   上传文档落盘持久化
│   │   │   ├── PdfTextExtractor.java           #   PDF 简历文本抽取
│   │   │   ├── KnowledgeAutoLoader.java        #   内置知识自动入库
│   │   │   ├── SafetyService.java              #   安全防护（注入检测 + PII）
│   │   │   ├── PromptBuilder.java              #   提示词工程
│   │   │   ├── FollowupFilter.java             #   流式追问块过滤器
│   │   │   ├── DataDirs.java                   #   数据目录统一解析/迁移
│   │   │   ├── AuditService.java               #   审计日志
│   │   │   └── TechGuideService.java           #   技术引航图教学服务（追问提取+RAG检索）
│   │   ├── dto/                                # 数据传输对象
│   │   ├── model/                              # 数据模型
│   │   └── config/                             # 配置类
│   └── src/main/resources/
│       ├── application.yml                     # ⚙️ 应用配置
│       └── knowledge/                          # 内置知识库（resume/projects/blog）
├── frontend/                                   # Vue3 前端
│   ├── src/
│   │   ├── components/                         # 页面组件
│   │   │   ├── WelcomePanel.vue                #   欢迎首页（技术栈/能力概览）
│   │   │   ├── ProfilePanel.vue                #   我的档案（网申表单+简历上传+多模板）
│   │   │   ├── KnowledgePanel.vue              #   知识库管理（上传/粘贴/删除）
│   │   │   ├── ChatPanel.vue                   #   模拟面试（面试/通用双模式）
│   │   │   ├── JobMatchPanel.vue               #   岗位匹配（快速/深度报告双模式）
│   │   │   ├── ReviewPanel.vue                 #   面试复盘（多场对比+导出）
│   │   │   ├── InterviewPanel.vue              #   面试助手
│   │   │   ├── SkillAnalysisPanel.vue          #   技能分析
│   │   │   ├── ProjectQueryPanel.vue           #   项目查询
│   │   │   ├── ProjectDetailPanel.vue          #   项目详情
│   │   │   ├── TechGuidePanel.vue              #   技术引航图（引导追问+智能滚动）
│   │   │   ├── GuideModal.vue                  #   使用指南弹窗
│   │   │   ├── ThinkingIndicator.vue           #   思考中指示器
│   │   │   └── AuditPanel.vue                  #   审计日志（管理员）
│   │   ├── api/                                # API 客户端
│   │   │   ├── http.ts                         #   Axios 实例
│   │   │   ├── agent.ts                        #   Agent API
│   │   │   ├── profile.ts                      #   档案模板 API
│   │   │   ├── rag.ts                          #   知识库 API
│   │   │   ├── export.ts                       #   导出 API
│   │   │   └── audit.ts                        #   审计 API
│   │   ├── composables/                        # 组合式函数（会话/存储/Markdown）
│   │   ├── App.vue                             # 主应用（标签页路由）
│   │   └── main.ts                             # 入口文件
│   └── package.json
├── CLAUDE.md                                   # Claude Code 指导文件
└── README.md                                   # 📍 你在这里
```

---

## 🗺️ 开发计划

- [x] **Phase 1** — 基础聊天系统
  - Vue 聊天页面、Spring Boot 接口、LLM 调用、SSE 流式输出
- [x] **Phase 2** — RAG 知识库
  - 文档解析、文本切片、Embedding 向量化、向量检索、检索增强问答
- [x] **Phase 3** — 服务编排与分析能力
  - 岗位匹配、技能分析、安全防护、对话审计
- [x] **Phase 3.5** — RAG 全面化重构
  - 个人资料不硬编码，系统提示词和分析能力基于知识库动态构建
  - PromptBuilder 动态系统提示词，RAG 检索结果作为 LLM 背景知识
  - AgentService 所有 fallback 方法改为基于知识库检索生成
- [x] **Phase 3.6** — 前端 UI 重构
  - 欢迎首页重新设计：网格背景、技术栈标签云、能力概览侧边栏、功能卡片导航
  - 聊天页支持 RAG / 通用对话双模式切换
  - 侧边栏导航 + 响应式布局优化
- [x] **Phase 3.7** — LLM 状态指示器
  - 侧边栏绿点悬停弹出 LLM 配置详情卡片（模型、端点、密钥脱敏、温度、Token 上限）
  - 新增 `/api/system/llm-config` 接口，`LlmService.isAvailable()` 状态检测
  - 白色卡片设计风格与主页统一
- [x] **Phase 4** — 部署上线
  - Nginx 反向代理 + HTTPS（Let's Encrypt）
  - systemd 后端进程管理
  - 前端静态文件托管
  - 部署至阿里云 ECS，域名 mentalczh66.xyz
- [x] **Phase 4.5** — 对话质量优化
  - 对话历史滑动窗口机制（最近 20 轮），防止 token 溢出
  - 追问上下文继承：自动检测追问模式，继承上一轮问题类型
  - 对话阶段感知：根据面试进度动态调整回答策略
  - 智能关键词提取：从历史对话中提取项目名和技术栈，优化检索相关性
  - LLM 参数优化：temperature 0.7→0.3，max_tokens 2048→1024
- [x] **Phase 5** — 模拟面试优化
  - 智能项目讲述策略：区分列举类和深挖类问题的回答结构
  - 重点项目优先：优先讲述 FundPilot-AI 和 ResumeAgent 两个核心项目
  - STAR 法则应用：深挖类问题自动应用 STAR 法则（情境/任务/行动/结果）
  - 防文档照搬：强调用自然语言重新组织，体现思考和理解
  - 项目描述准确性：确保项目描述准确，避免误导面试官
- [x] **Phase 5.5** — 技术引航图
  - 8 大学习模块：Java基础、Spring生态、数据库与缓存、RAG核心技术、Agent架构、安全机制、实战项目×2
  - 知识点标签交互式教学，AI 导师角色（生活类比 + ASCII图解 + 记忆口诀 + 代码实战）
  - SSE 流式教学输出，多轮对话上下文保持
  - RAG 检索辅助教学，保证内容准确性
  - 超出范围的问题自动引导回技术主题
- [x] **Phase 5.6** — 技术引航图交互升级
  - **主动引导式学习**：AI 回复末尾自动生成 2-3 个可点击的「推荐追问」胶囊按钮，层层深入无需手动提问
  - 后端 Prompt 工程：系统提示词强制要求 LLM 输出 `<FOLLOWUP>` 标签格式的追问，自动提取并分离
  - 智能自动滚动：流式输出时检测用户滚动行为，向上翻页暂停自动滚动，5 秒无操作后恢复
  - 代码块增强：深色主题 + 语言标签角标，ASCII 图示自动检测并应用等宽渲染
  - 清空对话二次确认气泡，防止误触丢失学习记录
  - 侧边栏标签高亮（accent 背景 + 左侧蓝色竖条），切换模块自动折叠前一个
- [x] **Phase 5.7** — 模拟面试回答质量保障
  - 强制 400 字以上回答要求，系统提示词明确字数下限
  - 后处理重试机制：回答长度不足时自动重新请求并附加字数提示
  - 双重约束确保 LLM 不会偷懒，提升面试模拟质量
- [x] **Phase 5.8** — 个人资产与真流式升级
  - 我的档案：网申式信息表单 + 简历上传解析（PDF/TXT/MD），多模板管理、一键切换、版本历史/回滚
  - 档案以「我的档案」为第一事实来源最优先注入检索；档案切换自动清空旧身份对话历史（防幻觉）
  - 深度匹配报告（逐条 JD 要求 + 维度分 + 简历修改建议）与模拟面试复盘（维度评分 + 逐题点评，多场对比）
  - PDF/Markdown 导出（PDFBox 3.x + CJK 字体自动探测，无中文字体降级 Markdown）
  - 检索层重构：倒排索引粗排（中文 2-gram）+ 向量/词汇精排两阶段检索
  - SSE 真流式：直接转发 LLM `stream: true` 增量 delta；知识库/档案/复盘 JSON 落盘持久化（`backend/data/`）
  - 个人资料不硬编码：检索路由与关键词基于知识库元数据/IDF 动态生成
- [ ] **Phase 6** — 优化与扩展
  - 替换 Milvus 向量数据库
  - 优化检索算法（重排序、多路召回）
  - 完善 Docker 部署
  - 性能优化和压力测试

---

## 💡 项目亮点

| 亮点 | 说明 |
|:---|:---|
| 🧠 **RAG 驱动** | 基于个人知识库动态生成回答，引用可溯源，大幅降低幻觉 |
| 🚫 **个人资料不硬编码** | 个人经历从知识库检索，系统提示词和分析结果基于当前资料动态构建 |
| 🤖 **Agent 能力** | 岗位匹配、技能分析、面试辅助，不只是简单问答 |
| 🛡️ **安全优先** | Prompt Injection 检测 + PII 脱敏，Agent 安全设计 |
| 📡 **流式体验** | SSE 逐字输出，打字机效果，实时感知生成过程 |
| 🔌 **优雅降级** | LLM 未配置时自动回退到基于检索的模板回答，零成本验证流程 |
| 🌐 **已部署上线** | Nginx + systemd 架构，HTTPS 访问，域名 mentalczh66.xyz |
| 📋 **全链路审计** | 用户输入 → 检索 → 生成 → 输出，完整可追溯 |
| 🧹 **智能历史管理** | 滑动窗口机制自动清理旧对话，防止 token 溢出，保持上下文连贯 |
| 🎯 **追问上下文继承** | 自动检测追问模式，继承上一轮问题类型，保持对话连贯性 |
| 📊 **对话阶段感知** | 根据面试进度动态调整回答策略（开场/前期/深入/后期） |
| 🔍 **智能关键词提取** | 从对话历史中提取高价值关键词（项目名、技术栈），优化检索相关性 |
| 🎯 **智能项目讲述策略** | 区分列举类和深挖类问题，优先讲述重点项目，回答有重点、有层次、有深度 |
| 📝 **STAR 法则回答** | 面试深挖类问题自动应用 STAR 法则，包含情境、任务、行动、结果 |
| 📏 **回答质量保障** | 强制 400 字以上回答，系统提示词 + 后处理双重约束，避免 LLM 偷懒 |
| 🧭 **技术引航图** | 交互式学习引导系统，8 大模块覆盖核心知识点，AI 导师风格教学 + 主动引导追问，层层深入无需手动提问 |

---

## 📝 简历描述（可直接使用）

> **ResumeAgent** — 基于 RAG 的模拟面试 AI 系统
>
> 基于 Spring Boot + Vue3 开发模拟面试 AI 系统，通过 RAG 技术构建个人知识库，AI 以候选人身份回答面试官问题。个人资料均从知识库动态检索，全程第一人称回答，支持向量检索、流式对话、岗位匹配分析、技能分析和面试回答生成，并针对 Agent 场景设计 Prompt Injection 防护机制。生产环境采用 Nginx + HTTPS 部署，可在线体验。

---

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。

---

<div align="center">

**如果觉得有用，点个 ⭐ 支持一下吧！**

</div>
