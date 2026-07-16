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
        <div class="sidebar-status">
          <span class="status-dot" :class="{ online: isOnline }"></span>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import WelcomePanel from './components/WelcomePanel.vue'
import ChatPanel from './components/ChatPanel.vue'
import ProjectQueryPanel from './components/ProjectQueryPanel.vue'
import SkillAnalysisPanel from './components/SkillAnalysisPanel.vue'
import JobMatchPanel from './components/JobMatchPanel.vue'
import InterviewPanel from './components/InterviewPanel.vue'
import AuditPanel from './components/AuditPanel.vue'
import ProjectDetailPanel from './components/ProjectDetailPanel.vue'

const activePanel = ref('chat')
const showWelcome = ref(true)
const pendingQuestion = ref('')
const chatPanelRef = ref<InstanceType<typeof ChatPanel> | null>(null)
const isOnline = ref(true)

const navItems = [
  { key: 'chat',      icon: '▸', label: '聊天问答' },
  { key: 'analyze',   icon: '◎', label: '岗位匹配' },
  { key: 'interview', icon: '◇', label: '面试助手' },
  { key: 'skill',     icon: '◈', label: '技能分析' },
  { key: 'project',   icon: '▣', label: '项目查询' },
  { key: 'projects',  icon: '⊞', label: '项目详情' },
  // 安全审计已隐藏，仅管理员可用。如需恢复，取消下行注释：
  // { key: 'audit',     icon: '▤', label: '安全审计' },
]

// 每次加载都显示欢迎页面
onMounted(() => {
  showWelcome.value = true
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
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  transition: background 0.3s;
}

.status-dot.online {
  background: #22c55e;
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.4);
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
</style>
