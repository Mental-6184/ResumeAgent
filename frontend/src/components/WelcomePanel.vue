<template>
  <div class="welcome-container">
    <!-- 页面背景 -->
    <div class="welcome-bg">
      <div class="bg-grid"></div>
      <div class="bg-glow"></div>
      <div class="bg-orb bg-orb-1"></div>
      <div class="bg-orb bg-orb-2"></div>
    </div>

    <!-- 左侧装饰 -->
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

    <!-- 右侧装饰 -->
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
      <div class="hero-section">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          <span>AI-Powered</span>
        </div>
        <h1 class="welcome-title">开始准备你的下一次机会</h1>
        <p class="welcome-desc">
          你的求职资料、岗位分析和面试训练，都集中在这里完成。
        </p>
        <button class="welcome-primary" @click="handleQuestion('请介绍一下你自己')">
          <span>开始模拟面试</span><span class="primary-arrow">→</span>
        </button>
      </div>

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

      <div class="welcome-actions">
        <div class="section-kicker">快速开始</div>
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

      <div class="welcome-flow">
        <div class="section-kicker">使用流程</div>
        <div class="flow-cards">
          <div class="flow-card"><span>01</span><strong>完善档案</strong><small>上传简历与经历</small></div>
          <div class="flow-card"><span>02</span><strong>选择目标</strong><small>匹配岗位或练习面试</small></div>
          <div class="flow-card"><span>03</span><strong>持续提升</strong><small>查看分析与复盘</small></div>
        </div>
      </div>
    </div>

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
  'PDF', 'Markdown', 'TXT'
]

const features = [
  { icon: '📌', name: '岗位工作区', desc: '管理岗位、报告、简历建议与投递进度', panel: 'jobs' },
  { icon: '🎯', name: '岗位匹配', desc: '分析简历与目标岗位的匹配度', panel: 'analyze' },
  { icon: '💡', name: '面试助手', desc: '模拟面试问答与准备', panel: 'interview' },
  { icon: '📊', name: '技能分析', desc: '梳理技术栈与能力图谱', panel: 'skill' },
  { icon: '🧭', name: '技术引航图', desc: 'AI导师互动教学，8大技术模块', panel: 'techguide' },
  { icon: '🔍', name: '项目查询', desc: '深度检索项目经历细节', panel: 'project' }
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
  } catch {
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

/* ─── Background ─── */
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
    linear-gradient(rgba(36, 86, 230, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(36, 86, 230, 0.04) 1px, transparent 1px);
  background-size: 60px 60px;
  opacity: 0.8;
  mask-image: radial-gradient(ellipse 60% 50% at 50% 50%, black 20%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse 60% 50% at 50% 50%, black 20%, transparent 70%);
}

.bg-glow {
  position: absolute;
  top: -20%;
  left: 50%;
  transform: translateX(-50%);
  width: 900px;
  height: 600px;
  background: radial-gradient(ellipse, rgba(36, 86, 230, 0.06) 0%, transparent 70%);
  border-radius: 50%;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
}

.bg-orb-1 {
  width: 300px;
  height: 300px;
  background: rgba(36, 86, 230, 0.08);
  top: 10%;
  right: 10%;
  animation: orbFloat 20s ease-in-out infinite;
}

.bg-orb-2 {
  width: 250px;
  height: 250px;
  background: rgba(91, 143, 217, 0.06);
  bottom: 15%;
  left: 15%;
  animation: orbFloat 25s ease-in-out infinite reverse;
}

@keyframes orbFloat {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -20px) scale(1.05); }
  66% { transform: translate(-20px, 30px) scale(0.95); }
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

/* ─── Hero ─── */
.hero-section {
  margin-bottom: 32px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
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
  background: var(--success);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 var(--success-glow); }
  50% { opacity: 0.8; box-shadow: 0 0 0 4px rgba(74, 222, 128, 0); }
}

.welcome-title {
  margin: 0 0 12px;
  font-size: 56px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: -0.04em;
  line-height: 1.1;
}

.title-main {
  color: var(--text);
}

.title-accent {
  background: linear-gradient(135deg, var(--accent), var(--accent-light));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
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

/* ─── Quote ─── */
.tech-quote {
  margin-bottom: 36px;
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-lg);
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
  display: flex;
  gap: 16px;
  text-align: left;
  box-shadow: var(--shadow-glass);
}

.quote-icon {
  font-size: 28px;
  color: var(--accent);
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

/* ─── Actions ─── */
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
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-lg);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-smooth);
  font-family: var(--font-sans);
  box-shadow: var(--shadow-sm);
}

.chip-index {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--accent);
  opacity: 0.5;
}

.action-chip:hover {
  border-color: var(--accent-light);
  color: var(--text);
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
  background: rgba(255, 255, 255, 0.85);
}

.action-chip:hover .chip-index {
  opacity: 1;
}

