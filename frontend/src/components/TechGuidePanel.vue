<template>
  <div class="techguide-layout">
    <!-- 左侧模块列表 -->
    <div class="techguide-sidebar">
      <div class="techguide-sidebar-header">
        <span class="techguide-sidebar-icon">🧭</span>
        <span class="techguide-sidebar-title">技术引航图</span>
        <button class="tg-session-btn" @click="toggleSessionSidebar" title="历史会话">
          📋
          <span v-if="sessionList.length > 0" class="tg-session-badge">{{ sessionList.length }}</span>
        </button>
      </div>

      <div class="techguide-modules">
        <div
          v-for="mod in modules"
          :key="mod.id"
          class="module-card"
          :class="{ active: activeModule?.id === mod.id }"
          @click="selectModule(mod)"
        >
          <div class="module-card-header">
            <span class="module-icon">{{ mod.icon }}</span>
            <span class="module-name">{{ mod.name }}</span>
          </div>
          <div class="module-desc">{{ mod.description }}</div>

          <transition name="expand">
            <div v-if="activeModule?.id === mod.id" class="module-tags">
              <button
                v-for="tag in mod.tags"
                :key="tag"
                class="module-tag"
                :class="{ active: activeTag === tag }"
                @click.stop="startLesson(tag)"
              >
                {{ tag }}
              </button>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <!-- 会话侧边栏 -->
    <transition name="slide-right">
      <div v-if="showSessionSidebar" class="tg-session-sidebar">
        <div class="tg-session-header">
          <span class="tg-session-title">历史会话</span>
          <button class="tg-session-new" @click="handleNewSession">+</button>
        </div>
        <div class="tg-session-list">
          <div v-if="sessionList.length === 0" class="tg-session-empty">暂无历史</div>
          <div
            v-for="session in sessionList"
            :key="session.id"
            class="tg-session-item"
            :class="{ active: session.id === activeSessionId }"
            @click="handleSwitchSession(session.id)"
          >
            <div class="tg-session-item-title">{{ session.title }}</div>
            <div class="tg-session-item-time">{{ formatTime(session.updatedAt) }}</div>
            <button class="tg-session-del" @click.stop="handleDeleteSession(session.id)">×</button>
          </div>
        </div>
        <div class="tg-session-footer" v-if="sessionList.length > 0">
          <button class="tg-session-clear" @click="handleClearAll">清空所有</button>
        </div>
      </div>
    </transition>

    <!-- 右侧教学对话区 -->
    <div class="techguide-main">
      <div class="techguide-breadcrumb" v-if="activeModule">
        <span class="breadcrumb-module">{{ activeModule.icon }} {{ activeModule.name }}</span>
        <span v-if="activeTag" class="breadcrumb-sep">›</span>
        <span v-if="activeTag" class="breadcrumb-tag">{{ activeTag }}</span>
      </div>

      <div class="techguide-messages" ref="messagesRef" @scroll="handleScroll">
        <div v-if="messages.length === 0" class="techguide-empty">
          <div class="empty-glow"></div>
          <div class="empty-icon">🧭</div>
          <div class="empty-title">选择一个模块开始学习</div>
          <div class="empty-desc">从左侧选择学习模块，点击知识点标签即可开始 AI 导师教学</div>
          <div class="empty-modules">
            <div v-for="mod in modules.slice(0, 4)" :key="mod.id" class="empty-module-chip" @click="selectModule(mod)">
              <span>{{ mod.icon }}</span>
              <span>{{ mod.name }}</span>
            </div>
          </div>
        </div>

        <div v-for="(msg, idx) in messages" :key="idx" class="chat-msg" :class="msg.role">
          <span class="prompt-char">{{ msg.role === 'user' ? '>' : '✦' }}</span>
          <div class="msg-body">
            <span v-html="renderMarkdown(msg.content)"></span>
            <span v-if="msg.role === 'assistant' && streaming && idx === messages.length - 1" class="chat-cursor"></span>

            <div v-if="msg.role === 'assistant' && followupsMap[idx]?.length && !streaming" class="followup-section">
              <div class="followup-hint">👇 想要继续深入吗？点击下方问题：</div>
              <div class="followup-chips">
                <button
                  v-for="(fq, fi) in followupsMap[idx]"
                  :key="fi"
                  class="followup-chip"
                  @click="handleFollowup(fq)"
                >
                  {{ fq }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="waitingForResponse" class="chat-loading">
          <ThinkingIndicator variant="inline" />
        </div>
      </div>

      <div class="chat-input-area" v-if="activeModule">
        <div class="chat-input-row">
          <textarea
            ref="inputRef"
            class="chat-input"
            v-model="question"
            :placeholder="activeTag ? `继续追问 ${activeModule.name} · ${activeTag}...` : `向 AI 导师提问...`"
            @keydown="handleKeydown"
            rows="1"
            @input="autoResize"
            :disabled="streaming"
          ></textarea>
          <button
            class="chat-send-btn"
            :disabled="streaming || !question.trim()"
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
          <span v-if="activeModule" class="hint-divider">·</span>
          <span v-if="activeModule" class="hint-item">
            <span class="hint-key hint-clear" @click="confirmClearChat">清空对话</span>
          </span>
        </div>

        <transition name="fade">
          <div v-if="showClearConfirm" class="clear-confirm-popover">
            <div class="clear-confirm-text">确定要清空当前对话吗？</div>
            <div class="clear-confirm-actions">
              <button class="clear-btn clear-btn-cancel" @click="showClearConfirm = false">取消</button>
              <button class="clear-btn clear-btn-ok" @click="doClearChat">确定</button>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onBeforeUnmount, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { TechGuideModule, TechGuideMessage } from '../types'
import ThinkingIndicator from './ThinkingIndicator.vue'
import { useSessionManager } from '../composables/useSessionManager'
import { useMarkdown } from '../composables/useMarkdown'
import { consumeSse } from '../composables/useSseFetch'

const { renderMarkdown } = useMarkdown()

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
} = useSessionManager('techguide')

