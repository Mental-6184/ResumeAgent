<template>
  <div class="chat-layout">
    <!-- 会话侧边栏 -->
    <transition name="slide-right">
      <div v-if="showSessionSidebar" class="session-sidebar">
        <div class="session-sidebar-header">
          <span class="session-sidebar-title">历史会话</span>
          <button class="session-new-btn" @click="handleNewSession" title="新建对话">
            <span>+</span>
          </button>
        </div>

        <div class="session-list">
          <div v-if="sessionList.length === 0" class="session-empty">
            <span class="session-empty-icon">💬</span>
            <span>暂无历史会话</span>
          </div>
          <div
            v-for="session in sessionList"
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === activeSessionId }"
            @click="handleSwitchSession(session.id)"
          >
            <div class="session-item-content">
              <div class="session-item-title">{{ session.title }}</div>
              <div class="session-item-meta">
                <span class="session-mode-badge" :class="session.mode || 'rag'">
                  {{ session.mode === 'llm' ? '通用' : '面试' }}
                </span>
                <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
              </div>
            </div>
            <button
              class="session-delete-btn"
              @click.stop="handleDeleteSession(session.id)"
              title="删除会话"
            >
              ×
            </button>
          </div>
        </div>

        <div class="session-sidebar-footer" v-if="sessionList.length > 0">
          <button class="session-clear-btn" @click="handleClearAll">
            清空所有会话
          </button>
        </div>
      </div>
    </transition>

    <!-- 主聊天区 -->
    <div class="chat-container">
      <!-- 顶部工具栏 -->
      <div class="chat-toolbar">
        <button
          class="toolbar-btn"
          :class="{ active: showSessionSidebar }"
          @click="toggleSessionSidebar"
          title="历史会话"
        >
          <span class="toolbar-icon">📋</span>
          <span class="toolbar-label">会话</span>
          <span v-if="sessionList.length > 0" class="toolbar-badge">{{ sessionList.length }}</span>
        </button>
        <div class="toolbar-spacer"></div>
        <button
          class="toolbar-btn"
          :disabled="reviewLoading || messages.length === 0"
          title="对当前会话生成面试复盘报告"
          @click="generateReview"
        >
          <span class="toolbar-icon">📈</span>
          <span class="toolbar-label">{{ reviewLoading ? '生成中…' : '生成复盘' }}</span>
        </button>
        <div v-if="messages.length > 0" class="mode-badge" :class="chatMode">
          {{ chatMode === 'rag' ? '◈ 模拟面试' : '◆ 通用对话' }}
        </div>
      </div>

      <!-- 消息区域 -->
      <div class="chat-messages" ref="messagesRef">
        <!-- 欢迎区域 - 无消息时显示 -->
        <div v-if="messages.length === 0" class="chat-hero" :class="{ 'visible': heroReady }">
          <div class="hero-glow"></div>
          <div class="hero-icon">✦</div>
          <h1 class="hero-title">ResumeAgent</h1>
          <p class="hero-subtitle">模拟面试 · 我就是你，替你回答每一个问题</p>

          <!-- 模式切换 -->
          <div class="mode-switcher">
            <button
              class="mode-btn"
              :class="{ active: chatMode === 'rag' }"
              @click="switchMode('rag')"
            >
              <span class="mode-icon">◈</span>
              <span class="mode-label">模拟面试</span>
              <span class="mode-desc">替你回答面试问题</span>
            </button>
            <button
              class="mode-btn"
              :class="{ active: chatMode === 'llm' }"
              @click="switchMode('llm')"
            >
              <span class="mode-icon">◆</span>
              <span class="mode-label">通用对话</span>
              <span class="mode-desc">纯大模型问答</span>
            </button>
          </div>

          <!-- 快速提问 -->
          <div class="hero-suggestions">
            <button
              v-for="q in suggestions"
              :key="q"
              class="suggestion-btn"
              @click="handleSuggestion(q)"
            >
              {{ q }}
            </button>
          </div>
        </div>

        <!-- 消息列表 - 气泡样式 -->
        <div v-for="msg in messages" :key="msg.id" class="bubble-row" :class="msg.role">
          <!-- AI 头像 -->
          <div v-if="msg.role === 'assistant'" class="bubble-avatar ai-avatar">
            <span>✦</span>
          </div>

          <div class="bubble-wrapper" :class="msg.role">
            <div class="bubble" :class="msg.role">
              <div class="bubble-content" v-html="renderSimple(msg.content)"></div>
              <!-- 光标 + 思考提示 -->
              <span v-if="msg.role === 'assistant' && streaming && msg.id === streamingId" class="cursor-wrapper">
                <span class="chat-cursor"></span>
                <ThinkingIndicator v-if="waitingForResponse" variant="inline" />
              </span>

              <!-- 引用 (仅 RAG 模式显示) -->
              <div v-if="chatMode === 'rag' && msg.citations && msg.citations.length > 0" class="chat-citations">
                <div class="cite-label">── references ──</div>
                <div v-for="(cite, i) in msg.citations" :key="i" class="chat-citation-item">
                  [{{ i + 1 }}] {{ cite }}
                </div>
              </div>

              <!-- 错误重试 -->
              <div v-if="msg.error" class="bubble-error">
                <span class="error-text">{{ msg.error }}</span>
                <button class="retry-btn" @click="retryMessage(msg)">重试</button>
              </div>
            </div>
            <div class="bubble-time">{{ formatBubbleTime(msg.timestamp) }}</div>
          </div>

          <!-- 用户头像 -->
          <div v-if="msg.role === 'user'" class="bubble-avatar user-avatar">
            <span>👤</span>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div class="chat-input-row">
          <textarea
            ref="inputRef"
            class="chat-input"
            v-model="question"
            :placeholder="chatMode === 'rag' ? '作为面试官，向我提问...' : '输入任何问题...'"
            @keydown="handleKeydown"
            rows="1"
            @input="autoResize"
            :disabled="loading || streaming"
          ></textarea>
          <button
            class="chat-send-btn"
            :disabled="loading || streaming || !question.trim()"
            @click="sendMessage"
          >
            <span v-if="streaming" class="btn-loading">
              <span class="loading-spinner"></span>
            </span>
            <span v-else class="send-icon">↑</span>
          </button>
        </div>
        <div class="chat-hint">
          <span class="hint-item">
            <span class="hint-key">Enter</span> 发送
          </span>
          <span class="hint-divider">·</span>
          <span class="hint-item">
            <span class="hint-key">Shift+Enter</span> 换行
          </span>
          <span class="hint-divider">·</span>
          <span class="hint-item mode-hint" @click="toggleMode">
            <span class="hint-key">{{ chatMode === 'rag' ? '通用对话' : '模拟面试' }}</span>
            切换模式
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ThinkingIndicator from './ThinkingIndicator.vue'
import { useSessionManager } from '../composables/useSessionManager'
import { useMarkdown } from '../composables/useMarkdown'
import { consumeSse } from '../composables/useSseFetch'
import { createReview } from '../api/agent'
import { linkJobReview } from '../api/jobs'

