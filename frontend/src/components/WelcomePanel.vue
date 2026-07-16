<template>
  <div class="welcome-container">
    <!-- 主欢迎区域 -->
    <div class="welcome-content" :class="{ 'visible': isReady }">
      <!-- 顶部问候 -->
      <div class="welcome-greeting">
        <span class="greeting-time">{{ greeting }}</span>
      </div>

      <!-- 主标题 -->
      <h1 class="welcome-title">
        <span class="title-main">ResumeAgent</span>
        <span class="title-dot">.</span>
      </h1>

      <p class="welcome-desc">
        智能简历助手 · 基于 RAG 的个人知识库问答
      </p>

      <!-- 技术话语 -->
      <div class="tech-quote" v-if="techQuote">
        <div class="quote-content">
          <span class="quote-mark">"</span>
          <span class="quote-text">{{ techQuote.quote }}</span>
          <span class="quote-mark">"</span>
        </div>
        <div class="quote-meta">
          <span class="quote-author">— {{ techQuote.author }}</span>
          <span class="quote-category">{{ techQuote.category }}</span>
        </div>
      </div>
      <div class="tech-quote-loading" v-else-if="quoteLoading">
        <span class="loading-dots">加载中</span>
      </div>

      <!-- 快速提问 -->
      <div class="welcome-actions">
        <div class="action-label">试试问我</div>
        <div class="action-chips">
          <button
            v-for="q in questions"
            :key="q"
            class="action-chip"
            @click="handleQuestion(q)"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <!-- 功能入口 -->
      <div class="welcome-features">
        <button
          v-for="f in features"
          :key="f.panel"
          class="feature-btn"
          @click="handleFeature(f.panel)"
        >
          <span class="feature-icon">{{ f.icon }}</span>
          <span class="feature-name">{{ f.name }}</span>
        </button>
      </div>
    </div>

    <!-- 底部装饰 -->
    <div class="welcome-footer" :class="{ 'visible': isReady }">
      <span class="footer-text">按 Enter 开始对话</span>
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

const greeting = '您好'

const questions = [
  '介绍一下你的技术栈',
  '你有哪些项目经历',
  '你的优势是什么'
]

const features = [
  { icon: '◎', name: '岗位匹配', panel: 'analyze' },
  { icon: '◇', name: '面试助手', panel: 'interview' },
  { icon: '◈', name: '技能分析', panel: 'skill' },
  { icon: '▣', name: '项目查询', panel: 'project' }
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
}

.welcome-content {
  max-width: 520px;
  width: 100%;
  text-align: center;
  opacity: 0;
  transform: translateY(12px);
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}

.welcome-content.visible {
  opacity: 1;
  transform: translateY(0);
}

/* 问候 */
.welcome-greeting {
  margin-bottom: 20px;
}

.greeting-time {
  font-size: 13px;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

/* 标题 */
.welcome-title {
  margin: 0 0 8px;
  font-size: 42px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: var(--text);
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.title-dot {
  color: var(--text-muted);
}

.welcome-desc {
  margin: 0 0 24px;
  font-size: 14px;
  color: var(--text-secondary);
}

/* 技术话语 */
.tech-quote {
  margin-bottom: 32px;
  padding: 20px 24px;
  background: var(--bg-secondary);
  border-radius: 12px;
  border: 1px solid var(--border);
  max-width: 480px;
  margin-left: auto;
  margin-right: auto;
}

.quote-content {
  font-size: 15px;
  line-height: 1.6;
  color: var(--text);
  font-style: italic;
}

.quote-mark {
  color: var(--text-muted);
  font-size: 20px;
  font-family: var(--font-mono);
  vertical-align: middle;
}

.quote-text {
  margin: 0 4px;
}

.quote-meta {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.quote-author {
  font-size: 13px;
  color: var(--text-secondary);
}

.quote-category {
  font-size: 11px;
  color: var(--text-muted);
  padding: 2px 8px;
  background: var(--bg);
  border-radius: 10px;
  border: 1px solid var(--border);
}

.tech-quote-loading {
  margin-bottom: 32px;
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
  margin-bottom: 32px;
}

.action-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.action-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.action-chip {
  padding: 8px 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 20px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-chip:hover {
  border-color: var(--text);
  color: var(--text);
  transform: translateY(-1px);
}

/* 功能入口 */
.welcome-features {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.feature-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.feature-btn:hover {
  border-color: var(--text-secondary);
  color: var(--text);
  background: var(--bg-secondary);
}

.feature-icon {
  font-size: 14px;
}

.feature-name {
  font-size: 12px;
}

/* 底部 */
.welcome-footer {
  position: absolute;
  bottom: 24px;
  opacity: 0;
  transition: opacity 0.5s ease 0.4s;
}

.welcome-footer.visible {
  opacity: 1;
}

.footer-text {
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

@media (max-width: 640px) {
  .welcome-title {
    font-size: 32px;
  }

  .action-chips {
    flex-direction: column;
    align-items: center;
  }

  .welcome-features {
    flex-direction: column;
    align-items: center;
  }
}
</style>