const modules: TechGuideModule[] = [
  {
    id: 'java',
    name: 'Java基础',
    icon: '☕',
    description: '集合框架、IO流、多线程、JVM内存',
    tags: ['集合框架', 'IO流', '多线程', 'JVM内存', '泛型', '异常处理']
  },
  {
    id: 'spring',
    name: 'Spring生态',
    icon: '🌿',
    description: 'IoC/AOP、Boot、Security、Cloud',
    tags: ['IoC容器', 'AOP切面', 'Boot自动配置', 'Security', 'Cloud', '事务管理']
  },
  {
    id: 'database',
    name: '数据库与缓存',
    icon: '🗄️',
    description: 'MySQL索引、Redis缓存、MyBatis-Plus',
    tags: ['MySQL索引', 'SQL优化', 'Redis数据结构', '缓存策略', 'MyBatis-Plus', '分库分表']
  },
  {
    id: 'rag',
    name: 'RAG核心技术',
    icon: '🔍',
    description: '向量检索、Embedding、分块策略',
    tags: ['向量检索', 'Embedding原理', '分块策略', '混合检索', '重排序', '知识图谱']
  },
  {
    id: 'agent',
    name: 'Agent架构',
    icon: '🤖',
    description: '意图路由、工具调用、多Agent编排',
    tags: ['意图路由', '工具调用', 'ReAct模式', '多Agent协作', '记忆管理', '规划与反思']
  },
  {
    id: 'security',
    name: '安全机制',
    icon: '🔒',
    description: '认证授权、PII脱敏、注入防护',
    tags: ['Sa-Token认证', 'RBAC权限', 'PII脱敏', 'SQL注入防护', 'XSS防护', 'CSRF防护']
  },
  {
    id: 'resume-agent',
    name: '实战:ResumeAgent',
    icon: '📄',
    description: 'RAG流水线、知识库、SSE流式',
    tags: ['RAG检索流水线', 'SSE流式输出', '知识库构建', 'Prompt工程', '安全检测', '记忆管理']
  },
  {
    id: 'fundpilot',
    name: '实战:FundPilot',
    icon: '📈',
    description: '基金分析、AI决策、数据管道',
    tags: ['Two-stage路由', '基金数据获取', 'LLM决策链', '流式输出', '缓存策略', '异常处理']
  }
]

const activeModule = ref<TechGuideModule | null>(null)
const activeTag = ref<string>('')
const question = ref('')
const messages = ref<TechGuideMessage[]>([])
const streaming = ref(false)
const waitingForResponse = ref(false)
const messagesRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const conversationId = ref('')
const showClearConfirm = ref(false)

const followupsMap = reactive<Record<number, string[]>>({})

let userScrolledUp = false
let scrollResumeTimer: ReturnType<typeof setTimeout> | null = null
let streamController: AbortController | null = null

onMounted(() => {
  initSession()
  const session = activeSession.value
  if (session) {
    conversationId.value = session.id
    loadMessagesFromSession(session)
  } else {
    conversationId.value = `tg-${Date.now()}`
  }
})

watch(activeSessionId, (newId) => {
  if (!newId) return
  const session = storage.getSession(newId)
  if (session) {
    conversationId.value = session.id
    loadMessagesFromSession(session)
    closeStream()
    streaming.value = false
    waitingForResponse.value = false
  }
})

