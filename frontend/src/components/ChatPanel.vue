<template>
  <div class="chat-container">
    <!-- 消息区域 -->
    <div class="chat-messages" ref="messagesRef">
      <!-- 欢迎区域 - 无消息时显示 -->
      <div v-if="messages.length === 0" class="chat-hero" :class="{ 'visible': heroReady }">
        <div class="hero-icon">✦</div>
        <h1 class="hero-title">ResumeAgent</h1>
        <p class="hero-subtitle">有什么我可以帮你的？</p>

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

      <!-- 消息列表 -->
      <div v-for="msg in messages" :key="msg.id" class="chat-msg" :class="msg.role">
        <span class="prompt-char">{{ msg.role === 'user' ? '>' : '✦' }}</span>
        <div class="msg-body">
          <span v-html="formatContent(msg.content)"></span>
          <span v-if="msg.role === 'assistant' && streaming && msg.id === streamingId" class="chat-cursor"></span>

          <!-- 引用 -->
          <div v-if="msg.citations.length > 0" class="chat-citations">
            <div class="cite-label">── references ──</div>
            <div v-for="(cite, i) in msg.citations" :key="i" class="chat-citation-item">
              [{{ i + 1 }}] {{ cite }}
            </div>
          </div>
        </div>
      </div>

      <!-- 加载指示器 -->
      <div v-if="loading && !streaming" class="chat-loading">
        <span class="loading-dot"></span>
        <span class="loading-dot"></span>
        <span class="loading-dot"></span>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area">
      <div class="chat-input-row">
        <textarea
          ref="inputRef"
          class="chat-input"
          v-model="question"
          placeholder="输入问题... (Enter 发送, Shift+Enter 换行)"
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
          <span v-else>{{ loading ? '...' : '发送' }}</span>
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
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { postChat } from '../api/chat'

interface RenderMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: string[]
}

const props = defineProps<{
  initialQuestion?: string
}>()

const emit = defineEmits<{
  (e: 'question-sent'): void
}>()

const suggestions = [
  '介绍一下你的技术栈',
  '你有哪些项目经历',
  '你的优势是什么',
  '介绍一下你的实习经历'
]

const conversationId = ref(`conv-${Date.now()}`)
const question = ref('')
const loading = ref(false)
const streaming = ref(false)
const streamingId = ref('')
const messages = ref<RenderMessage[]>([])
const messagesRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const heroReady = ref(false)
let eventSource: EventSource | null = null

onMounted(() => {
  inputRef.value?.focus()
  setTimeout(() => {
    heroReady.value = true
  }, 50)
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

const formatContent = (value: string) => {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
}

function handleSuggestion(q: string) {
  question.value = q
  sendMessage()
}

function pushMessage(role: 'user' | 'assistant', content: string, citations: string[] = []) {
  const id = `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`
  messages.value.push({ id, role, content, citations })
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
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

async function handleStream(msg: string) {
  closeStream()
  streaming.value = true
  pushMessage('user', msg)
  const id = pushMessage('assistant', '')
  streamingId.value = id

  const url = new URL('/api/chat/stream', window.location.origin)
  url.searchParams.set('conversationId', conversationId.value)
  url.searchParams.set('message', msg)
  eventSource = new EventSource(url)

  const assistantMsg = messages.value.find(m => m.id === id)

  eventSource.onmessage = (event) => {
    if (assistantMsg) {
      assistantMsg.content = assistantMsg.content
        ? `${assistantMsg.content}\n${event.data}`
        : event.data
      messages.value = [...messages.value]
      scrollToBottom()
    }
  }

  eventSource.addEventListener('done', () => {
    streaming.value = false
    streamingId.value = ''
    closeStream()
    nextTick(() => inputRef.value?.focus())
  })

  eventSource.onerror = () => {
    streaming.value = false
    streamingId.value = ''
    closeStream()
    if (assistantMsg && !assistantMsg.content) {
      assistantMsg.content = '连接中断，请重试。'
      messages.value = [...messages.value]
    }
  }
}

onBeforeUnmount(() => {
  closeStream()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

/* 欢迎区域 */
.chat-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}

.chat-hero.visible {
  opacity: 1;
  transform: translateY(0);
}

.hero-icon {
  font-size: 36px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.hero-title {
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.hero-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 0 0 32px;
}

.hero-suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 480px;
}

.suggestion-btn {
  padding: 8px 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 20px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.suggestion-btn:hover {
  border-color: var(--text);
  color: var(--text);
  transform: translateY(-1px);
}

/* 消息样式 */
.chat-msg {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  font-family: var(--font-mono);
  font-size: 13.5px;
  line-height: 1.7;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.chat-msg .prompt-char {
  flex-shrink: 0;
  width: 18px;
  text-align: center;
  color: var(--text-muted);
  user-select: none;
  font-weight: 500;
}

.chat-msg.user .prompt-char {
  color: var(--accent);
}

.chat-msg .msg-body {
  flex: 1;
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-msg.user .msg-body {
  color: var(--text);
}

.chat-msg.assistant .msg-body {
  color: var(--text);
}

.chat-cursor {
  display: inline-block;
  width: 7px;
  height: 15px;
  background: var(--text);
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.chat-citations {
  margin-top: 8px;
  padding: 8px 12px;
  border-left: 2px solid var(--border);
  font-size: 12px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
}

.chat-citations .cite-label {
  color: var(--text-muted);
  font-size: 11px;
  margin-bottom: 4px;
}

.chat-citation-item {
  padding: 3px 0;
  color: var(--text-secondary);
}

/* 加载指示器 */
.chat-loading {
  display: flex;
  gap: 6px;
  padding: 12px 0;
  align-items: center;
}

.loading-dot {
  width: 6px;
  height: 6px;
  background: var(--text-muted);
  border-radius: 50%;
  animation: dotPulse 1.4s ease-in-out infinite;
}

.loading-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.loading-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dotPulse {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* 输入区域 */
.chat-input-area {
  border-top: 1px solid var(--border);
  padding: 16px 24px;
  background: var(--bg);
}

.chat-input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 14px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg);
  color: var(--text);
  resize: none;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  line-height: 1.5;
  min-height: 42px;
  max-height: 160px;
}

.chat-input:focus {
  border-color: var(--text);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.05);
}

.chat-input:disabled {
  background: var(--bg-secondary);
  cursor: not-allowed;
}

.chat-input::placeholder {
  color: var(--text-muted);
}

.chat-send-btn {
  height: 42px;
  min-width: 72px;
  padding: 0 18px;
  background: var(--text);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.1s;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-send-btn:hover:not(:disabled) {
  opacity: 0.85;
  transform: translateY(-1px);
}

.chat-send-btn:active:not(:disabled) {
  transform: translateY(0);
}

.chat-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-loading {
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-spinner {
  width: 16px;
  height: 16px;
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
  padding: 1px 5px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 4px;
  font-size: 10px;
}

.hint-divider {
  color: var(--border);
}

/* 响应式 */
@media (max-width: 640px) {
  .chat-messages {
    padding: 16px;
  }

  .chat-input-area {
    padding: 12px 16px;
  }

  .hero-title {
    font-size: 26px;
  }

  .hero-suggestions {
    flex-direction: column;
    align-items: center;
  }
}
</style>