interface RenderMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: string[]
  timestamp: number
  error?: string
}

type ChatMode = 'rag' | 'llm'

const props = defineProps<{
  initialQuestion?: string
  jobContext?: { jobId: string; jobTitle: string } | null
}>()

const emit = defineEmits<{
  (e: 'question-sent'): void
  (e: 'navigate', panel: string): void
}>()

const { renderSimple } = useMarkdown()

const {
  activeSessionId,
  activeSession,
  sessionList,
  showSessionSidebar,
  storage,
  switchSession: switchToSession,
  createNewSession,
  deleteSession,
  clearAllSessions,
  initSession,
  toggleSessionSidebar
} = useSessionManager('chat')

const ragSuggestions = [
  '请介绍一下你自己',
  '你做过哪些项目？',
  '你最擅长的技术是什么？',
  '你在项目中遇到过什么难点？'
]

const llmSuggestions = [
  '帮我写一段快速排序代码',
  '解释一下微服务架构的优缺点',
  '用Python实现一个简单的爬虫',
  '什么是RAG技术？'
]

const chatMode = ref<ChatMode>('rag')
const conversationId = ref('')
const question = ref('')
const loading = ref(false)
const streaming = ref(false)
const streamingId = ref('')
const waitingForResponse = ref(false)
const messages = ref<RenderMessage[]>([])
const messagesRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const heroReady = ref(false)
let streamController: AbortController | null = null