function loadMessagesFromSession(session: { messages: Array<{ role: string; content: string }> }) {
  messages.value = session.messages.map(msg => ({
    role: msg.role as 'user' | 'assistant',
    content: msg.content,
  }))
  scrollToBottom()
}

function handleNewSession() {
  const session = createNewSession()
  conversationId.value = session.id
  messages.value = []
  Object.keys(followupsMap).forEach(key => delete followupsMap[Number(key)])
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
  Object.keys(followupsMap).forEach(key => delete followupsMap[Number(key)])
  conversationId.value = `tg-${Date.now()}`
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

function selectModule(mod: TechGuideModule) {
  if (activeModule.value?.id === mod.id) {
    activeModule.value = null
    activeTag.value = ''
    return
  }
  activeModule.value = mod
  activeTag.value = ''
}

function startLesson(tag: string) {
  activeTag.value = tag
  const msg = `请讲解「${tag}」这个知识点`
  sendToBackend(msg)
}

function handleFollowup(fq: string) {
  question.value = ''
  sendToBackend(fq)
}

function sendMessage() {
  const msg = question.value.trim()
  if (!msg || streaming.value) return
  question.value = ''
  resetTextareaHeight()
  sendToBackend(msg)
}

async function sendToBackend(msg: string) {
  if (!activeModule.value) {
    ElMessage.warning('请先选择一个学习模块')
    return
  }

  messages.value = [...messages.value, { role: 'user', content: msg }]
  storage.addMessage({ role: 'user', content: msg })

  messages.value = [...messages.value, { role: 'assistant', content: '' }]

  streaming.value = true
  waitingForResponse.value = true
  userScrolledUp = false
  scrollToBottom()

  closeStream()
  const controller = new AbortController()
  streamController = controller
  try {
    await consumeSse('/api/techguide/stream', {
      module: activeModule.value.id,
      tag: activeTag.value,
      message: msg,
      conversationId: conversationId.value,
    }, {
      onMessage: (data) => {
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant') {
          waitingForResponse.value = false
          lastMsg.content += data
          messages.value = [...messages.value]
          smartScrollToBottom()
        }
      },
      onFollowups: (data) => {
        if (data) {
          const followups = data.split('|||').filter(Boolean)
          const assistantIdx = messages.value.length - 1
          followupsMap[assistantIdx] = followups
        }
      },
      onDone: () => {
        streaming.value = false
        waitingForResponse.value = false
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant') {
          storage.addMessage({ role: 'assistant', content: lastMsg.content })
        }
        nextTick(() => inputRef.value?.focus())
      },
    }, controller.signal)
  } catch (error) {
    if (controller.signal.aborted) return
    streaming.value = false
    waitingForResponse.value = false
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
      lastMsg.content = '⚠️ 连接中断，请稍后重试'
      messages.value = [...messages.value]
    }
  } finally {
    if (streamController === controller) {
      streamController = null
    }
  }
}

function closeStream() {
  if (streamController) {
    streamController.abort()
    streamController = null
  }
}

function handleScroll() {
  if (!messagesRef.value) return
  const el = messagesRef.value
  const isAtBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 60

  if (!isAtBottom && streaming.value) {
    userScrolledUp = true
    if (scrollResumeTimer) clearTimeout(scrollResumeTimer)
    scrollResumeTimer = setTimeout(() => {
      userScrolledUp = false
      if (streaming.value) scrollToBottom()
    }, 5000)
  } else if (isAtBottom) {
    userScrolledUp = false
    if (scrollResumeTimer) {
      clearTimeout(scrollResumeTimer)
      scrollResumeTimer = null
    }
  }
}

function smartScrollToBottom() {
  if (!userScrolledUp) {
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function confirmClearChat() {
  showClearConfirm.value = true
}

function doClearChat() {
  messages.value = []
  Object.keys(followupsMap).forEach(key => delete followupsMap[Number(key)])
  conversationId.value = `tg-${Date.now()}`
  showClearConfirm.value = false
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function autoResize(e: Event) {
  const textarea = e.target as HTMLTextAreaElement
  textarea.style.height = 'auto'
  textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px'
}

function resetTextareaHeight() {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
}

onBeforeUnmount(() => {
  closeStream()
  if (scrollResumeTimer) clearTimeout(scrollResumeTimer)
})
</script>

<style scoped>
.techguide-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  position: relative;
}

/* ─── Sidebar ─── */
.techguide-sidebar {
  width: 280px;
  min-width: 280px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-right: 1px solid rgba(255, 255, 255, 0.5);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.techguide-sidebar-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  gap: 8px;
}

.techguide-sidebar-icon {
  font-size: 18px;
}

.techguide-sidebar-title {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text);
  flex: 1;
}

.tg-session-btn {
  position: relative;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--bg-glass);
  cursor: pointer;
  font-size: 14px;
  transition: all var(--transition-fast);
}

