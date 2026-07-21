<template>
  <div class="techguide-layout">
    <!-- 左侧模块列表 -->
    <div class="techguide-sidebar">
      <div class="techguide-sidebar-header">
        <span class="techguide-sidebar-icon">⊕</span>
        <span class="techguide-sidebar-title">技术引航图</span>
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

          <!-- 知识点标签（展开时显示） -->
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

    <!-- 右侧教学对话区 -->
    <div class="techguide-main">
      <!-- 顶部路径 -->
      <div class="techguide-breadcrumb" v-if="activeModule">
        <span class="breadcrumb-module">{{ activeModule.icon }} {{ activeModule.name }}</span>
        <span v-if="activeTag" class="breadcrumb-sep">›</span>
        <span v-if="activeTag" class="breadcrumb-tag">{{ activeTag }}</span>
      </div>

      <!-- 消息区域 -->
      <div class="techguide-messages" ref="messagesRef" @scroll="handleScroll">
        <!-- 空状态 -->
        <div v-if="messages.length === 0" class="techguide-empty">
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

        <!-- 消息列表 -->
        <div v-for="(msg, idx) in messages" :key="idx" class="chat-msg" :class="msg.role">
          <span class="prompt-char">{{ msg.role === 'user' ? '>' : '✦' }}</span>
          <div class="msg-body">
            <span v-html="renderMarkdown(msg.content)"></span>
            <span v-if="msg.role === 'assistant' && streaming && idx === messages.length - 1" class="chat-cursor"></span>

            <!-- 推荐追问按钮 -->
            <div v-if="msg.role === 'assistant' && followupsMap[idx]?.length && !streaming" class="followup-section">
              <div class="followup-hint">👇 想要继续深入吗？点击下方问题，AI 将带你往下深挖：</div>
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

        <!-- 加载指示器 -->
        <div v-if="waitingForResponse" class="chat-loading">
          <ThinkingIndicator variant="inline" />
        </div>
      </div>

      <!-- 输入区域 -->
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
            <span v-else>发送</span>
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

        <!-- 清空确认气泡 -->
        <transition name="fade">
          <div v-if="showClearConfirm" class="clear-confirm-popover">
            <div class="clear-confirm-text">确定要清空当前对话吗？历史记录将丢失。</div>
            <div class="clear-confirm-actions">
              <button class="clear-btn clear-btn-cancel" @click="showClearConfirm = false">取消</button>
              <button class="clear-btn clear-btn-ok" @click="doClearChat">确定清空</button>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import type { TechGuideModule, TechGuideMessage } from '../types'
import ThinkingIndicator from './ThinkingIndicator.vue'

// 8 大模块定义
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
const conversationId = ref(`tg-${Date.now()}`)
const showClearConfirm = ref(false)

// 追问问题映射：messageIndex -> followup[]
const followupsMap = reactive<Record<number, string[]>>({})

// 智能滚动状态
let userScrolledUp = false
let scrollResumeTimer: ReturnType<typeof setTimeout> | null = null

let eventSource: EventSource | null = null

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

function sendToBackend(msg: string) {
  if (!activeModule.value) {
    ElMessage.warning('请先选择一个学习模块')
    return
  }

  messages.value = [...messages.value, { role: 'user', content: msg }]
  messages.value = [...messages.value, { role: 'assistant', content: '' }]

  streaming.value = true
  waitingForResponse.value = true
  userScrolledUp = false
  scrollToBottom()

  const url = new URL('/api/techguide/stream', window.location.origin)
  url.searchParams.set('module', activeModule.value.id)
  url.searchParams.set('tag', activeTag.value)
  url.searchParams.set('message', msg)
  url.searchParams.set('conversationId', conversationId.value)

  closeStream()
  eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.role === 'assistant') {
      waitingForResponse.value = false
      lastMsg.content += (lastMsg.content ? '\n' : '') + event.data
      messages.value = [...messages.value]
      smartScrollToBottom()
    }
  }

  eventSource.addEventListener('followups', (event) => {
    const data = event.data as string
    if (data) {
      const followups = data.split('|||').filter(Boolean)
      const assistantIdx = messages.value.length - 1
      followupsMap[assistantIdx] = followups
    }
  })

  eventSource.addEventListener('done', () => {
    streaming.value = false
    waitingForResponse.value = false
    closeStream()
    nextTick(() => inputRef.value?.focus())
  })

  eventSource.onerror = () => {
    streaming.value = false
    waitingForResponse.value = false
    closeStream()
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
      lastMsg.content = '⚠️ 连接中断，请稍后重试'
      messages.value = [...messages.value]
    }
  }
}