// Context window limit
const MAX_CONTEXT_MESSAGES = 20

const suggestions = computed(() => chatMode.value === 'rag' ? ragSuggestions : llmSuggestions)

// 初始化会话
onMounted(() => {
  inputRef.value?.focus()
  setTimeout(() => {
    heroReady.value = true
  }, 50)

  // 岗位工作区进入时创建岗位专属会话，否则恢复普通会话
  if (props.jobContext) {
    const session = createNewSession('rag', props.jobContext.jobId, props.jobContext.jobTitle)
    conversationId.value = session.id
    loadMessagesFromSession(session)
    return
  }

  // 从 localStorage 恢复会话
  initSession('rag')
  const session = activeSession.value
  if (session) {
    chatMode.value = session.mode || 'rag'
    conversationId.value = session.id
    loadMessagesFromSession(session)
  } else {
    conversationId.value = `conv-${Date.now()}`
  }
})

// 监听会话切换
watch(activeSessionId, (newId) => {
  if (!newId) return
  const session = storage.getSession(newId)
  if (session) {
    chatMode.value = session.mode || 'rag'
    conversationId.value = session.id
    loadMessagesFromSession(session)
    closeStream()
    streaming.value = false
    waitingForResponse.value = false
  }
})

// 监听初始问题
watch(() => props.initialQuestion, (newQ) => {
  if (newQ && newQ.trim()) {
    question.value = newQ
    emit('question-sent')
    nextTick(() => {
      sendMessage()
    })
  }
}, { immediate: true })

function loadMessagesFromSession(session: { messages: Array<{ role: string; content: string; citations?: string[]; timestamp: number }> }) {
  messages.value = session.messages.map((msg, idx) => ({
    id: `${msg.role}-${idx}-${msg.timestamp}`,
    role: msg.role as 'user' | 'assistant',
    content: msg.content,
    citations: msg.citations || [],
    timestamp: msg.timestamp,
  }))
  scrollToBottom()
}

function switchMode(mode: ChatMode) {
  chatMode.value = mode
  // 创建新会话
  const session = createNewSession(mode)
  conversationId.value = session.id
  messages.value = []
}

function toggleMode() {
  switchMode(chatMode.value === 'rag' ? 'llm' : 'rag')
}

function handleNewSession() {
  const session = createNewSession(chatMode.value)
  conversationId.value = session.id
  messages.value = []
}

function handleSwitchSession(id: string) {
  switchToSession(id)
}

function handleDeleteSession(id: string) {
  deleteSession(id)
}

