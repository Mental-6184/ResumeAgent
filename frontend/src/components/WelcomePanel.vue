<template>
  <div class="welcome-container">
    <!-- 背景装饰 -->
    <div class="welcome-bg">
      <div class="bg-grid"></div>
      <div class="bg-glow"></div>
    </div>

    <!-- 左侧装饰：技术关键词 -->
    <div class="side-panel side-left" :class="{ 'visible': isReady }">
      <div class="side-section">
        <div class="side-label">技术栈</div>
        <div class="side-tags">
          <span v-for="t in techTags" :key="t" class="side-tag">{{ t }}</span>
        </div>
      </div>
      <div class="side-section">
        <div class="side-label">工作流</div>
        <div class="side-flow">
          <div v-for="(s, i) in pipeline" :key="i" class="flow-step">
            <span class="flow-num">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="flow-text">{{ s }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧装饰：系统能力 -->
    <div class="side-panel side-right" :class="{ 'visible': isReady }">
      <div class="side-section">
        <div class="side-label">核心能力</div>
        <div class="side-stats">
          <div v-for="s in stats" :key="s.label" class="stat-item">
            <span class="stat-icon">{{ s.icon }}</span>
            <div class="stat-info">
              <span class="stat-value">{{ s.value }}</span>
              <span class="stat-label">{{ s.label }}</span>
            </div>
          </div>
        </div>
      </div>
      <div class="side-section">
        <div class="side-label">支持格式</div>
        <div class="side-tags">
          <span v-for="f in fileFormats" :key="f" class="side-tag">{{ f }}</span>
        </div>
      </div>
    </div>

    <!-- 主欢迎区域 -->
    <div class="welcome-content" :class="{ 'visible': isReady }">
      <!-- 主标题区 -->
      <div class="hero-section">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          <span>AI-Powered</span>
        </div>
        <h1 class="welcome-title">
          <span class="title-main">Resume</span><span class="title-accent">Agent</span>
        </h1>
        <p class="welcome-desc">
          基于 RAG 的模拟面试助手<br>
          <span class="desc-highlight">导入简历 → 知识检索 → 以你的身份回答</span>
        </p>
      </div>

      <!-- 技术话语 -->
      <div class="tech-quote" v-if="techQuote">
        <div class="quote-icon">❝</div>
        <div class="quote-body">
          <p class="quote-text">{{ techQuote.quote }}</p>
          <div class="quote-meta">
            <span class="quote-author">{{ techQuote.author }}</span>
            <span class="quote-divider">·</span>
            <span class="quote-category">{{ techQuote.category }}</span>
          </div>
        </div>
      </div>
      <div class="tech-quote-loading" v-else-if="quoteLoading">
        <span class="loading-dots">加载中</span>
      </div>

      <!-- 快速提问 -->
      <div class="welcome-actions">
        <div class="action-label">快速开始</div>
        <div class="action-chips">
          <button
            v-for="(q, i) in questions"
            :key="q"
            class="action-chip"
            @click="handleQuestion(q)"
          >
            <span class="chip-index">{{ String(i + 1).padStart(2, '0') }}</span>
            <span>{{ q }}</span>
          </button>
        </div>
      </div>

      <!-- 功能入口 -->
      <div class="welcome-features">
        <button
          v-for="f in features"
          :key="f.panel"
          class="feature-card"
          @click="handleFeature(f.panel)"
        >
          <span class="feature-icon">{{ f.icon }}</span>
          <div class="feature-info">
            <span class="feature-name">{{ f.name }}</span>
            <span class="feature-desc">{{ f.desc }}</span>
          </div>
          <span class="feature-arrow">→</span>
        </button>
      </div>
    </div>

    <!-- 底部装饰 -->
    <div class="welcome-footer" :class="{ 'visible': isReady }">
      <span class="footer-text">按 Enter 开始对话 · 使用侧边栏探索更多功能</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { getTechQuote } from '../api/agent'
import type { TechQuoteResponse } from '../types'

const emit = defineEmits<{
  (e: 'enter'): void
  (e: 'navigate', panel: string): void
  (e: 'quick-question', question: string): void
}>()

const isReady = ref(false)
const techQuote = ref<TechQuoteResponse | null>(null)
const quoteLoading = ref(false)

const questions = [
  '请介绍一下你自己',
  '你做过哪些项目？',
  '你最擅长什么技术？'
]

const techTags = [
  'Spring Boot', 'Vue3', 'RAG', 'Vector DB',
  'SSE Stream', 'Embedding', 'LLM API', 'Java 17'
]

const pipeline = [
  '文档解析',
  '文本分块',
  '向量嵌入',
  '语义检索',
  'LLM 生成'
]

const stats = [
  { icon: '◈', value: '128D', label: '向量维度' },
  { icon: '◎', value: '260', label: '分块上限' },
  { icon: '◇', value: '80ms', label: '流式延迟' },
  { icon: '▣', value: '∞', label: '对话轮次' }
]

const fileFormats = [
  'PDF', 'Word', 'Markdown', 'TXT', 'HTML'
]

const features = [
  { icon: '◎', name: '岗位匹配', desc: '分析简历与目标岗位的匹配度', panel: 'analyze' },
  { icon: '◇', name: '面试助手', desc: '模拟面试问答与准备', panel: 'interview' },
  { icon: '◈', name: '技能分析', desc: '梳理技术栈与能力图谱', panel: 'skill' },
  { icon: '⊕', name: '技术引航图', desc: 'AI导师互动教学，8大技术模块', panel: 'techguide' },
  { icon: '▣', name: '项目查询', desc: '深度检索项目经历细节', panel: 'project' }
]

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    emit('enter')
  }
}

