<template>
  <div class="app-shell">
    <!-- 页面背景 -->
    <div class="app-bg">
      <div class="bg-gradient"></div>
      <div class="bg-orbs">
        <div class="orb orb-1"></div>
        <div class="orb orb-2"></div>
        <div class="orb orb-3"></div>
      </div>
    </div>

    <!-- 主导航 -->
    <aside class="app-sidebar">
      <div class="sidebar-top">
        <button
          class="sidebar-logo"
          :class="{ active: showWelcome }"
          @click="goWelcome"
          title="返回首页"
        >
          <span class="logo-icon">R</span>
          <span class="logo-copy">
            <strong>ResumeAgent</strong>
            <small>求职智能工作台</small>
          </span>
        </button>
      </div>

      <nav class="sidebar-nav">
        <div v-for="group in navGroups" :key="group.title" class="nav-group">
          <div class="nav-group-title">{{ group.title }}</div>
          <button
            v-for="item in group.items"
            :key="item.key"
            class="sidebar-icon"
            :class="{ active: activePanel === item.key && !showWelcome }"
            @click="switchPanel(item.key)"
            :title="item.label"
          >
            <span class="nav-icon">{{ item.icon }}</span>
            <span class="nav-label">{{ item.label }}</span>
          </button>
        </div>
      </nav>

      <div class="sidebar-bottom">
        <button class="sidebar-icon" @click="showGuide = true" title="使用指南">
          <span class="nav-icon">?</span><span class="nav-label">使用指南</span>
        </button>
        <div class="sidebar-status" @mouseenter="onDotHover" @mouseleave="onDotLeave">
          <span class="status-dot" :class="{ online: isOnline }"></span>
          <span class="status-label">服务正常</span>
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
          :job-context="jobContext"
          @question-sent="pendingQuestion = ''"
          @navigate="switchPanel"
        />
        <ProfilePanel v-else-if="activePanel === 'profile'" />
        <JobWorkspacePanel v-else-if="activePanel === 'jobs'" @start-interview="handleStartJobInterview" @open-review="handleOpenReview" />
        <KnowledgePanel v-else-if="activePanel === 'knowledge'" />
        <ReviewPanel v-else-if="activePanel === 'review'" />
        <ProjectQueryPanel v-else-if="activePanel === 'project'" />
        <SkillAnalysisPanel v-else-if="activePanel === 'skill'" />
        <JobMatchPanel v-else-if="activePanel === 'analyze'" />
        <InterviewPanel v-else-if="activePanel === 'interview'" />
        <AuditPanel v-else-if="activePanel === 'audit'" />
        <ProjectDetailPanel v-else-if="activePanel === 'projects'" />
        <TechGuidePanel v-else-if="activePanel === 'techguide'" />
      </transition>
    </main>

    <!-- 使用指南弹窗 -->
    <GuideModal :visible="showGuide" @close="closeGuide" />
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent, onMounted, reactive, ref, watch, type Component } from 'vue'
import WelcomePanel from './components/WelcomePanel.vue'
import ChatPanel from './components/ChatPanel.vue'
import ProjectQueryPanel from './components/ProjectQueryPanel.vue'
import SkillAnalysisPanel from './components/SkillAnalysisPanel.vue'
import InterviewPanel from './components/InterviewPanel.vue'
import AuditPanel from './components/AuditPanel.vue'
import GuideModal from './components/GuideModal.vue'
import ThinkingIndicator from './components/ThinkingIndicator.vue'

const createAsyncPanel = (loader: () => Promise<{ default: Component }>) => defineAsyncComponent({
  loader,
  loadingComponent: ThinkingIndicator,
  delay: 100,
  timeout: 30000,
  onError(error, retry, fail, attempts) {
    if (attempts <= 2) retry()
    else fail(error)
  },
})

const ProfilePanel = createAsyncPanel(() => import('./components/ProfilePanel.vue'))
const KnowledgePanel = createAsyncPanel(() => import('./components/KnowledgePanel.vue'))
const ReviewPanel = createAsyncPanel(() => import('./components/ReviewPanel.vue'))
const JobMatchPanel = createAsyncPanel(() => import('./components/JobMatchPanel.vue'))
const ProjectDetailPanel = createAsyncPanel(() => import('./components/ProjectDetailPanel.vue'))
const TechGuidePanel = createAsyncPanel(() => import('./components/TechGuidePanel.vue'))
const JobWorkspacePanel = createAsyncPanel(() => import('./components/JobWorkspacePanel.vue'))