function handleClearAll() {
  clearAllSessions()
  messages.value = []
  conversationId.value = `conv-${Date.now()}`
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function formatBubbleTime(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function handleSuggestion(q: string) {
  question.value = q
  sendMessage()
}

function pushMessage(role: 'user' | 'assistant', content: string, citations: string[] = []) {
  const id = `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`
  const timestamp = Date.now()
  messages.value.push({ id, role, content, citations, timestamp })

  // 持久化到 session
  storage.addMessage({ role, content, citations })

  scrollToBottom()
  return id
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

// 构建上下文消息（滑动窗口）
function buildContextMessages(): string {
  const session = activeSession.value
  if (!session) return ''
  const recent = session.messages.slice(-MAX_CONTEXT_MESSAGES)
  return recent.map(m => `${m.role === 'user' ? '用户' : '助手'}: ${m.content}`).join('\n')
}

const reviewLoading = ref(false)

/** 对当前会话生成面试复盘，并跳转到复盘面板 */
async function generateReview() {
  if (reviewLoading.value || messages.value.length === 0) return
  reviewLoading.value = true
  try {
    const review = await createReview({
      messages: messages.value
        .filter(msg => msg.content.trim())
        .map(msg => ({ role: msg.role, content: msg.content })),
      jobTitle: props.jobContext?.jobTitle,
      sessionTitle: activeSession.value?.title || '模拟面试复盘',
    })
    if (props.jobContext) {
      try {
        await linkJobReview(props.jobContext.jobId, review.id)
      } catch {
        ElMessage.warning('复盘已生成，但关联岗位失败，可在岗位详情中手动处理')
      }
    }
    ElMessage.success('复盘已生成')
    emit('navigate', 'review')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '复盘生成失败，请稍后重试')
  } finally {
    reviewLoading.value = false
  }
}

async function sendMessage() {
  if (!question.value.trim() || loading.value || streaming.value) return

  const msg = question.value.trim()
  question.value = ''
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }

  await handleStream(msg)
}

function closeStream() {
  if (streamController) {
    streamController.abort()
    streamController = null
  }
}

async function handleStream(msg: string) {
  closeStream()
  streaming.value = true
  waitingForResponse.value = true
  pushMessage('user', msg)
  const id = pushMessage('assistant', '')
  streamingId.value = id

  const assistantMsg = messages.value.find(m => m.id === id)
  const controller = new AbortController()
  streamController = controller
  try {
    await consumeSse('/api/chat/stream', {
      conversationId: conversationId.value,
      message: msg,
      mode: chatMode.value,
      jobTitle: props.jobContext?.jobTitle,
    }, {
      onMessage: (data) => {
        if (assistantMsg) {
          waitingForResponse.value = false
          assistantMsg.content += data
          syncSessionMessage(assistantMsg)
          messages.value = [...messages.value]
          scrollToBottom()
        }
      },
      onDone: () => {
        streaming.value = false
        waitingForResponse.value = false
        streamingId.value = ''
        nextTick(() => inputRef.value?.focus())
      },
    }, controller.signal)
  } catch (error) {
    if (controller.signal.aborted) return
    streaming.value = false
    waitingForResponse.value = false
    streamingId.value = ''
    if (assistantMsg && !assistantMsg.content) {
      assistantMsg.content = '连接中断，请重试。'
      assistantMsg.error = '网络错误'
      messages.value = [...messages.value]
      syncSessionMessage(assistantMsg)
    }
  } finally {
    if (streamController === controller) {
      streamController = null
    }
  }
}

function syncSessionMessage(msg: RenderMessage) {
  const session = activeSession.value
  if (!session) return
  const sessionMsg = session.messages.find(m =>
    m.role === msg.role && m.timestamp === msg.timestamp
  )
  if (sessionMsg) {
    sessionMsg.content = msg.content
  }
}

function retryMessage(msg: RenderMessage) {
  // 找到这条消息之前的最后一条用户消息
  const idx = messages.value.indexOf(msg)
  if (idx < 0) return
  const prevUserMsg = [...messages.value.slice(0, idx)].reverse().find(m => m.role === 'user')
  if (prevUserMsg) {
    // 移除错误的 assistant 消息
    messages.value = messages.value.filter(m => m.id !== msg.id)
    const session = activeSession.value
    if (session) {
      session.messages = session.messages.filter((_, i) => i !== idx)
    }
    question.value = prevUserMsg.content
    sendMessage()
  }
}

onBeforeUnmount(() => {
  closeStream()
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  position: relative;
}

/* ─── Session Sidebar ─── */
.session-sidebar {
  width: 280px;
  min-width: 280px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(20px) saturate(1.2);
  -webkit-backdrop-filter: blur(20px) saturate(1.2);
  border-right: 1px solid rgba(255, 255, 255, 0.6);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 5;
}

.session-sidebar-header {
  padding: 16px 18px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-sidebar-title {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  letter-spacing: 0.02em;
}

.session-new-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--bg-glass);
  color: var(--accent);
  font-size: 18px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.session-new-btn:hover {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px;
  color: var(--text-muted);
  font-size: 13px;
}

.session-empty-icon {
  font-size: 28px;
  opacity: 0.5;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: 2px;
  position: relative;
}

.session-item:hover {
  background: var(--accent-soft);
}

.session-item.active {
  background: linear-gradient(135deg, var(--accent-soft), rgba(36, 86, 230, 0.18));
  border-left: 3px solid var(--accent);
}

.session-item-content {
  flex: 1;
  min-width: 0;
}

.session-item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.session-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-mode-badge {
  font-family: var(--font-mono);
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.session-mode-badge.rag {
  background: var(--accent-soft);
  color: var(--accent);
}

.session-mode-badge.llm {
  background: var(--blue-soft);
  color: var(--blue);
}

.session-time {
  font-size: 11px;
  color: var(--text-muted);
}

.session-delete-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 16px;
  cursor: pointer;
  opacity: 0;
  transition: all var(--transition-fast);
}

.session-item:hover .session-delete-btn {
  opacity: 1;
}

.session-delete-btn:hover {
  background: var(--error-soft);
  color: var(--error);
}

.session-sidebar-footer {
  padding: 12px 18px;
  border-top: 1px solid var(--border-light);
}

.session-clear-btn {
  width: 100%;
  padding: 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.session-clear-btn:hover {
  border-color: var(--error);
  color: var(--error);
  background: var(--error-soft);
}

/* ─── Chat Container ─── */
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

/* ─── Toolbar ─── */
.chat-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-light);
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--bg-glass);
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
  position: relative;
}

