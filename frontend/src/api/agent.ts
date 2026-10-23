import { http } from './http'
import type {
  JobAnalyzeRequest,
  JobAnalyzeResponse,
  ProjectQueryRequest,
  ProjectQueryResponse,
  SkillAnalysisResponse,
  InterviewAnswerRequest,
  InterviewAnswerResponse,
  TechQuoteResponse,
  MatchReportResponse,
  InterviewReviewRequest,
  InterviewReviewResponse,
  InterviewReviewSummary
} from '../types'

export async function analyzeJob(request: JobAnalyzeRequest) {
  const response = await http.post<JobAnalyzeResponse>('/agent/analyze', request)
  return response.data
}

export async function queryProjects(request: ProjectQueryRequest) {
  const response = await http.post<ProjectQueryResponse>('/agent/project-query', request)
  return response.data
}

export async function analyzeSkills() {
  const response = await http.post<SkillAnalysisResponse>('/agent/skill-analysis', {})
  return response.data
}

export async function generateInterviewAnswer(request: InterviewAnswerRequest) {
  const response = await http.post<InterviewAnswerResponse>('/agent/interview-answer', request)
  return response.data
}

export async function getTechQuote() {
  const response = await http.get<TechQuoteResponse>('/agent/tech-quote')
  return response.data
}

// ===== 深度匹配报告 =====

export async function getMatchReport(request: JobAnalyzeRequest): Promise<MatchReportResponse> {
  const res = await http.post<MatchReportResponse>('/agent/match-report', request, { timeout: 120000 })
  return res.data
}

// ===== 模拟面试复盘 =====

export async function createReview(request: InterviewReviewRequest): Promise<InterviewReviewResponse> {
  const res = await http.post<InterviewReviewResponse>('/agent/interview-reviews', request, { timeout: 120000 })
  return res.data
}

export async function listReviews(): Promise<InterviewReviewSummary[]> {
  const res = await http.get<InterviewReviewSummary[]>('/agent/interview-reviews')
  return res.data
}

export async function getReview(id: string): Promise<InterviewReviewResponse> {
  const res = await http.get<InterviewReviewResponse>(`/agent/interview-reviews/${id}`)
  return res.data
}

export async function deleteReview(id: string): Promise<{ success: boolean }> {
  const res = await http.delete<{ success: boolean }>(`/agent/interview-reviews/${id}`)
  return res.data
}