const activePanel = ref('chat')
const showWelcome = ref(true)
const pendingQuestion = ref('')
const chatPanelRef = ref<InstanceType<typeof ChatPanel> | null>(null)
const isOnline = ref(true)
const showLlmInfo = ref(false)
const showGuide = ref(false)
const jobContext = ref<{ jobId: string; jobTitle: string } | null>(null)
let llmInfoLoaded = false

const llmConfig = reactive({
  baseUrl: '',
  model: '',
  apiKey: '',
  temperature: 0,
  maxTokens: 0,
  llmAvailable: false
})

const navGroups = [
  {
    title: '核心功能',
    items: [
      { key: 'chat', icon: '◉', label: '模拟面试' },
      { key: 'jobs', icon: '▣', label: '岗位工作区' },
      { key: 'analyze', icon: '↗', label: '岗位匹配' },
      { key: 'interview', icon: '✦', label: '面试助手' },
    ]
  },
  {
    title: '资料管理',
    items: [
      { key: 'profile', icon: '▤', label: '我的档案' },
      { key: 'knowledge', icon: '▥', label: '知识库' },
      { key: 'projects', icon: '□', label: '项目详情' },
      { key: 'project', icon: '⌕', label: '项目查询' },
    ]
  },
  {
    title: '能力提升',
    items: [
      { key: 'review', icon: '⌁', label: '面试复盘' },
      { key: 'skill', icon: '▥', label: '技能分析' },
      { key: 'techguide', icon: '⌖', label: '技术引航图' },
    ]
  }
]

onMounted(() => {
  showWelcome.value = true
  const hasVisited = sessionStorage.getItem('resumeagent_guide_done')
  if (!hasVisited) {
    showGuide.value = true
  }
})

watch(activePanel, () => {
  if (showWelcome.value) {
    showWelcome.value = false
  }
})

function goWelcome() {
  showWelcome.value = true
}

function switchPanel(panel: string) {
  if (panel === 'chat') {
    jobContext.value = null
  }
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

function handleStartJobInterview(context: { jobId: string; jobTitle: string }) {
  jobContext.value = context
  activePanel.value = 'chat'
  showWelcome.value = false
}

function handleOpenReview(id: string) {
  sessionStorage.setItem('resumeagent_open_review', id)
  activePanel.value = 'review'
  showWelcome.value = false
}

function maskUrl(url: string): string {
  if (!url || url === '未配置') return url
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
  sessionStorage.setItem('resumeagent_guide_done', 'true')
}
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  position: relative;
}

/* ─── Background（白色极简底色） ─── */
.app-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.bg-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #f7f8fa 0%, #eef1f6 100%);
}

.bg-orbs {
  display: none;
}

/* ─── Sidebar ─── */
.app-sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px) saturate(1.2);
  -webkit-backdrop-filter: blur(20px) saturate(1.2);
  border-right: 1px solid rgba(255, 255, 255, 0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0;
  z-index: 10;
  position: relative;
  box-shadow: 1px 0 12px rgba(0, 0, 0, 0.03);
}

.sidebar-top {
  margin-bottom: 10px;
}

.sidebar-logo {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 18px;
  color: var(--accent);
  border: none;
  background: var(--accent-soft);
  transition: all var(--transition-normal);
}

.sidebar-logo:hover {
  background: var(--accent);
  color: #fff;
  transform: scale(1.05);
  box-shadow: 0 4px 12px var(--accent-glow);
}

.sidebar-logo.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-light));
  color: #fff;
  box-shadow: 0 4px 12px var(--accent-glow);
}

