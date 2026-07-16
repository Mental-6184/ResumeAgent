<template>
  <div class="panel">
    <div class="panel-header" :class="{ 'has-back': viewMode === 'detail' }">
      <span v-if="viewMode === 'detail'" class="back-btn" @click="backToList">← 返回</span>
      {{ viewMode === 'list' ? '项目详情' : selectedProject?.name }}
    </div>

    <!-- 项目列表视图 -->
    <div v-if="viewMode === 'list'" class="panel-body">
      <div class="project-grid">
        <div
          v-for="(project, index) in projects"
          :key="project.id"
          class="project-card"
          :style="{ animationDelay: `${index * 60}ms` }"
          @click="openProject(project)"
        >
          <div class="project-card-icon">{{ project.icon }}</div>
          <div class="project-card-info">
            <div class="project-card-name">{{ project.name }}</div>
            <div class="project-card-desc">{{ project.shortDesc }}</div>
            <div class="project-card-tags">
              <span v-for="tag in project.tags" :key="tag" class="mini-tag">{{ tag }}</span>
            </div>
          </div>
          <div class="project-card-arrow">→</div>
        </div>
      </div>
    </div>

    <!-- 项目详情视图 -->
    <div v-else class="panel-body detail-view">
      <div class="detail-header">
        <div class="detail-icon">{{ selectedProject?.icon }}</div>
        <div class="detail-meta">
          <div class="detail-name">{{ selectedProject?.name }}</div>
          <div class="detail-desc">{{ selectedProject?.shortDesc }}</div>
          <div class="detail-tags">
            <span v-for="tag in selectedProject?.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>
        </div>
      </div>
      <div class="detail-content" v-html="renderedContent"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

interface Project {
  id: string
  name: string
  icon: string
  shortDesc: string
  tags: string[]
  readme: string
}

const viewMode = ref<'list' | 'detail'>('list')
const selectedProject = ref<Project | null>(null)

