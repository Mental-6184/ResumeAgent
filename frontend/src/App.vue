<template>
  <div class="app-shell">
    <!-- 侧边栏 -->
    <aside class="app-sidebar">
      <div class="sidebar-top">
        <button
          class="sidebar-logo"
          :class="{ active: showWelcome }"
          @click="goWelcome"
          title="欢迎页"
        >
          <span>✦</span>
        </button>
      </div>

      <nav class="sidebar-nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="sidebar-icon"
          :class="{ active: activePanel === item.key && !showWelcome }"
          @click="switchPanel(item.key)"
          :title="item.label"
        >
          <span>{{ item.icon }}</span>
          <span class="tooltip">{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar-bottom">
        <button class="sidebar-icon" @click="showGuide = true" title="使用指南">
          <span>?</span>
        </button>
        <div class="sidebar-status" @mouseenter="onDotHover" @mouseleave="onDotLeave">
          <span class="status-dot" :class="{ online: isOnline }"></span>
          <transition name="popup">
            <div v-if="showLlmInfo" class="llm-popup">
              <div class="llm-popup-header">
                <span class="llm-popup-header-title">LLM 配置</span>
                <span class="llm-popup-header-status" :class="llmConfig.llmAvailable ? 'llm-on' : 'llm-off'">
                  <span class="status-indicator" :class="llmConfig.llmAvailable ? 'llm-on' : 'llm-off'"></span>
                  {{ llmConfig.llmAvailable ? '已连接' : '未连接' }}
                </span>
              </div>
              <div class="llm-popup-body">
                <div class="llm-popup-row">
                  <span class="llm-label">模型</span>
                  <span class="llm-value">{{ llmConfig.model || '—' }}</span>
                </div>
                <div class="llm-popup-row">
                  <span class="llm-label">端点</span>
                  <span class="llm-value llm-mono">{{ maskUrl(llmConfig.baseUrl) }}</span>
                </div>
                <div class="llm-popup-row">
                  <span class="llm-label">密钥</span>
                  <span class="llm-value llm-mono">{{ llmConfig.apiKey || '—' }}</span>
                </div>
                <div class="llm-popup-row">
                  <span class="llm-label">温度</span>
                  <span class="llm-value">{{ llmConfig.temperature }}</span>
                </div>
                <div class="llm-popup-row">
                  <span class="llm-label">Max Token</span>
                  <span class="llm-value">{{ llmConfig.maxTokens }}</span>
                </div>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="app-content">
      <transition name="fade" mode="out-in">
        <WelcomePanel
          v-if="showWelcome"
          @enter="handleWelcomeEnter"
          @navigate="handleWelcomeNavigate"
          @quick-question="handleQuickQuestion"
        />
        <ChatPanel
          v-else-if="activePanel === 'chat'"
          ref="chatPanelRef"
          :initial-question="pendingQuestion"
          @question-sent="pendingQuestion = ''"
        />
        <ProjectQueryPanel v-else-if="activePanel === 'project'" />
        <SkillAnalysisPanel v-else-if="activePanel === 'skill'" />
        <JobMatchPanel v-else-if="activePanel === 'analyze'" />
        <InterviewPanel v-else-if="activePanel === 'interview'" />
        <AuditPanel v-else-if="activePanel === 'audit'" />
        <ProjectDetailPanel v-else-if="activePanel === 'projects'" />
      </transition>
    </main>

    <!-- 使用指南弹窗 -->
    <GuideModal :visible="showGuide" @close="closeGuide" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import WelcomePanel from './components/WelcomePanel.vue'
import ChatPanel from './components/ChatPanel.vue'
import ProjectQueryPanel from './components/ProjectQueryPanel.vue'
import SkillAnalysisPanel from './components/SkillAnalysisPanel.vue'
import JobMatchPanel from './components/JobMatchPanel.vue'
import InterviewPanel from './components/InterviewPanel.vue'
import AuditPanel from './components/AuditPanel.vue'
import ProjectDetailPanel from './components/ProjectDetailPanel.vue'
import GuideModal from './components/GuideModal.vue'

const activePanel = ref('chat')
const showWelcome = ref(true)
const pendingQuestion = ref('')
const chatPanelRef = ref<InstanceType<typeof ChatPanel> | null>(null)
const isOnline = ref(true)
const showLlmInfo = ref(false)
const showGuide = ref(false)
let llmInfoLoaded = false

const llmConfig = reactive({
  baseUrl: '',
  model: '',
  apiKey: '',
  temperature: 0,
  maxTokens: 0,
  llmAvailable: false
})

const navItems = [
  { key: 'chat',      icon: '▸', label: '模拟面试' },
  { key: 'analyze',   icon: '◎', label: '岗位匹配' },
  { key: 'interview', icon: '◇', label: '面试助手' },
  { key: 'skill',     icon: '◈', label: '技能分析' },
  { key: 'project',   icon: '▣', label: '项目查询' },
  { key: 'projects',  icon: '⊞', label: '项目详情' },
  // 安全审计已隐藏，仅管理员可用。如需恢复，取消下行注释：
  // { key: 'audit',     icon: '▤', label: '安全审计' },
]

// 每次加载都显示欢迎页面，首次访问显示引导
onMounted(() => {
  showWelcome.value = true
  const hasVisited = localStorage.getItem('resumeagent_guide_done')
  if (!hasVisited) {
    showGuide.value = true
  }
})

// 监听面板切换，隐藏欢迎页
watch(activePanel, () => {
  if (showWelcome.value) {
    showWelcome.value = false
  }
})

function goWelcome() {
  showWelcome.value = true
}

function switchPanel(panel: string) {
  activePanel.value = panel
  showWelcome.value = false
}

function handleWelcomeEnter() {
  showWelcome.value = false
  activePanel.value = 'chat'
  localStorage.setItem('resumeagent_visited', 'true')
}

function handleWelcomeNavigate(panel: string) {
  activePanel.value = panel
  showWelcome.value = false
  localStorage.setItem('resumeagent_visited', 'true')
}

function handleQuickQuestion(question: string) {
  pendingQuestion.value = question
  activePanel.value = 'chat'
  showWelcome.value = false
  localStorage.setItem('resumeagent_visited', 'true')
}

function maskUrl(url: string): string {
  if (!url || url === '未配置') return url
  // 只显示协议和域名，隐藏路径
  try {
    const u = new URL(url)
    return u.protocol + '//' + u.host + (u.pathname.length > 1 ? '/...' : '')
  } catch {
    return url.slice(0, 30) + '...'
  }
}

async function onDotHover() {
  showLlmInfo.value = true
  if (!llmInfoLoaded) {
    try {
      const res = await fetch('/api/system/llm-config')
      if (res.ok) {
        const data = await res.json()
        Object.assign(llmConfig, data)
        llmInfoLoaded = true
      }
    } catch { /* ignore */ }
  }
}

function onDotLeave() {
  showLlmInfo.value = false
}

function closeGuide() {
  showGuide.value = false
  localStorage.setItem('resumeagent_guide_done', 'true')
}
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.app-sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  background: var(--bg);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0;
  z-index: 10;
}