/* ─── Features ─── */
.welcome-features {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  max-width: 480px;
  margin: 0 auto;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-smooth);
  text-align: left;
  font-family: inherit;
  box-shadow: var(--shadow-sm);
}

.feature-card:hover {
  border-color: var(--accent-light);
  background: rgba(255, 255, 255, 0.85);
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.feature-card:hover .feature-arrow {
  opacity: 1;
  transform: translateX(0);
  color: var(--accent);
}

.feature-icon {
  font-size: 22px;
  flex-shrink: 0;
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

/* ─── Footer ─── */
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

/* ─── Side Panels ─── */
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
  color: var(--accent);
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
  padding: 2px 8px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.5);
  transition: all 0.15s ease;
}

.side-tag:hover {
  color: var(--accent);
  border-color: var(--accent-light);
  background: var(--accent-soft);
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
  color: var(--accent);
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
  opacity: 0.5;
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

/* ─── Simple workspace home ─── */
 .welcome-container { min-height: 100%; justify-content: flex-start; padding: 32px 32px 40px; background: var(--bg); overflow-y: auto; }
.welcome-bg, .bg-grid, .bg-glow, .bg-orb { display: none; }
.side-panel { display: none; }
.welcome-content { max-width: 1080px; text-align: left; }
.hero-section { margin-bottom: 28px; }
.hero-badge { background: var(--accent-soft); border: 0; border-radius: 5px; color: var(--accent); font-family: var(--font-sans); font-weight: 600; padding: 6px 10px; margin-bottom: 18px; }
.welcome-title { color: var(--text); font: 700 clamp(30px, 4vw, 42px) var(--font-sans); letter-spacing: -.03em; margin-bottom: 12px; }
.title-main, .title-accent { color: inherit; background: none; -webkit-text-fill-color: initial; }
.welcome-desc { max-width: 600px; color: var(--text-secondary); font-size: 15px; }
.desc-highlight { color: var(--text-muted); font-family: var(--font-sans); }
.welcome-primary { display: inline-flex; align-items: center; gap: 24px; margin-top: 24px; padding: 11px 16px 11px 18px; border: 0; border-radius: 7px; background: var(--accent); color: #fff; font: 600 13px var(--font-sans); cursor: pointer; box-shadow: 0 5px 12px var(--accent-glow); transition: background .2s, transform .2s; }
.welcome-primary:hover { background: #0958d9; transform: translateY(-1px); }
.primary-arrow { font-size: 18px; font-weight: 400; line-height: 1; }
.tech-quote { max-width: none; margin: 0 0 26px; padding: 16px 18px; background: #fff; border: 1px solid var(--border); border-radius: 8px; box-shadow: var(--shadow-sm); }
.quote-icon { font-size: 22px; }
.welcome-actions { margin-bottom: 28px; }
.section-kicker { margin-bottom: 10px; color: var(--text); font-size: 13px; font-weight: 600; }
.action-chips { justify-content: flex-start; gap: 10px; }
.action-chip { padding: 10px 14px; background: #fff; border: 1px solid var(--border); border-radius: 7px; box-shadow: none; }
.action-chip:hover { background: var(--accent-soft); border-color: #b7d4ff; transform: none; box-shadow: none; }
.welcome-features { max-width: none; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin: 0; }
.feature-card { min-height: 128px; align-items: flex-start; flex-direction: column; gap: 14px; padding: 16px; background: #fff; border: 1px solid var(--border); border-radius: 8px; box-shadow: var(--shadow-sm); }
.feature-card:hover { background: #fff; border-color: #9ec5ff; transform: translateY(-2px); box-shadow: var(--shadow-md); }
.feature-icon { width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center; border-radius: 7px; background: var(--accent-soft); font-size: 17px; }
.feature-info { gap: 5px; }
.feature-name { font: 600 13px var(--font-sans); }
.feature-desc { font-size: 11px; }
.feature-arrow { position: absolute; display: none; }
.welcome-flow { margin-top: 28px; }
.flow-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.flow-card { display: grid; grid-template-columns: 32px 1fr; column-gap: 10px; padding: 14px 16px; background: #fff; border: 1px solid var(--border); border-radius: 8px; }
.flow-card span { grid-row: span 2; color: var(--accent); font: 700 12px var(--font-mono); }
.flow-card strong { font-size: 13px; }
.flow-card small { margin-top: 4px; color: var(--text-muted); font-size: 11px; }
.welcome-footer { position: static; margin-top: 28px; }
.footer-text { font-family: var(--font-sans); }
@media (max-width: 900px) { .welcome-features { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 640px) { .welcome-container { padding: 28px 18px; } .welcome-features, .flow-cards { grid-template-columns: 1fr; } .action-chips { flex-direction: column; align-items: stretch; } .action-chip { justify-content: flex-start; } }
</style>
