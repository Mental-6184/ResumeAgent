export interface IngestResponse {
  success: boolean
  message: string
  chunks: number
}

export interface JobAnalyzeRequest {
  jobTitle: string
  jobDescription: string
}

export interface JobAnalyzeResponse {
  jobTitle: string
  matchScore: number
  strengths: string[]
  gaps: string[]
  suggestions: string[]
}

export interface ProjectQueryRequest {
  query: string
  techStack?: string
  timeRange?: string
}

export interface ProjectInfo {
  name: string
  description: string
  techStack: string[]
  role: string
  highlights: string[]
}

export interface ProjectQueryResponse {
  query: string
  projects: ProjectInfo[]
  summary: string
}

export interface SkillAnalysisResponse {
  coreSkills: string[]
  technicalStrengths: string[]
  coverageAreas: string[]
  improvementSuggestions: string[]
  summary: string
}

export interface InterviewAnswerRequest {
  question: string
  jobTitle?: string
}

export interface InterviewAnswerResponse {
  question: string
  answer: string
  keyPoints: string[]
  tips: string[]
}

export interface KnowledgeDocumentInfo {
  sourceName: string
  sourceType: string
  chunks: number
  uploadedAt: number
  builtin: boolean
}

export interface AuditEntryResponse {
  timestamp: string
  category: string
  conversationId: string
  input: string
  output: string
  notes: string
}

export interface TechQuoteResponse {
  quote: string
  author: string
  category: string
}

// ===== 技术引航图 =====

export interface TechGuideModule {
  id: string
  name: string
  icon: string
  description: string
  tags: string[]
}

export interface TechGuideMessage {
  role: 'user' | 'assistant'
  content: string
}

// ===== 用户档案模板 =====

export interface ProfileForm {
  name: string
  phone: string
  email: string
  city: string
  school: string
  major: string
  degree: string
  graduationYear: string
  targetPosition: string
  expectedSalary: string
  skills: string
  projectExperience: string
  internshipExperience: string
  selfEvaluation: string
  awards: string
}

export interface ProfileSaveRequest {
  name: string
  form: ProfileForm
  resumeText: string
  resumeFileName: string
}

export interface ProfileTemplate {
  id: string
  name: string
  form: ProfileForm
  resumeText: string
  resumeFileName: string
  createdAt: number
  updatedAt: number
  active?: boolean
}

export interface ProfileListResponse {
  templates: ProfileTemplate[]
  activeId: string | null
}

export interface ProfileVersionItem {
  timestamp: number
  name: string
  summary: string
}

export interface ProfileUploadResponse {
  fileName: string
  extractedText: string
  charCount: number
}

// ===== 深度匹配报告 =====

export interface DimensionScore {
  name: string
  score: number
  comment: string
}

export interface RequirementMatch {
  requirement: string
  status: string
  evidence: string
  advice: string
}

export interface ResumeSuggestion {
  original: string
  suggestion: string
  reason: string
}

export interface MatchReportResponse {
  jobTitle: string
  overallScore: number
  dimensions: DimensionScore[]
  requirementMatches: RequirementMatch[]
  resumeSuggestions: ResumeSuggestion[]
  summary: string
  degraded: boolean
}

// ===== 模拟面试复盘 =====

export interface ReviewMessageItem {
  role: string
  content: string
}

export interface ReviewDimension {
  name: string
  score: number
  comment: string
}

export interface QuestionReview {
  question: string
  score: number
  comment: string
  improvement: string
}

export interface InterviewReviewRequest {
  messages: ReviewMessageItem[]
  jobTitle?: string
  sessionTitle?: string
}

export interface InterviewReviewResponse {
  id: string
  title: string
  createdAt: number
  overallScore: number
  dimensions: ReviewDimension[]
  highlights: string[]
  improvements: string[]
  perQuestion: QuestionReview[]
  summary: string
  degraded: boolean
}

export interface InterviewReviewSummary {
  id: string
  title: string
  createdAt: number
  overallScore: number
}

// ===== 导出 =====

export interface ExportResumeRequest {
  format: 'pdf' | 'md'
  templateId?: string | null
  matchReport?: MatchReportResponse | null
  resumeDraft?: JobResumeDraft | null
}

export interface ExportInterviewRequest {
  format: 'pdf' | 'md'
  reviewId?: string | null
  title?: string
  messages?: ReviewMessageItem[]
}

// ===== 求职岗位工作区 =====

export type JobStatus = 'SAVED' | 'PREPARING' | 'APPLIED' | 'WRITTEN_TEST' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'

export interface JobResumeProjectSuggestion {
  project: string
  original: string
  suggestion: string
  reason: string
}

export interface JobResumeDraft {
  summary: string
  skills: string[]
  projectSuggestions: JobResumeProjectSuggestion[]
  keywords: string[]
  edited: boolean
  updatedAt: number
}

export interface JobRecord {
  id: string
  company: string
  title: string
  location: string
  salaryRange: string
  url: string
  jobDescription: string
  deadline: number | null
  status: JobStatus
  notes: string
  profileTemplateId: string | null
  matchReport: MatchReportResponse | null
  resumeDraft: JobResumeDraft | null
  reviewIds: string[]
  createdAt: number
  updatedAt: number
}

export interface JobCreateRequest {
  company: string
  title: string
  location?: string
  salaryRange?: string
  url?: string
  jobDescription: string
  deadline?: number | null
  status?: JobStatus
  notes?: string
}

export interface JobReviewSummary {
  id: string
  title: string
  createdAt: number
  overallScore: number
}

// ===== 会话持久化 =====

export interface ChatSession {
  id: string
  title: string
  module: 'chat' | 'techguide' | 'interview'
  mode?: 'rag' | 'llm'
  jobId?: string
  createdAt: number
  updatedAt: number
  messages: SessionMessage[]
}

export interface SessionMessage {
  role: 'user' | 'assistant'
  content: string
  citations?: string[]
  timestamp: number
}