.tg-session-btn:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.tg-session-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.techguide-modules {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.module-card {
  padding: 12px 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-normal);
  margin-bottom: 4px;
  border-left: 3px solid transparent;
}

.module-card:hover {
  background: var(--accent-soft);
}

.module-card.active {
  background: linear-gradient(135deg, var(--accent-soft), rgba(36, 86, 230, 0.15));
  border-left-color: var(--accent);
}

.module-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.module-icon {
  font-size: 16px;
  width: 20px;
  text-align: center;
}

.module-name {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.module-desc {
  font-size: 12px;
  color: var(--text-muted);
  padding-left: 28px;
  line-height: 1.4;
}

.module-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 0 4px 28px;
}

.module-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--bg-glass);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.module-tag:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.module-tag.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-light));
  color: #fff;
  border-color: var(--accent);
  box-shadow: 0 2px 8px var(--accent-glow);
}

/* ─── Session Sidebar ─── */
.tg-session-sidebar {
  width: 240px;
  min-width: 240px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255, 255, 255, 0.6);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tg-session-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tg-session-title {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
}

.tg-session-new {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--bg-glass);
  color: var(--accent);
  font-size: 16px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tg-session-new:hover {
  background: var(--accent);
  color: #fff;
}

.tg-session-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

.tg-session-empty {
  text-align: center;
  padding: 30px 16px;
  color: var(--text-muted);
  font-size: 12px;
}

.tg-session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: 2px;
}

.tg-session-item:hover {
  background: var(--accent-soft);
}

.tg-session-item.active {
  background: linear-gradient(135deg, var(--accent-soft), rgba(36, 86, 230, 0.18));
  border-left: 3px solid var(--accent);
}

.tg-session-item-title {
  flex: 1;
  font-size: 12px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tg-session-item-time {
  font-size: 10px;
  color: var(--text-muted);
}

.tg-session-del {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  opacity: 0;
  transition: all var(--transition-fast);
}

.tg-session-item:hover .tg-session-del {
  opacity: 1;
}

.tg-session-del:hover {
  background: var(--error-soft);
  color: var(--error);
}

.tg-session-footer {
  padding: 10px 16px;
  border-top: 1px solid var(--border-light);
}

.tg-session-clear {
  width: 100%;
  padding: 6px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tg-session-clear:hover {
  border-color: var(--error);
  color: var(--error);
}

/* ─── Main Area ─── */
.techguide-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.techguide-breadcrumb {
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-light);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.breadcrumb-module {
  font-weight: 600;
  color: var(--text);
}

.breadcrumb-sep {
  color: var(--text-muted);
}

.breadcrumb-tag {
  color: var(--accent);
  font-weight: 500;
}

.techguide-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.techguide-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  position: relative;
}

.empty-glow {
  position: absolute;
  width: 250px;
  height: 250px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--accent-soft) 0%, transparent 70%);
  filter: blur(40px);
  pointer-events: none;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.7;
  position: relative;
  z-index: 1;
}

.empty-title {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
  position: relative;
  z-index: 1;
}

.empty-desc {
  font-size: 13px;
  color: var(--text-muted);
  max-width: 320px;
  line-height: 1.6;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.empty-modules {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  position: relative;
  z-index: 1;
}

.empty-module-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.empty-module-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
  transform: translateY(-2px);
}

/* ─── Messages ─── */
.chat-msg {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  animation: msgSlideIn 0.3s ease;
}

.chat-msg.user {
  color: var(--text);
}

.chat-msg.assistant {
  color: var(--text-secondary);
}

.prompt-char {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--accent);
  user-select: none;
  min-width: 14px;
}

.chat-msg.assistant .prompt-char {
  color: var(--accent);
}

.msg-body {
  font-size: 14px;
  line-height: 1.7;
  flex: 1;
  word-break: break-word;
}

.msg-body :deep(.md-h2) {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  margin: 16px 0 8px;
  color: var(--text);
}

.msg-body :deep(.md-h3) {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 700;
  margin: 14px 0 6px;
  color: var(--text);
}

.msg-body :deep(.md-h4) {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  margin: 12px 0 4px;
  color: var(--text);
}

.msg-body :deep(.code-block-wrapper) {
  position: relative;
  margin: 10px 0;
}

