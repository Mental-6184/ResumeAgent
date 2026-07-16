import { http } from './http'
import type { ChatRequest, ChatResponse, ConversationMessage } from '../types'

export async function postChat(request: ChatRequest) {
  const response = await http.post<ChatResponse>('/chat', request)
  return response.data
}

export async function getChatHistory(conversationId: string) {
  const response = await http.get<ConversationMessage[]>('/chat/history', {
    params: { conversationId }
  })
  return response.data
}