.toolbar-btn:hover,
.toolbar-btn.active {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.toolbar-icon {
  font-size: 14px;
}

.toolbar-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toolbar-spacer {
  flex: 1;
}

.mode-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 4px 12px;
  border-radius: 20px;
  background: var(--bg-glass);
  border: 1px solid var(--border-strong);
  color: var(--text-secondary);
}

.mode-badge.rag {
  border-color: var(--accent-light);
  color: var(--accent);
  background: var(--accent-soft);
}

.mode-badge.llm {
  border-color: var(--blue-light);
  color: var(--blue);
  background: var(--blue-soft);
}

/* ─── Messages ─── */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

/* ─── Hero (empty state) ─── */
.chat-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
  position: relative;
}

.chat-hero.visible {
  opacity: 1;
  transform: translateY(0);
}

.hero-glow {
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--accent-soft) 0%, transparent 70%);
  filter: blur(40px);
  pointer-events: none;
  z-index: 0;
}

.hero-icon {
  font-size: 40px;
  margin-bottom: 16px;
  color: var(--accent);
  position: relative;
  z-index: 1;
}

.hero-title {
  font-family: var(--font-mono);
  font-size: 36px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--text), var(--accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0 0 8px;
  letter-spacing: -0.02em;
  position: relative;
  z-index: 1;
}

.hero-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 0 0 32px;
  position: relative;
  z-index: 1;
}

.hero-suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 480px;
  position: relative;
  z-index: 1;
}

/* ─── Mode Switcher ─── */
.mode-switcher {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.mode-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 28px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 2px solid var(--border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-smooth);
  min-width: 150px;
}

.mode-btn:hover {
  border-color: var(--accent-light);
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.mode-btn.active {
  border-color: var(--accent);
  background: var(--accent-soft);
  box-shadow: 0 4px 16px var(--accent-glow);
}

.mode-icon {
  font-size: 24px;
  color: var(--text-muted);
}

.mode-btn.active .mode-icon {
  color: var(--accent);
}

.mode-label {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.mode-desc {
  font-size: 11px;
  color: var(--text-muted);
}

.suggestion-btn {
  padding: 8px 18px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--border-strong);
  border-radius: 20px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
}

.suggestion-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

/* ─── Bubble Messages ─── */
.bubble-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  animation: msgSlideIn 0.3s ease;
  align-items: flex-start;
}

.bubble-row.user {
  /* 用户消息右对齐：DOM 顺序 [气泡][头像]，头像贴在气泡右侧（外侧） */
  flex-direction: row;
  justify-content: flex-end;
}

.bubble-row.assistant {
  flex-direction: row;
}

.bubble-avatar {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 2px;
}

.ai-avatar {
  background: linear-gradient(135deg, var(--accent-soft), rgba(36, 86, 230, 0.2));
  color: var(--accent);
  border: 1px solid rgba(36, 86, 230, 0.2);
}

.user-avatar {
  background: linear-gradient(135deg, var(--blue-soft), rgba(91, 143, 217, 0.15));
  color: var(--blue);
  border: 1px solid rgba(91, 143, 217, 0.2);
}

.bubble-wrapper {
  max-width: 72%;
  min-width: 0;
}

.bubble-wrapper.user {
  align-items: flex-end;
  display: flex;
  flex-direction: column;
}

