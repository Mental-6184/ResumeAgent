import { ref, computed } from 'vue'
import type { ChatSession } from '../types'
import { useChatStorage } from './useChatStorage'

/**
 * 多会话管理 composable
 * 管理当前活跃会话、会话列表、切换/创建/删除操作
 */
export function useSessionManager(module: 'chat' | 'techguide' | 'interview') {
  const storage = useChatStorage(module)
  const activeSessionId = ref<string>('')
  const showSessionSidebar = ref(false)

  // 当前活跃会话
  const activeSession = computed(() => {
    return storage.sessions.value.find(s => s.id === activeSessionId.value) || null
  })

  // 会话列表（按更新时间倒序）
  const sessionList = computed(() =>
    storage.sessions.value
      .filter(s => s.module === module)
      .sort((a, b) => b.updatedAt - a.updatedAt)
  )

  // 切换到指定会话
  function switchSession(sessionId: string) {
    activeSessionId.value = sessionId
    showSessionSidebar.value = false
  }

  // 创建新会话
  function createNewSession(mode?: 'rag' | 'llm', jobId?: string, title?: string): ChatSession {
    const session = storage.createSession(mode, jobId, title)
    activeSessionId.value = session.id
    return session
  }

  // 删除会话
  function deleteSession(sessionId: string) {
    storage.deleteSession(sessionId)
    // 如果删除的是当前活跃会话，切换到最新的会话
    if (activeSessionId.value === sessionId) {
      if (sessionList.value.length > 0) {
        activeSessionId.value = sessionList.value[0].id
      } else {
        createNewSession()
      }
    }
  }

  // 清空所有会话
  function clearAllSessions() {
    storage.clearSessions()
    createNewSession()
  }

  // 初始化：加载最近会话或创建新会话
  function initSession(mode?: 'rag' | 'llm') {
    const list = sessionList.value
    if (list.length > 0) {
      activeSessionId.value = list[0].id
    } else {
      createNewSession(mode)
    }
  }

  // 切换会话侧边栏显示
  function toggleSessionSidebar() {
    showSessionSidebar.value = !showSessionSidebar.value
  }

  return {
    activeSessionId,
    activeSession,
    sessionList,
    showSessionSidebar,
    storage,
    switchSession,
    createNewSession,
    deleteSession,
    clearAllSessions,
    initSession,
    toggleSessionSidebar
  }
}