async function loadTechQuote() {
  quoteLoading.value = true
  try {
    techQuote.value = await getTechQuote()
  } catch (e) {
    // 失败时使用默认话语
    techQuote.value = {
      quote: '程序必须是写给人阅读的，顺便让机器执行。',
      author: 'Harold Abelson',
      category: '名人名言'
    }
  } finally {
    quoteLoading.value = false
  }
}

onMounted(() => {
  setTimeout(() => {
    isReady.value = true
  }, 50)
  window.addEventListener('keydown', handleKeydown)
  loadTechQuote()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})

function handleQuestion(q: string) {
  emit('quick-question', q)
}

function handleFeature(panel: string) {
  emit('navigate', panel)
}
</script>

<style scoped>
.welcome-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 24px;
  position: relative;
  overflow: hidden;
}

/* 背景装饰 */
.welcome-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--border-light) 1px, transparent 1px),
    linear-gradient(90deg, var(--border-light) 1px, transparent 1px);
  background-size: 60px 60px;
  opacity: 0.5;
  mask-image: radial-gradient(ellipse 60% 50% at 50% 50%, black 20%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse 60% 50% at 50% 50%, black 20%, transparent 70%);
}

.bg-glow {
  position: absolute;
  top: -20%;
  left: 50%;
  transform: translateX(-50%);
  width: 800px;
  height: 500px;
  background: radial-gradient(ellipse, rgba(0, 0, 0, 0.03) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-content {
  max-width: 560px;
  width: 100%;
  text-align: center;
  opacity: 0;
  transform: translateY(16px);
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
  position: relative;
  z-index: 1;
}

.welcome-content.visible {
  opacity: 1;
  transform: translateY(0);
}

/* Hero 区域 */
.hero-section {
  margin-bottom: 32px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 20px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
  margin-bottom: 20px;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.3); }
  50% { opacity: 0.8; box-shadow: 0 0 0 4px rgba(34, 197, 94, 0); }
}

/* 标题 */
.welcome-title {
  margin: 0 0 12px;
  font-size: 56px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: var(--text);
  letter-spacing: -0.04em;
  line-height: 1.1;
}

.title-main {
  color: var(--text);
}

.title-accent {
  color: var(--text-muted);
}

.welcome-desc {
  margin: 0;
  font-size: 15px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.desc-highlight {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-muted);
  letter-spacing: 0.02em;
}

