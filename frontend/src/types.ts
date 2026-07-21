export interface ChatRequest {
  conversationId: string
  message: string
  mode?: 'rag' | 'llm'
}

export interface ChatResponse {
  conversationId: string
  reply: string
  citations: string[]
}

export interface ConversationMessage {
  role: string
  content: string
}

export interface IngestRequest {
  sourceType: string
  sourceName: string
  content: string
}

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

export interface SkillAnalysisRequest {
  // 无需输入，从知识库自动获取
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

export interface KnowledgeSearchItem {
  sourceName: string
  sourceType: string
  content: string
  score: number
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