const projects: Project[] = [
  {
    id: 'mentalblog',
    name: 'MentalBlog 个人博客系统',
    icon: '📝',
    shortDesc: '功能完整的个人博客系统，支持文章管理、分类标签、评论互动、图库、用户权限等',
    tags: ['Vue 3', 'Spring Boot 4', 'MySQL', 'Redis', 'Element Plus'],
    readme: `# MentalBlog 个人博客系统

一个功能完整的个人博客系统，采用前后端分离架构。支持文章管理、分类标签、评论互动、图片图库、用户权限管理等完整博客功能。

## 技术栈

**后端**
- Spring Boot 4.0 + MyBatis-Plus
- Spring Security + JWT 认证
- MySQL 8 + Redis
- Knife4j API 文档

**前端**
- Vue 3 + Composition API + TypeScript
- Vite 8 构建工具
- Vue Router + Pinia
- Element Plus UI 组件库
- ECharts 数据可视化

## 功能特性

### 前台功能
- 文章列表展示（分类/标签筛选、分页）
- 文章详情（Markdown 渲染、目录导航 TOC、阅读进度条）
- 分类与标签浏览、时间轴归档、全文搜索
- 评论系统（嵌套回复）、点赞/收藏互动
- 图库系统（上传/浏览/标签筛选/下载/评论）
- 深色模式、RSS 订阅

### 后台管理
- 仪表盘（数据统计、趋势图表）
- 文章/分类/标签/评论/用户/图库管理
- 友链管理、系统设置

### 设计特色
- Indigo/Cyan 蓝白科技配色
- Glassmorphism 毛玻璃效果
- 响应式布局、页面过渡动画`
  },
  {
    id: 'gazeforge',
    name: 'GazeForge 视觉追踪训练平台',
    icon: '👁️',
    shortDesc: '基于多目标追踪游戏的注意力训练应用，66个关卡、自由训练、专注力测试',
    tags: ['React 19', 'TypeScript', 'Tailwind CSS', 'Matter.js', 'Framer Motion'],
    readme: `# GazeForge — 视觉追踪训练平台

一个基于 React + TypeScript 的 Web 端视觉注意力追踪训练应用。通过多目标追踪（MOT）游戏，帮助用户提升注意力广度、动态追踪能力和抗干扰能力。

## 核心特性

- **闯关模式**：6 个章节 × 11 关（含 Boss 关），共 66 关，难度逐步递增
- **自由训练**：自定义小球数量、速度、干扰类型等参数
- **专注力测试**：4 个测试维度，生成雷达图报告
- **成就系统**：22 个成就，覆盖技巧、探索、极限三大类别
- **无障碍支持**：深色/浅色主题、色盲模式

## 技术栈

| 类别 | 技术 | 用途 |
|------|------|------|
| 框架 | React 19 | UI 构建 |
| 语言 | TypeScript | 类型安全 |
| 样式 | Tailwind CSS | 原子化 CSS |
| 物理 | Matter.js | 2D 物理引擎 |
| 动画 | Framer Motion | 页面过渡与动画 |
| 图表 | Recharts | 测试报告图表 |
| 存储 | Dexie | IndexedDB 封装 |

## 游戏玩法

游戏分为 5 个阶段：准备 → 提示 → 运动 → 选择 → 结果。难度通过球数、速度、干扰效果等维度递进。`
  },
  {
    id: 'fundpilot',
    name: 'FundPilot AI 基金智能分析平台',
    icon: '📊',
    shortDesc: '基于多 Agent AI 架构的基金投资分析平台，支持智能对话、组合管理与报告生成',
    tags: ['Vue 3', 'Spring Boot 3', 'Spring AI', 'MyBatis-Plus', 'ECharts'],
    readme: `# FundPilot AI · 基金智能分析平台

基于多 Agent AI 架构的基金投资智能分析平台，支持基金搜索、组合管理、智能对比、AI 顾问对话与自动报告生成。

## 功能特性

### 基金数据
- 基金搜索、详情、净值走势、业绩指标
- 多基金横向对比，收益率 & 风险指标雷达图

### 组合管理
- 投资组合 CRUD，设置持仓权重
- 收益走势、资产配置饼图、行业分布分析

### AI 智能
- ChatGPT 风格对话界面，支持上下文记忆
- 两阶段意图路由（关键词正则 → LLM 分类）
- 4 个专家 Agent：分析、对比、推荐、诊断

## AI 多 Agent 架构

采用编排器 + 专家 Agent 的分层架构：
- FundAdvisorAgent（编排器）
- FundAnalysisAgent（单只基金分析）
- FundComparisonAgent（多基金对比）
- FundRecommendationAgent（基金推荐）
- PortfolioDiagnosisAgent（组合诊断）

配合 8 个 Tool Calling 函数实现数据查询与分析。`
  },
  {
    id: '3d-particles',
    name: '3D 粒子系统',
    icon: '✨',
    shortDesc: '基于 Three.js 和 MediaPipe Hands 的交互式 3D 粒子系统，支持手势控制',
    tags: ['Three.js', 'MediaPipe', 'GLSL Shader', 'HTML/CSS/JS'],
    readme: `# 3D 粒子系统

基于 Three.js 和 MediaPipe Hands 的交互式 3D 粒子系统，支持手势控制和多种粒子效果。

## 功能

### 粒子模板
5 种预设形状：爱心、花朵、土星、佛像、烟花

### 颜色自定义
- 颜色选择器 + 8 种预设色块
- 彩虹模式：每个粒子根据位置分配不同色相

### 手势交互
通过摄像头检测手部动作，实时控制粒子效果：
- 张开手掌：粒子向外爆炸扩散 + 螺旋扭转
- 握紧拳头：粒子向心收缩 + 涡流旋绕
- 方向挥动：粒子整体推移

## 技术栈

| 技术 | 用途 |
|------|------|
| Three.js v0.160.0 | 3D 渲染引擎 |
| MediaPipe Hands v0.4 | 手部关键点检测 |
| GLSL Shader | 粒子着色 |
| 原生 HTML/CSS/JS | 无框架，单文件 |

所有依赖通过 CDN 加载，无需安装。`
  },
  {
    id: 'pubg-wheel',
    name: 'PUBG 武器幸运转盘',
    icon: '🎯',
    shortDesc: '基于 Spring Boot 的 PUBG 武器随机抽取 Web 应用，支持转盘动画和抽取历史',
    tags: ['Spring Boot 3', 'Java 17', 'Canvas API', 'MySQL'],
    readme: `# PUBG 武器幸运转盘

一个基于 Spring Boot + 原生前端的 PUBG 武器随机抽取 Web 应用。用户可以选择武器类型，通过转盘动画随机抽取武器，并查看抽取历史记录。

## 功能特性

- 武器分类选择 — 按类型筛选，支持全选/清空
- 动画转盘抽取 — Canvas 绘制的可视化转盘，平滑旋转动画
- 抽取历史记录 — 记录每次抽取结果与时间戳
- 音效反馈 — 旋转音效 + 结束音效
- 响应式设计 — 适配桌面端与移动端

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.1.0, Java 17, Spring Data JPA |
| 前端 | HTML5, CSS3, JavaScript, Canvas API |
| 数据库 | MySQL 8.0 |

已部署上线：pubg.mentalczh66.xyz`
  },
  {
    id: 'sec-sonar',
    name: 'Sec-Sonar 安全态势感知平台',
    icon: '🛡️',
    shortDesc: '分布式 Web 安全态势感知与 RASP 自防御平台，结合主动扫描与被动防护',
    tags: ['Spring Boot 3', 'Vue 3', 'Python', 'Elasticsearch', 'RabbitMQ'],
    readme: `# Sec-Sonar

分布式 Web 安全态势感知与 RASP 自防御平台，结合主动漏洞扫描与被动 RASP 运行时防护。

## 架构

- **控制中心**（Spring Boot 3.2）：资产管理、扫描任务调度、RASP 日志接收
- **扫描引擎**（Python 3.10+）：RabbitMQ 消费者，布尔盲注 SQLi + OAST RCE 检测
- **RASP 探针**（Java Agent）：ByteBuddy 拦截 ProcessBuilder.start()，四层防御体系
- **前端大屏**（Vue 3）：赛博朋克风格安全态势感知大屏

## 四层防御体系

1. 全局异常保护（任何异常 → 放行）
2. 输入校验（空命令检查）
3. 命令重建（List → 完整命令字符串）
4. 上下文关联分析 + 威胁检测
   - 34 个危险命令关键词
   - 15 个反弹 Shell 正则模式
   - 9 个命令注入正则模式

## 前端大屏

- Matrix Rain 背景动画、霓虹绿发光效果
- 实时攻击流、网络拓扑图、系统健康监控`
  },
  {
    id: 'shotnote',
    name: 'ShotNote 截图标注工具',
    icon: '📸',
    shortDesc: '基于 Electron 的 Windows 截图标注工具，支持截图、标注、OCR 与导出',
    tags: ['Electron', 'Canvas', 'tesseract.js', 'Node.js'],
    readme: `# ShotNote

一款基于 Electron 构建的 Windows 截图标注工具，专注于更快的截图、批注、识别与导出体验。

## 功能特性

- 全局快捷键截图（Ctrl+Alt+Shift+S）
- 多屏幕区域截图
- 丰富的标注工具：画笔、高亮、矩形、箭头、文字、马赛克、模糊
- 画布缩放与视图控制
- OCR 文字识别（tesseract.js）
- 导出 PNG / 复制到剪贴板
- 历史记录管理
- 本地图片二次标注
- 系统托盘常驻

## 技术栈

- Electron + screenshot-desktop
- tesseract.js（OCR）
- electron-builder（打包）

## 使用场景

- 产品经理整理需求截图并添加说明
- 测试同学提交 Bug 时标出异常位置
- 开发同学记录报错信息
- 教学演示中的步骤说明`
  },
  {
    id: 'pocketledger',
    name: 'PocketLedger 记账小程序',
    icon: '💰',
    shortDesc: '轻量本地优先的微信小程序记账应用，支持预算、订阅管理、多币种换算',
    tags: ['微信小程序', 'JavaScript', 'WXML', 'WXSS'],
    readme: `# PocketLedger

一个主打轻量、本地优先、可快速记账的微信小程序项目。

## 功能亮点

- 自定义数字键盘，提升记账录入效率
- 收入/支出双类型记账
- 月度预算设置与预算使用进度展示
- 订阅项目管理与到期提醒
- 多币种订阅金额录入与人民币换算
- 账户资产管理，追踪钱在哪里
- 待报销标记与退款冲抵
- 月度统计、分类占比、趋势分析
- 数据导出 CSV / JSON

## 技术栈

- 微信小程序原生框架
- JavaScript / WXML / WXSS
- 微信本地存储 API

## 数据设计

- Record（记账记录）：类型、金额、分类、日期、账户关联
- Subscription（订阅项目）：周期、币种、提醒、自动扣费
- Account（账户）：余额、类型、计入总资产控制`
  },
  {
    id: 'garage-system',
    name: '智能车库管理系统',
    icon: '🅿️',
    shortDesc: '基于 Spring Boot + MyBatis-Plus 的一体化车库管理平台，已获国家软件著作权',
    tags: ['Spring Boot', 'MyBatis-Plus', 'MySQL', 'Docker', 'Vue'],
    readme: `# 智能车库管理系统

基于 Spring Boot + MyBatis-Plus + MySQL 开发的一体化车库管理平台，已获国家软件著作权（登记号：2025SR0936029）。

## 功能特性

- 车辆进出管理与自动识别
- 多策略计费规则引擎设计（按时段、按时长、阶梯计费）
- 车位管理与实时状态监控
- 用户管理与权限控制
- 数据统计与报表导出

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot |
| ORM 框架 | MyBatis-Plus |
| 数据库 | MySQL |
| 容器化 | Docker |
| 前端 | Vue |

## 项目亮点

- 多策略计费规则引擎设计，支持灵活配置不同计费规则
- 数据库建模与物理表设计，规范化数据结构
- Docker 容器化部署，一键启动
- 已获国家软件著作权`
  },
  {
    id: 'resumeagent',
    name: 'ResumeAgent 简历智能助手',
    icon: '🤖',
    shortDesc: '基于 RAG 的个人简历 AI 助手系统，支持智能问答、岗位匹配和面试准备',
    tags: ['Spring Boot 3', 'Vue 3', 'RAG', 'Agent', 'OpenAI API'],
    readme: `# ResumeAgent · 简历智能助手

基于 RAG（检索增强生成）架构的个人简历 AI 助手系统，采用 Spring Boot 3 + Vue3 构建。将个人文档导入知识库，使用 RAG 检索相关上下文，通过自然语言对话生成准确、可追溯的回答。

## 功能特性

### 智能问答
- 基于知识库的 RAG 检索问答
- SSE 流式输出，逐行显示回答
- 对话历史上下文记忆
- 回答引用溯源

### Agent 分析
- 岗位匹配分析 — 关键词匹配 + LLM 语义分析
- 技能画像分析 — 自动提取核心技能和技术优势
- 项目智能查询 — 按名称或技术栈筛选项目
- 面试回答生成 — 基于真实经历的 STAR 法则回答

### 安全防护
- Prompt Injection 检测（中英文模式）
- PII 脱敏（手机号、邮箱、身份证号）
- 安全审计日志

## 架构

\`\`\`
用户输入 → SafetyService (注入/PII 检查)
        → KnowledgeBaseService.search() (向量 + 词汇检索)
        → PromptBuilder (系统提示词 + 历史 + 上下文)
        → LlmService (OpenAI 兼容 API，或回退)
        → AuditService (记录交互)
        → 响应 (含引用)
\`\`\`

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3, Java 17, Maven |
| 前端 | Vue 3, TypeScript, Vite, Element Plus |
| AI | RAG 检索, Agent 工具调用, OpenAI 兼容 API |
| 安全 | 正则注入检测, PII 脱敏, 审计日志 |`
  },
  {
    id: 'book-system',
    name: '图书信息管理系统',
    icon: '📚',
    shortDesc: '基于 Spring Boot 的图书管理系统，实现用户管理、角色权限和图书 CRUD',
    tags: ['Spring Boot 2', 'MyBatis', 'Thymeleaf', 'MySQL'],
    readme: `# 图书信息管理系统

基于 Spring Boot 2.7.18 + MyBatis + Thymeleaf 构建的图书信息管理系统，实现了完整的用户管理、角色权限控制和图书 CRUD 功能。

## 功能模块

**用户模块**
- 用户注册、用户登录（根据角色跳转不同首页）

**管理员功能**
- 图书查询、添加、编辑、删除
- 支持封面图片上传

**普通用户功能**
- 单条件查询（书名、作者、ISBN）
- 多条件联合筛选

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.18 |
| ORM 框架 | MyBatis 2.3.1 |
| 数据库 | MySQL 8.0+ |
| 前端模板 | Thymeleaf + HTML/CSS |
| 构建工具 | Maven |

## 命名规范

严格遵循 czh- 前缀命名规范：Java 类名 czh 开头驼峰，数据库表 czh_ 开头下划线分隔。`
  }
]

