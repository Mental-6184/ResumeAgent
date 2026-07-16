import { http } from './http'
import type { AuditEntryResponse } from '../types'

export async function getAuditLogs(limit = 20) {
  const response = await http.get<AuditEntryResponse[]>('/audit/logs', {
    params: { limit }
  })
  return response.data
}