.sidebar-top {
  margin-bottom: 8px;
}

.sidebar-logo {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  cursor: pointer;
  font-size: 18px;
  color: var(--text);
  border: none;
  background: transparent;
  transition: all 0.15s ease;
}

.sidebar-logo:hover {
  background: var(--bg-hover);
}

.sidebar-logo.active {
  background: var(--text);
  color: #fff;
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sidebar-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  cursor: pointer;
  font-size: 18px;
  color: var(--text-secondary);
  transition: all 0.15s ease;
  border: none;
  background: transparent;
  position: relative;
}

.sidebar-icon:hover {
  background: var(--bg-hover);
  color: var(--text);
}

.sidebar-icon.active {
  background: var(--text);
  color: #fff;
}

.sidebar-icon .tooltip {
  position: absolute;
  left: calc(100% + 8px);
  top: 50%;
  transform: translateY(-50%);
  background: var(--text);
  color: #fff;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s;
  z-index: 100;
}

.sidebar-icon:hover .tooltip {
  opacity: 1;
}

.sidebar-bottom {
  margin-top: auto;
  padding-top: 8px;
}

.sidebar-status {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: default;
  padding: 8px;
  border-radius: 8px;
  transition: background 0.15s ease;
}

.sidebar-status:hover {
  background: var(--bg-hover);
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--text-muted);
  transition: all 0.3s;
  position: relative;
}

.status-dot.online {
  background: #22c55e;
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
  animation: dotPulse 2s ease-in-out infinite;
}

@keyframes dotPulse {
  0%, 100% { box-shadow: 0 0 6px rgba(34, 197, 94, 0.4); }
  50% { box-shadow: 0 0 12px rgba(34, 197, 94, 0.7), 0 0 20px rgba(34, 197, 94, 0.2); }
}

.app-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateX(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}

/* LLM 弹出卡片 */

.llm-popup {
  position: absolute;
  left: calc(100% + 12px);
  bottom: -8px;
  width: 280px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08), 0 1px 4px rgba(0, 0, 0, 0.04);
  z-index: 200;
  pointer-events: none;
  overflow: hidden;
}

.llm-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--border-light);
}

.llm-popup-header-title {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.llm-popup-header-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-family: var(--font-mono);
}

.llm-popup-header-status .status-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.llm-popup-header-status .status-indicator.llm-on {
  background: #22c55e;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.4);
}

.llm-popup-header-status .status-indicator.llm-off {
  background: #ef4444;
}

.llm-popup-header-status.llm-on {
  color: #16a34a;
}

.llm-popup-header-status.llm-off {
  color: #dc2626;
}

.llm-popup-body {
  padding: 12px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.llm-popup-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.llm-label {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
  width: 60px;
  font-family: var(--font-mono);
  letter-spacing: 0.02em;
}

.llm-value {
  font-size: 12px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.llm-mono {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-secondary);
  letter-spacing: -0.01em;
}

/* 弹出动画 */
.popup-enter-active,
.popup-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.popup-enter-from,
.popup-leave-to {
  opacity: 0;
  transform: translateX(-4px);
}
</style>