function openProject(project: Project) {
  selectedProject.value = project
  viewMode.value = 'detail'
}

function backToList() {
  viewMode.value = 'list'
  selectedProject.value = null
}

const renderedContent = computed(() => {
  if (!selectedProject.value) return ''
  return renderMarkdown(selectedProject.value.readme)
})

function renderMarkdown(md: string): string {
  let html = md

  // 代码块
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_, lang, code) => {
    return `<pre class="md-code-block"><code>${escapeHtml(code.trim())}</code></pre>`
  })

  // 表格
  html = html.replace(/(\|.+\|\n)+/g, (match) => {
    const rows = match.trim().split('\n').filter(r => r.trim())
    if (rows.length < 2) return match

    // 检测分隔行
    const separatorIdx = rows.findIndex(r => /^\|[\s\-:|]+\|$/.test(r.trim()))
    if (separatorIdx < 1) return match

    const headerRows = rows.slice(0, separatorIdx)
    const bodyRows = rows.slice(separatorIdx + 1)

    const parseRow = (row: string) =>
      row.split('|').slice(1, -1).map(c => c.trim())

    let table = '<div class="md-table-wrap"><table class="md-table">'
    table += '<thead>'
    headerRows.forEach(r => {
      table += '<tr>' + parseRow(r).map(c => `<th>${inlineFormat(c)}</th>`).join('') + '</tr>'
    })
    table += '</thead><tbody>'
    bodyRows.forEach(r => {
      table += '<tr>' + parseRow(r).map(c => `<td>${inlineFormat(c)}</td>`).join('') + '</tr>'
    })
    table += '</tbody></table></div>'
    return table
  })

  // 标题
  html = html.replace(/^#### (.+)$/gm, '<h4 class="md-h4">$1</h4>')
  html = html.replace(/^### (.+)$/gm, '<h3 class="md-h3">$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2 class="md-h2">$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1 class="md-h1">$1</h1>')

  // 分隔线
  html = html.replace(/^---$/gm, '<hr class="md-hr">')

  // 无序列表
  html = html.replace(/(^- .+$(\n)?)+/gm, (match) => {
    const items = match.trim().split('\n').map(line => {
      const content = line.replace(/^- /, '')
      return `<li>${inlineFormat(content)}</li>`
    }).join('')
    return `<ul class="md-ul">${items}</ul>`
  })

  // 有序列表
  html = html.replace(/(^\d+\. .+$(\n)?)+/gm, (match) => {
    const items = match.trim().split('\n').map(line => {
      const content = line.replace(/^\d+\. /, '')
      return `<li>${inlineFormat(content)}</li>`
    }).join('')
    return `<ol class="md-ol">${items}</ol>`
  })

  // 段落（连续非空行）
  html = html.replace(/^(?!<[a-z/])((?!^\s*$).+)$/gm, (line) => {
    if (line.trim() === '') return ''
    return `<p class="md-p">${inlineFormat(line)}</p>`
  })

  // 清理多余空行
  html = html.replace(/\n{3,}/g, '\n\n')

  return html
}

function inlineFormat(text: string): string {
  let s = text
  // 加粗
  s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  // 斜体
  s = s.replace(/\*(.+?)\*/g, '<em>$1</em>')
  // 行内代码
  s = s.replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>')
  // 链接
  s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" class="md-link">$1</a>')
  return s
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
</script>

<style scoped>
/* ─── Project Grid ─── */
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
}

.project-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg);
  cursor: pointer;
  transition: all 0.2s ease;
  animation: cardFadeIn 0.35s ease both;
}

