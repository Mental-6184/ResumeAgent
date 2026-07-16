import { http } from './http'
import type { IngestRequest, IngestResponse, KnowledgeSearchItem } from '../types'

export async function ingestKnowledge(request: IngestRequest) {
  const response = await http.post<IngestResponse>('/rag/ingest', request)
  return response.data
}

export async function searchKnowledge(query: string) {
  const response = await http.get<{ items: KnowledgeSearchItem[] }>('/rag/search', {
    params: { query }
  })
  return response.data.items
}