.msg-body :deep(.code-lang) {
  position: absolute;
  top: 0;
  right: 0;
  font-family: var(--font-mono);
  font-size: 10px;
  padding: 2px 8px;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.5);
  border-radius: 0 var(--radius-sm) 0 var(--radius-sm);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.msg-body :deep(.code-block) {
  background: #1e1e2e;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: var(--radius-sm);
  padding: 14px 16px;
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.6;
  overflow-x: auto;
  color: #cdd6f4;
  tab-size: 4;
  margin: 8px 0;
}

.msg-body :deep(.inline-code) {
  background: var(--accent-soft);
  border: 1px solid rgba(36, 86, 230, 0.12);
  border-radius: 4px;
  padding: 1px 6px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--accent);
}

.msg-body :deep(.ascii-diagram) {
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.5;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px 16px;
  overflow-x: auto;
  margin: 10px 0;
  white-space: pre;
  color: var(--text-secondary);
}

.msg-body :deep(.md-li),
.msg-body :deep(.md-oli) {
  padding-left: 4px;
  margin: 2px 0;
}

.followup-section {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
}

.followup-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
  line-height: 1.5;
}

.followup-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.followup-chip {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 6px 14px;
  border: 1px solid var(--accent-light);
  border-radius: 20px;
  background: var(--accent-soft);
  color: var(--accent);
  cursor: pointer;
  transition: all var(--transition-normal);
  line-height: 1.4;
  text-align: left;
}

.followup-chip:hover {
  background: linear-gradient(135deg, var(--accent), var(--accent-light));
  color: #fff;
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--accent-glow);
}

.followup-chip:active {
  transform: translateY(0);
}

.chat-cursor {
  display: inline-block;
  width: 2px;
  height: 14px;
  background: var(--accent);
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink 1s step-end infinite;
  border-radius: 1px;
}

@keyframes blink {
  50% { opacity: 0; }
}

.chat-loading {
  display: flex;
  gap: 6px;
  padding: 8px 0;
}

/* ─── Input ─── */
.chat-input-area {
  border-top: 1px solid var(--border-light);
  padding: 12px 20px;
  position: relative;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.chat-input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  font-family: var(--font-sans);
  font-size: 14px;
  padding: 10px 14px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.85);
  color: var(--text);
  resize: none;
  outline: none;
  transition: all var(--transition-normal);
  min-height: 40px;
  max-height: 120px;
}

.chat-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.chat-input::placeholder {
  color: var(--text-muted);
}

.chat-send-btn {
  font-family: var(--font-mono);
  font-size: 13px;
  padding: 10px 14px;
  background: linear-gradient(135deg, var(--accent), var(--accent-light));
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-normal);
  white-space: nowrap;
  box-shadow: 0 2px 8px var(--accent-glow);
  min-width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-send-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px var(--accent-glow);
}

.chat-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  box-shadow: none;
}

.send-icon {
  font-weight: 700;
  font-size: 16px;
}

.btn-loading {
  display: flex;
  align-items: center;
  gap: 6px;
}

.loading-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.chat-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0 0;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.hint-key {
  padding: 1px 5px;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  background: var(--bg-glass);
}

.hint-clear {
  cursor: pointer;
  transition: all var(--transition-fast);
}

.hint-clear:hover {
  border-color: var(--error);
  color: var(--error);
}

.hint-divider {
  color: var(--border);
}

.clear-confirm-popover {
  position: absolute;
  bottom: 100%;
  left: 20px;
  right: 20px;
  margin-bottom: 8px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--radius-md);
  padding: 14px 18px;
  box-shadow: var(--shadow-float);
  z-index: 10;
}

.clear-confirm-text {
  font-size: 13px;
  color: var(--text);
  margin-bottom: 12px;
  line-height: 1.5;
}

.clear-confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.clear-btn {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  border: 1px solid var(--border-strong);
}

.clear-btn-cancel {
  background: var(--bg-glass);
  color: var(--text-secondary);
}

.clear-btn-cancel:hover {
  background: var(--bg-hover);
}

.clear-btn-ok {
  background: var(--error);
  color: #fff;
  border-color: var(--error);
}

.clear-btn-ok:hover {
  opacity: 0.9;
}

/* ─── Transitions ─── */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 200px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

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
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ─── Light documentation surface ─── */
.techguide-sidebar, .tg-session-sidebar { background: #fff; backdrop-filter: none; -webkit-backdrop-filter: none; border-color: var(--border); }
.msg-body :deep(.code-block) { background: #f6f8fb; border-color: var(--border); color: var(--text); }
.msg-body :deep(.code-lang) { background: var(--accent-soft); color: var(--accent); }
</style>
