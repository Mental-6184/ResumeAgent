import { onBeforeUnmount, ref, watch } from 'vue'
import type { ChatSession, SessionMessage } from '../types'

const STORAGE_KEY = 'resumeagent_sessions'
const MAX_SESSIONS_PER_MODULE = 30
const MAX_MESSAGES_PER_SESSION = 40
const SAVE_DEBOUNCE_MS = 300

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

function loadSessions(): ChatSession[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) return []
    const validSessions = parsed.filter((session): session is ChatSession => {
      return session && typeof session.id === 'string'
        && typeof session.module === 'string'
        && Array.isArray(session.messages)
    }).map(session => ({
      ...session,
      messages: session.messages.slice(-MAX_MESSAGES_PER_SESSION),
    }))
    const moduleCounts = new Map<string, number>()
    return validSessions
      .sort((a, b) => b.updatedAt - a.updatedAt)
      .filter(session => {
        const count = moduleCounts.get(session.module) ?? 0
        if (count >= MAX_SESSIONS_PER_MODULE) return false
        moduleCounts.set(session.module, count + 1)
        return true
      })
  } catch {
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // 浏览器禁用存储时保持内存模式运行。
    }
    return []
  }
}

let storageWarningShown = false

function saveSessions(sessions: ChatSession[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions))
  } catch {
    if (!storageWarningShown) {
      storageWarningShown = true
      console.warn('ResumeAgent 会话保存失败：浏览器存储空间不足。')
    }
  }
}

export function useChatStorage(module: ChatSession['module']) {
  const sessions = ref<ChatSession[]>(loadSessions())
  const currentSessionId = ref<string | null>(null)
  let saveTimer: ReturnType<typeof setTimeout> | null = null

  function scheduleSave() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => {
      saveTimer = null
      saveSessions(sessions.value)
    }, SAVE_DEBOUNCE_MS)
  }

  function flushSave() {
    if (saveTimer) {
      clearTimeout(saveTimer)
      saveTimer = null
    }
    saveSessions(sessions.value)
  }

  function trimModuleSessions() {
    const moduleSessions = sessions.value
      .filter(session => session.module === module)
      .sort((a, b) => b.updatedAt - a.updatedAt)
    const keep = new Set(moduleSessions.slice(0, MAX_SESSIONS_PER_MODULE).map(session => session.id))
    sessions.value = sessions.value.filter(session => session.module !== module || keep.has(session.id))
  }

  // Auto-persist on change
  watch(sessions, () => scheduleSave(), { deep: true })
  window.addEventListener('beforeunload', flushSave)

  function createSession(mode?: 'rag' | 'llm', jobId?: string, title = '新对话'): ChatSession {
    const session: ChatSession = {
      id: generateId(),
      title,
      module,
      mode,
      jobId,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      messages: [],
    }
    sessions.value.unshift(session)
    trimModuleSessions()
    currentSessionId.value = session.id
    return session
  }

  function getSession(id: string): ChatSession | undefined {
    return sessions.value.find((s) => s.id === id)
  }

  function currentSession(): ChatSession | undefined {
    return currentSessionId.value ? getSession(currentSessionId.value) : undefined
  }

  function addMessage(msg: Omit<SessionMessage, 'timestamp'>) {
    const session = currentSession()
    if (!session) return
    session.messages.push({ ...msg, timestamp: Date.now() })
    if (session.messages.length > MAX_MESSAGES_PER_SESSION) {
      session.messages.splice(0, session.messages.length - MAX_MESSAGES_PER_SESSION)
    }
    session.updatedAt = Date.now()
    // Auto-title from first user message
    if (session.title === '新对话' && msg.role === 'user') {
      session.title = msg.content.slice(0, 20) + (msg.content.length > 20 ? '...' : '')
    }
  }

  function deleteSession(id: string) {
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = sessions.value[0]?.id ?? null
    }
  }

  function switchSession(id: string) {
    currentSessionId.value = id
  }

  function clearSessions() {
    sessions.value = sessions.value.filter((s) => s.module !== module)
    currentSessionId.value = null
  }

  function moduleSessions() {
    return sessions.value
      .filter((s) => s.module === module)
      .sort((a, b) => b.updatedAt - a.updatedAt)
  }

  onBeforeUnmount(() => {
    flushSave()
    window.removeEventListener('beforeunload', flushSave)
  })

  return {
    sessions,
    currentSessionId,
    currentSession,
    createSession,
    getSession,
    addMessage,
    deleteSession,
    switchSession,
    clearSessions,
    moduleSessions,
  }
}