@keyframes cardFadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.project-card:hover {
  border-color: var(--text);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}

.project-card-icon {
  font-size: 28px;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
  border-radius: 10px;
}

.project-card-info {
  flex: 1;
  min-width: 0;
}

.project-card-name {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-card-desc {
  font-size: 12.5px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.mini-tag {
  display: inline-block;
  padding: 1px 7px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-secondary);
}

.project-card-arrow {
  flex-shrink: 0;
  font-size: 16px;
  color: var(--text-muted);
  transition: transform 0.2s ease, color 0.2s ease;
}

.project-card:hover .project-card-arrow {
  color: var(--text);
  transform: translateX(3px);
}

/* ─── Detail View ─── */
.detail-view {
  animation: detailSlideIn 0.3s ease;
}

@keyframes detailSlideIn {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.back-btn {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color 0.15s;
  margin-right: 8px;
}

.back-btn:hover {
  color: var(--text);
}

.panel-header.has-back::before {
  content: '' !important;
  display: none;
}

.detail-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding-bottom: 20px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--border);
}

.detail-icon {
  font-size: 36px;
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
  border-radius: 14px;
  flex-shrink: 0;
}

.detail-meta {
  flex: 1;
}

.detail-name {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 6px;
}

.detail-desc {
  font-size: 13.5px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 10px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* ─── Markdown Content ─── */
.detail-content {
  line-height: 1.8;
  color: var(--text);
}

.detail-content :deep(.md-h1) {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  margin: 28px 0 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
  color: var(--text);
}

.detail-content :deep(.md-h1:first-child) {
  margin-top: 0;
}

.detail-content :deep(.md-h2) {
  font-family: var(--font-mono);
  font-size: 17px;
  font-weight: 600;
  margin: 24px 0 12px;
  color: var(--text);
}

.detail-content :deep(.md-h3) {
  font-family: var(--font-mono);
  font-size: 14.5px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: var(--text);
}

.detail-content :deep(.md-h4) {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  margin: 16px 0 8px;
  color: var(--text-secondary);
}

.detail-content :deep(.md-p) {
  margin: 8px 0;
  font-size: 14px;
  color: var(--text);
}

.detail-content :deep(.md-ul),
.detail-content :deep(.md-ol) {
  margin: 8px 0;
  padding-left: 22px;
}

.detail-content :deep(.md-ul li),
.detail-content :deep(.md-ol li) {
  margin: 4px 0;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.detail-content :deep(.md-hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 24px 0;
}

.detail-content :deep(.md-code-block) {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 14px 16px;
  overflow-x: auto;
  margin: 12px 0;
}

.detail-content :deep(.md-code-block code) {
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--text);
  line-height: 1.6;
}

.detail-content :deep(.md-inline-code) {
  font-family: var(--font-mono);
  font-size: 12.5px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 3px;
  padding: 1px 5px;
  color: var(--accent);
}

.detail-content :deep(.md-link) {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.detail-content :deep(strong) {
  font-weight: 600;
  color: var(--text);
}

.detail-content :deep(em) {
  font-style: italic;
}

/* ─── Markdown Table ─── */
.detail-content :deep(.md-table-wrap) {
  overflow-x: auto;
  margin: 14px 0;
  border: 1px solid var(--border);
  border-radius: 8px;
}

.detail-content :deep(.md-table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.detail-content :deep(.md-table th) {
  font-family: var(--font-mono);
  font-weight: 600;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text);
  background: var(--bg-secondary);
  padding: 10px 14px;
  text-align: left;
  border-bottom: 1px solid var(--border);
}

.detail-content :deep(.md-table td) {
  padding: 9px 14px;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-light);
}

.detail-content :deep(.md-table tr:last-child td) {
  border-bottom: none;
}

.detail-content :deep(.md-table tr:hover td) {
  background: var(--bg-hover);
}
</style>