.logo-icon {
  font-size: 16px;
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.nav-icon {
  font-size: 17px;
  line-height: 1;
}

.sidebar-bottom {
  margin-top: auto;
  padding-top: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sidebar-status {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: default;
  padding: 10px;
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
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
  background: var(--success);
  box-shadow: 0 0 8px var(--success-glow);
  animation: dotPulse 2s ease-in-out infinite;
}

.app-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: transparent;
  position: relative;
  z-index: 1;
}

/* ─── Transitions ─── */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.fade-enter-from {
  opacity: 0;
  transform: translateX(12px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}

/* ─── LLM Popup ─── */
.llm-popup {
  position: absolute;
  left: calc(100% + 14px);
  bottom: -8px;
  width: 290px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px) saturate(1.2);
  -webkit-backdrop-filter: blur(20px) saturate(1.2);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--radius-lg);
  padding: 0;
  box-shadow: var(--shadow-float);
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
  background: var(--success);
  box-shadow: 0 0 6px var(--success-glow);
}

.llm-popup-header-status .status-indicator.llm-off {
  background: var(--error);
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

.popup-enter-active,
.popup-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.popup-enter-from,
.popup-leave-to {
  opacity: 0;
  transform: translateX(-4px);
}

@media (max-width: 640px) {
  .app-sidebar {
    width: 48px;
    min-width: 48px;
  }

  .sidebar-icon {
    width: 36px;
    height: 36px;
  }
}

/* ─── Enterprise workspace overrides ─── */
.app-shell { background: var(--bg); }
.app-bg, .bg-gradient { background: var(--bg); }
.app-sidebar {
  width: 220px;
  min-width: 220px;
  align-items: stretch;
  padding: 20px 14px 16px;
  background: #fff;
  border-right: 1px solid var(--border);
  box-shadow: 1px 0 8px rgba(31, 35, 41, .03);
}
.sidebar-top { margin: 0 8px 28px; }
.sidebar-logo {
  width: 100%;
  height: auto;
  min-height: 42px;
  justify-content: flex-start;
  gap: 11px;
  padding: 7px 10px;
  background: transparent;
  color: var(--text);
  box-shadow: none;
  transform: none;
}
.sidebar-logo:hover, .sidebar-logo.active { background: var(--accent-soft); color: var(--text); box-shadow: none; transform: none; }
.logo-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: var(--accent);
  color: #fff;
  font: 700 15px var(--font-sans);
}
.logo-copy { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; }
.logo-copy strong { font-size: 14px; line-height: 1.2; }
.logo-copy small { font-size: 10px; color: var(--text-muted); font-weight: 400; }
.sidebar-nav { align-items: stretch; gap: 18px; }
.nav-group { display: flex; flex-direction: column; gap: 3px; }
.nav-group-title { padding: 0 12px 7px; color: var(--text-muted); font-size: 11px; font-weight: 600; }
.sidebar-icon {
  width: 100%;
  height: 38px;
  justify-content: flex-start;
  gap: 11px;
  padding: 0 12px;
  border-radius: 7px;
  color: var(--text-secondary);
  font-size: 13px;
  transform: none;
}
.sidebar-icon:hover { background: var(--bg-hover); color: var(--text); transform: none; }
.sidebar-icon.active { background: var(--accent-soft); color: var(--accent); box-shadow: none; font-weight: 600; }
.nav-icon { width: 18px; text-align: center; font-size: 16px; }
.nav-label { display: inline; white-space: nowrap; }
.sidebar-icon .tooltip { display: none; }
.sidebar-bottom { align-items: stretch; padding: 14px 0 0; border-top: 1px solid var(--border-light); gap: 8px; }
.sidebar-status { justify-content: flex-start; gap: 10px; padding: 8px 12px; border-radius: 7px; }
.status-label { color: var(--text-muted); font-size: 11px; }
.status-dot.online { box-shadow: none; animation: none; }
.llm-popup { left: calc(100% + 10px); bottom: 0; border: 1px solid var(--border); box-shadow: var(--shadow-float); }

@media (max-width: 720px) {
  .app-sidebar { width: 64px; min-width: 64px; padding: 14px 10px; align-items: center; }
  .sidebar-top { margin: 0 0 20px; }
  .sidebar-logo { width: 42px; padding: 7px; justify-content: center; }
  .logo-copy, .nav-group-title, .nav-label, .status-label { display: none; }
  .sidebar-nav { align-items: center; gap: 8px; }
  .sidebar-icon { width: 42px; justify-content: center; padding: 0; }
  .sidebar-bottom { align-items: center; }
  .sidebar-status { justify-content: center; padding: 10px; }
}
</style>
