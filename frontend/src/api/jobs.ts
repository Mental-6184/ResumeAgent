import { http } from './http'
import type { JobCreateRequest, JobRecord, JobResumeDraft, JobReviewSummary, JobStatus, MatchReportResponse } from '../types'

export async function listJobs(params?: { status?: JobStatus; q?: string }): Promise<JobRecord[]> {
  const response = await http.get<JobRecord[]>('/jobs', { params })
  return response.data
}

export async function createJob(request: JobCreateRequest): Promise<JobRecord> {
  const response = await http.post<JobRecord>('/jobs', request)
  return response.data
}

export async function updateJob(id: string, request: JobCreateRequest): Promise<JobRecord> {
  const response = await http.put<JobRecord>(`/jobs/${id}`, request)
  return response.data
}

export async function deleteJob(id: string): Promise<void> {
  await http.delete(`/jobs/${id}`)
}

export async function updateJobStatus(id: string, status: JobStatus): Promise<JobRecord> {
  const response = await http.patch<JobRecord>(`/jobs/${id}/status`, { status })
  return response.data
}

export async function generateJobMatchReport(id: string): Promise<JobRecord> {
  const response = await http.post<JobRecord>(`/jobs/${id}/match-report`, undefined, { timeout: 120000 })
  return response.data
}

export async function getJobMatchReport(id: string): Promise<MatchReportResponse> {
  const response = await http.get<MatchReportResponse>(`/jobs/${id}/match-report`)
  return response.data
}

export async function generateJobResumeDraft(id: string): Promise<JobResumeDraft> {
  const response = await http.post<JobResumeDraft>(`/jobs/${id}/resume-draft`, undefined, { timeout: 120000 })
  return response.data
}

export async function saveJobResumeDraft(id: string, draft: JobResumeDraft): Promise<JobResumeDraft> {
  const response = await http.put<JobResumeDraft>(`/jobs/${id}/resume-draft`, draft)
  return response.data
}

export async function listJobReviews(id: string): Promise<JobReviewSummary[]> {
  const response = await http.get<JobReviewSummary[]>(`/jobs/${id}/reviews`)
  return response.data
}

export async function linkJobReview(id: string, reviewId: string): Promise<JobRecord> {
  const response = await http.post<JobRecord>(`/jobs/${id}/reviews`, { reviewId })
  return response.data
}
