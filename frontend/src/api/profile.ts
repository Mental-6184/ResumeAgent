import { http } from './http'
import type {
  ProfileListResponse,
  ProfileSaveRequest,
  ProfileTemplate,
  ProfileUploadResponse,
  ProfileVersionItem,
} from '../types'

/** 档案模板 API：多模板管理、激活切换、简历文件上传解析 */

export async function listTemplates(): Promise<ProfileListResponse> {
  const res = await http.get<ProfileListResponse>('/profile/templates')
  return res.data
}

export async function createTemplate(payload: ProfileSaveRequest): Promise<ProfileTemplate> {
  const res = await http.post<ProfileTemplate>('/profile/templates', payload)
  return res.data
}

export async function updateTemplate(id: string, payload: ProfileSaveRequest): Promise<ProfileTemplate> {
  const res = await http.put<ProfileTemplate>(`/profile/templates/${id}`, payload)
  return res.data
}

export async function deleteTemplate(id: string): Promise<{ success: boolean; activeId: string }> {
  const res = await http.delete<{ success: boolean; activeId: string }>(`/profile/templates/${id}`)
  return res.data
}

export async function activateTemplate(id: string): Promise<ProfileListResponse> {
  const res = await http.post<ProfileListResponse>(`/profile/templates/${id}/activate`)
  return res.data
}

export async function getActiveProfile(): Promise<{ activeId: string; template: ProfileTemplate | null }> {
  const res = await http.get<{ activeId: string; template: ProfileTemplate | null }>('/profile/active')
  return res.data
}

export async function uploadResume(file: File): Promise<ProfileUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await http.post<ProfileUploadResponse>('/profile/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
  return res.data
}

// ===== 版本历史 =====

export async function listVersions(id: string): Promise<ProfileVersionItem[]> {
  const res = await http.get<ProfileVersionItem[]>(`/profile/templates/${id}/versions`)
  return res.data
}

export async function getVersion(id: string, timestamp: number): Promise<ProfileTemplate> {
  const res = await http.get<ProfileTemplate>(`/profile/templates/${id}/versions/${timestamp}`)
  return res.data
}

export async function rollbackVersion(id: string, timestamp: number): Promise<ProfileTemplate> {
  const res = await http.post<ProfileTemplate>(`/profile/templates/${id}/versions/${timestamp}/rollback`)
  return res.data
}
