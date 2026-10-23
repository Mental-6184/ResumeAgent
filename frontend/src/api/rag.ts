import { http } from './http'
import type { IngestResponse, KnowledgeDocumentInfo } from '../types'

// ===== 知识库文档管理 =====

export async function listDocuments(): Promise<KnowledgeDocumentInfo[]> {
  const res = await http.get<KnowledgeDocumentInfo[]>('/rag/documents')
  return res.data
}

export async function saveDocument(request: {
  sourceName: string
  sourceType: string
  content: string
}): Promise<IngestResponse> {
  const res = await http.post<IngestResponse>('/rag/documents', request)
  return res.data
}

export async function deleteDocument(sourceName: string): Promise<{ success: boolean }> {
  const res = await http.delete<{ success: boolean }>(
    `/rag/documents/${encodeURIComponent(sourceName)}`
  )
  return res.data
}

export async function uploadDocument(file: File, sourceType: string): Promise<IngestResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('sourceType', sourceType)
  const res = await http.post<IngestResponse>('/rag/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
  return res.data
}
