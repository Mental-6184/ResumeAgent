import { http } from './http'
import type {
  ExportInterviewRequest,
  ExportResumeRequest,
} from '../types'

/**
 * 导出 API：简历优化版 / 面试问答集与复盘。
 * 服务端在 X-Export-Format 头返回实际格式（PDF 中文字体缺失时自动降级为 md）。
 */

async function download(url: string, payload: unknown, fallbackName: string): Promise<void> {
  const res = await http.post(url, payload, {
    responseType: 'blob',
    timeout: 60000,
  })
  const format = (res.headers['x-export-format'] as string) || 'md'
  const blob = new Blob([res.data], {
    type: (res.headers['content-type'] as string) || 'application/octet-stream',
  })
  const anchor = document.createElement('a')
  anchor.href = URL.createObjectURL(blob)
  anchor.download = `${fallbackName}.${format}`
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(anchor.href)
}

export async function exportResume(payload: ExportResumeRequest, fallbackName = '简历优化'): Promise<void> {
  await download('/export/resume', payload, fallbackName)
}

export async function exportInterview(payload: ExportInterviewRequest, fallbackName = '面试复盘'): Promise<void> {
  await download('/export/interview', payload, fallbackName)
}
