import { http } from './http'
import type {
  JobAnalyzeRequest,
  JobAnalyzeResponse,
  ProjectQueryRequest,
  ProjectQueryResponse,
  SkillAnalysisResponse,
  InterviewAnswerRequest,
  InterviewAnswerResponse,
  TechQuoteResponse
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