.bubble-wrapper.assistant {
  align-items: flex-start;
  display: flex;
  flex-direction: column;
}

.bubble {
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  position: relative;
}

.bubble.user {
  background: linear-gradient(135deg, var(--accent), var(--accent-light));
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 12px var(--accent-glow);
}

.bubble.assistant {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: var(--text);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
  box-shadow: var(--shadow-sm);
}

.bubble-content {
  white-space: pre-wrap;
}

.bubble-time {
  font-size: 10px;
  color: var(--text-muted);
  margin-top: 4px;
  padding: 0 4px;
}

.bubble-wrapper.user .bubble-time {
  text-align: right;
}

.cursor-wrapper {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: 2px;
}

.chat-cursor {
  display: inline-block;
  width: 7px;
  height: 15px;
  background: var(--accent);
  vertical-align: text-bottom;
  animation: blink 1s step-end infinite;
  border-radius: 1px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.chat-citations {
  margin-top: 10px;
  padding: 10px 14px;
  border-left: 2px solid var(--accent-light);
  font-size: 12px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
  background: var(--accent-soft);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.chat-citations .cite-label {
  color: var(--accent);
  font-size: 11px;
  margin-bottom: 4px;
  font-weight: 600;
}

.chat-citation-item {
  padding: 3px 0;
  color: var(--text-secondary);
}

.bubble-error {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--error-soft);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-text {
  font-size: 12px;
  color: var(--error);
  flex: 1;
}

.retry-btn {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--error);
  background: transparent;
  color: var(--error);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.retry-btn:hover {
  background: var(--error);
  color: #fff;
}

/* ─── Input Area ─── */
.chat-input-area {
  border-top: 1px solid var(--border-light);
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.chat-input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  font-family: var(--font-sans);
  font-size: 14px;
  padding: 12px 16px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: var(--text);
  resize: none;
  outline: none;
  transition: all var(--transition-normal);
  line-height: 1.5;
  min-height: 44px;
  max-height: 160px;
}

.chat-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft), var(--shadow-sm);
}

.chat-input:disabled {
  background: var(--bg-secondary);
  cursor: not-allowed;
  opacity: 0.7;
}

.chat-input::placeholder {
  color: var(--text-muted);
}

.chat-send-btn {
  height: 44px;
  min-width: 44px;
  padding: 0;
  background: linear-gradient(135deg, var(--accent), var(--accent-light));
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 18px;
  cursor: pointer;
  transition: all var(--transition-normal);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px var(--accent-glow);
}

.chat-send-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px var(--accent-glow);
}

.chat-send-btn:active:not(:disabled) {
  transform: translateY(0);
}

.chat-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  box-shadow: none;
}

.send-icon {
  font-weight: 700;
  font-size: 18px;
}

.btn-loading {
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.chat-hint {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 8px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.hint-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.hint-key {
  padding: 1px 6px;
  background: var(--bg-glass);
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  font-size: 10px;
}

.hint-divider {
  color: var(--border);
}

.mode-hint {
  cursor: pointer;
  transition: color 0.15s;
}

.mode-hint:hover {
  color: var(--accent);
}

.mode-hint .hint-key {
  color: var(--accent);
  border-color: var(--accent-light);
  background: var(--accent-soft);
}

/* ─── Transitions ─── */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(-100%);
  opacity: 0;
}

@keyframes msgSlideIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ─── Responsive ─── */
@media (max-width: 640px) {
  .session-sidebar {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 20;
    box-shadow: var(--shadow-lg);
  }

  .chat-messages {
    padding: 16px;
  }

  .chat-input-area {
    padding: 12px 16px;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-suggestions {
    flex-direction: column;
    align-items: center;
  }

  .mode-switcher {
    flex-direction: column;
    align-items: center;
  }

  .bubble-wrapper {
    max-width: 85%;
  }
}

/* ─── Simple workspace chat overrides ─── */
.session-sidebar { background: #fff; backdrop-filter: none; -webkit-backdrop-filter: none; border-right: 1px solid var(--border); }
.chat-toolbar { background: #fff; border-bottom-color: var(--border); }
.chat-input-area { background: #fff; backdrop-filter: none; -webkit-backdrop-filter: none; }
.chat-hero .hero-glow { display: none; }
</style>