function closeStream() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

// === 智能自动滚动 ===
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

// === 清空对话确认 ===
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

function renderMarkdown(text: string): string {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_match, lang, code) => {
    const langLabel = lang ? `<div class="code-lang">${lang}</div>` : ''
    return `<div class="code-block-wrapper">${langLabel}<pre class="code-block"><code>${code}</code></pre></div>`
  })
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
  html = html.replace(/^### (.+)$/gm, '<h4 class="md-h4">$1</h4>')
  html = html.replace(/^## (.+)$/gm, '<h3 class="md-h3">$1</h3>')
  html = html.replace(/^# (.+)$/gm, '<h2 class="md-h2">$1</h2>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')
  html = html.replace(/^- (.+)$/gm, '<div class="md-li">• $1</div>')
  html = html.replace(/^\d+\. (.+)$/gm, '<div class="md-oli">$1</div>')
  html = html.replace(/((?:^.*[─│┌┐└┘┬┴├┤┼╔╗╚╝═║╠╣╦╩╬].*\n?){3,})/gm, '<pre class="ascii-diagram">$1</pre>')
  html = html.replace(/\n/g, '<br>')

  return html
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
}

.techguide-sidebar {
  width: 280px;
  min-width: 280px;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.techguide-sidebar-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
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
}

.techguide-modules {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.module-card {
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: 4px;
  border-left: 3px solid transparent;
}

.module-card:hover {
  background: var(--bg-hover);
}

.module-card.active {
  background: var(--bg-hover);
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
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--bg);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.module-tag:hover {
  border-color: var(--accent);
  color: var(--text);
}

.module-tag.active {
  background: var(--accent);
  color: var(--bg);
  border-color: var(--accent);
}

.techguide-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.techguide-breadcrumb {
  padding: 12px 20px;
  border-bottom: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 8px;
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
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.empty-title {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--text-muted);
  max-width: 320px;
  line-height: 1.6;
  margin-bottom: 24px;
}

.empty-modules {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.empty-module-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.empty-module-chip:hover {
  border-color: var(--accent);
  color: var(--text);
}

.chat-msg {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  animation: slideInUp 0.2s ease;
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
  color: var(--text-muted);
  user-select: none;
  min-width: 14px;
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
  background: var(--border);
  color: var(--text-muted);
  border-radius: 0 var(--radius-sm) 0 var(--radius-sm);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.msg-body :deep(.code-block) {
  background: #1e1e2e;
  border: 1px solid var(--border);
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
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 3px;
  padding: 1px 5px;
  font-family: var(--font-mono);
  font-size: 12px;
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
  border: 1px solid var(--accent);
  border-radius: 20px;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  transition: all var(--transition-fast);
  line-height: 1.4;
  text-align: left;
}

.followup-chip:hover {
  background: var(--accent);
  color: var(--bg);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.followup-chip:active {
  transform: translateY(0);
}

.chat-cursor {
  display: inline-block;
  width: 2px;
  height: 14px;
  background: var(--text);
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.chat-loading {
  display: flex;
  gap: 6px;
  padding: 8px 0;
}

.chat-input-area {
  border-top: 1px solid var(--border);
  padding: 12px 20px;
  position: relative;
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
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg);
  color: var(--text);
  resize: none;
  outline: none;
  transition: border-color var(--transition-fast);
  min-height: 40px;
  max-height: 120px;
}

.chat-input:focus {
  border-color: var(--accent);
}

.chat-input::placeholder {
  color: var(--text-muted);
}

.chat-send-btn {
  font-family: var(--font-mono);
  font-size: 13px;
  padding: 10px 18px;
  background: var(--accent);
  color: var(--bg);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.chat-send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.chat-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
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
  border-top-color: var(--bg);
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
  border: 1px solid var(--border-light);
  border-radius: 3px;
  background: var(--bg-secondary);
}

.hint-clear {
  cursor: pointer;
  transition: all var(--transition-fast);
}

.hint-clear:hover {
  border-color: #e74c3c;
  color: #e74c3c;
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
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 14px 18px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
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
  border: 1px solid var(--border);
}

.clear-btn-cancel {
  background: var(--bg);
  color: var(--text-secondary);
}

.clear-btn-cancel:hover {
  background: var(--bg-hover);
}

.clear-btn-ok {
  background: #e74c3c;
  color: #fff;
  border-color: #e74c3c;
}

.clear-btn-ok:hover {
  background: #c0392b;
}

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

@keyframes slideInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