/* 技术话语 */
.tech-quote {
  margin-bottom: 36px;
  padding: 20px 24px;
  background: var(--bg-secondary);
  border-radius: 12px;
  border: 1px solid var(--border);
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
  display: flex;
  gap: 16px;
  text-align: left;
}

.quote-icon {
  font-size: 28px;
  color: var(--text-muted);
  line-height: 1;
  flex-shrink: 0;
  opacity: 0.4;
}

.quote-body {
  flex: 1;
  min-width: 0;
}

.quote-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
  font-style: italic;
  margin: 0;
}

.quote-meta {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.quote-author {
  font-size: 12px;
  color: var(--text-secondary);
}

.quote-divider {
  color: var(--text-muted);
  font-size: 10px;
}

.quote-category {
  font-size: 11px;
  color: var(--text-muted);
}

.tech-quote-loading {
  margin-bottom: 36px;
  padding: 20px 24px;
  text-align: center;
}

.loading-dots {
  font-size: 13px;
  color: var(--text-muted);
  animation: loadingPulse 1.5s ease-in-out infinite;
}

@keyframes loadingPulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}

/* 快速提问 */
.welcome-actions {
  margin-bottom: 36px;
}

.action-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-family: var(--font-mono);
}

.action-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.action-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: var(--font-sans);
}

.chip-index {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.5;
}

.action-chip:hover {
  border-color: var(--text);
  color: var(--text);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.action-chip:hover .chip-index {
  opacity: 1;
}

/* 功能入口 */
.welcome-features {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  max-width: 480px;
  margin: 0 auto;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
  font-family: inherit;
}

.feature-card:hover {
  border-color: var(--text-muted);
  background: var(--bg-secondary);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.feature-card:hover .feature-arrow {
  opacity: 1;
  transform: translateX(0);
}

.feature-icon {
  font-size: 20px;
  flex-shrink: 0;
  opacity: 0.6;
}

.feature-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.feature-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  font-family: var(--font-mono);
}

.feature-desc {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.4;
}

.feature-arrow {
  font-size: 14px;
  color: var(--text-muted);
  opacity: 0;
  transform: translateX(-4px);
  transition: all 0.2s ease;
  flex-shrink: 0;
}

/* 底部 */
.welcome-footer {
  position: absolute;
  bottom: 24px;
  opacity: 0;
  transition: opacity 0.5s ease 0.4s;
  z-index: 1;
}

.welcome-footer.visible {
  opacity: 1;
}

.footer-text {
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

/* 侧边装饰面板 */
.side-panel {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 180px;
  opacity: 0;
  transition: opacity 0.8s ease 0.3s, transform 0.8s ease 0.3s;
  z-index: 1;
}

.side-panel.visible {
  opacity: 1;
}

.side-left {
  left: 40px;
  transform: translateY(-50%) translateX(-10px);
}

.side-left.visible {
  transform: translateY(-50%) translateX(0);
}

.side-right {
  right: 40px;
  transform: translateY(-50%) translateX(10px);
}

.side-right.visible {
  transform: translateY(-50%) translateX(0);
}

.side-section {
  margin-bottom: 24px;
}

.side-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.12em;
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border-light);
}

.side-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.side-tag {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-muted);
  padding: 2px 7px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  background: var(--bg);
  transition: all 0.15s ease;
}

.side-tag:hover {
  color: var(--text-secondary);
  border-color: var(--border);
}

.side-flow {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow-num {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-muted);
  opacity: 0.4;
  flex-shrink: 0;
  width: 16px;
}

.flow-text {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: var(--font-sans);
}

.side-stats {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-icon {
  font-size: 14px;
  opacity: 0.4;
  flex-shrink: 0;
  width: 18px;
  text-align: center;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.stat-value {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  letter-spacing: -0.02em;
}

.stat-label {
  font-size: 10px;
  color: var(--text-muted);
}

@media (max-width: 1100px) {
  .side-panel {
    display: none;
  }
}

@media (max-width: 640px) {
  .welcome-title {
    font-size: 36px;
  }

  .action-chips {
    flex-direction: column;
    align-items: center;
  }

  .welcome-features {
    grid-template-columns: 1fr;
  }

  .bg-grid {
    display: none;
  }
}
</style>
