// API 响应结构
export interface Result<T> {
  code: number
  message: string
  data: T
}

// 分页结果
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// ==================== 枚举类型 ====================

export type UserStatus = 'ACTIVE' | 'LOCKED' | 'DELETED'

export type QuestionType = 'TECHNICAL' | 'BEHAVIORAL' | 'SHORT_ANSWER' | 'MULTIPLE_CHOICE' | 'CODING'

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export type QuestionSource = 'SYSTEM' | 'USER' | 'OPEN_API'

export type ExperienceType = 'PROJECT' | 'WORK' | 'EDUCATION' | 'OTHER'

export type AgentType = 'INTERVIEW' | 'EVALUATION' | 'SEARCH'

export type TeamExecutionMode = 'PARALLEL' | 'SEQUENTIAL'

export type InterviewMode = 'VOICE' | 'TEXT'

export type InterviewConfigStatus = 'DRAFT' | 'GENERATING' | 'GENERATE_FAILED' | 'READY' | 'ARCHIVED'

export type SessionStatus = 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'ABANDONED'

export type InterviewReportStatus = 'PENDING' | 'EVALUATING' | 'COMPLETED' | 'FAILED'

export type RecallStrategy = 'VECTOR' | 'KEYWORD' | 'HYBRID'

export type LlmProtocol = 'OPENAI' | 'CLAUDE' | 'QWEN' | 'VOLCENGINE'

export type ModelType = 'INFERENCE' | 'EMBEDDING'

export type ResumeStatus = 'PENDING' | 'PARSED' | 'EMBEDDED'

export type Speaker = 'AI' | 'USER' | 'CANDIDATE' | 'INTERVIEWER'

export type VoiceServiceProvider = 'ALIYUN' | 'TENCENT' | 'QWEN' | 'VOLCENGINE' | 'AZURE' | 'OPENAI'

// ==================== 用户相关 ====================

export interface UserVO {
  id: number
  username: string
  email: string
  nickname?: string
  avatar?: string
  status: UserStatus
  createdAt: string
  updatedAt: string
  token?: string
}

export interface UserLoginRequest {
  username: string
  password: string
}

export interface UserRegisterRequest {
  username: string
  password: string
  email: string
  nickname?: string
}

export interface UserUpdateRequest {
  nickname?: string
  avatar?: string
  email?: string
}

export interface PasswordChangeRequest {
  oldPassword: string
  newPassword: string
}

// ==================== 题目相关 ====================

export interface QuestionVO {
  id: number
  title: string
  description?: string
  type: QuestionType
  tags: string[]
  difficulty: Difficulty
  referenceAnswer?: string
  sourceDocumentId?: number
  source?: QuestionSource
  ingestStatus?: string
  createdAt: string
  updatedAt: string
}

export interface QuestionRequest {
  title: string
  description?: string
  type: QuestionType
  tags?: string[]
  difficulty: Difficulty
  referenceAnswer?: string
  sourceDocumentId?: number
}

export interface QuestionQueryRequest {
  type?: QuestionType
  difficulty?: Difficulty
  tag?: string
  page?: number
  size?: number
}

// ==================== 岗位相关 ====================

export interface JobVO {
  id: number
  name: string
  description?: string
  requiredSkills?: string[]
  experienceYears?: number
  education?: string
  salaryRange?: string
  domains?: string[]
  ingestStatus?: string
  createdAt: string
  updatedAt: string
}

export interface JobRequest {
  name: string
  description?: string
  requiredSkills?: string[]
  experienceYears?: number
  education?: string
  salaryRange?: string
  domains?: string[]
}

export interface JobQueryRequest {
  page?: number
  size?: number
}

// ==================== 简历相关 ====================

export interface ResumeVO {
  id: number
  name: string
  fileName?: string
  filePath?: string
  rawText?: string
  status: ResumeStatus
  ingestStatus?: string
  embeddedAt?: string
  createdAt: string
  updatedAt: string
}

// ==================== 经历相关 ====================

export interface ExperienceVO {
  id: number
  type: ExperienceType
  title: string
  startDate?: string
  endDate?: string
  description?: string
  skills?: string[]
  attachments?: string[]
  ingestStatus?: string
  createdAt: string
  updatedAt: string
}

export interface ExperienceRequest {
  type: ExperienceType
  title: string
  startDate?: string
  endDate?: string
  description?: string
  skills?: string[]
  attachments?: string[]
}

// ==================== Agent 可用工具 ====================

export interface ToolVO {
  name: string
  description: string
}


// ==================== Agent 相关 ====================

export interface AgentVO {
  id: number
  key?: string
  name: string
  role?: string
  systemPrompt?: string
  llmConfigId?: number
  llmConfigName?: string
  availableTools?: string[]
  type?: AgentType
  isSystem?: boolean
  createdAt: string
  updatedAt: string
}

export interface AgentRequest {
  name: string
  role?: string
  systemPrompt?: string
  llmConfigId?: number
  availableTools?: string[]
  type?: AgentType
}

// ==================== Agent 团队相关 ====================

export interface TeamMemberDTO {
  agentId: number
  role?: string
  priority?: number
}

export interface AgentTeamVO {
  id: number
  key?: string
  name: string
  description?: string
  mainAgentId?: number
  mainAgentName?: string
  members: Record<string, unknown>[]
  executionMode?: TeamExecutionMode
  isSystem?: boolean
  createdAt: string
}

export interface TeamRequest {
  name: string
  description?: string
  mainAgentId?: number
  members?: TeamMemberDTO[]
  executionMode?: TeamExecutionMode
}

// ==================== Agent LLM配置相关 ====================

export interface AgentLlmConfigVO {
  id: number
  name: string
  description?: string
  provider?: string
  apiEndpoint: string
  authParams?: Record<string, unknown>
  protocol: LlmProtocol
  modelName: string
  modelType: ModelType
  temperature?: number
  maxTokens?: number
  extraParams?: Record<string, unknown>
  isEnabled?: boolean
  isDefault?: boolean
  createdAt: string
  updatedAt: string
}

export interface AgentLlmConfigRequest {
  name: string
  description?: string
  provider?: string
  apiEndpoint: string
  authParams?: Record<string, unknown>
  protocol: LlmProtocol
  modelName: string
  modelType: ModelType
  temperature?: number
  maxTokens?: number
  extraParams?: Record<string, unknown>
  isEnabled?: boolean
  isDefault?: boolean
}

// ==================== 面试配置相关 ====================

export interface TeamAssignment {
  role: string
  teamId: number
  teamName?: string
}

export interface InterviewConfigVO {
  id: number
  name: string
  mode: InterviewMode
  jobId?: number
  jobName?: string
  resumeId?: number
  resumeName?: string
  rounds?: Record<string, unknown>[]
  difficultyConfig?: Record<string, unknown>
  durationMinutes?: number
  hintEnabled?: boolean
  teamConfig?: TeamAssignment[]
  agentModelMapping?: Record<string, unknown>
  recallStrategy?: RecallStrategy
  maxRecallCount?: number
  recallItems?: Record<string, unknown>[]
  status: InterviewConfigStatus
  generateError?: string
  createdAt: string
  updatedAt: string
}

export interface InterviewConfigRequest {
  name: string
  mode: InterviewMode
  jobId?: number
  resumeId?: number
  rounds?: Record<string, unknown>[]
  difficultyConfig?: Record<string, unknown>
  durationMinutes?: number
  hintEnabled?: boolean
  teamConfig?: string[]
  agentModelMapping?: Record<string, unknown>
  recallStrategy?: RecallStrategy
  maxRecallCount?: number
  recallItems?: Record<string, unknown>[]
}

// ==================== 面试会话相关 ====================

export interface RecallPreviewItem {
  id: number
  type: 'QUESTION' | 'EXPERIENCE'
  sourceType?: string
  sourceId?: number
  source_type?: string
  source_id?: number
  title: string
  recallMethod?: string
  recallScore?: number
  recall_method?: string
  recall_score?: number
  sortOrder?: number
  sort_order?: number
  reason?: string
}

export interface InterviewSessionVO {
  id: number
  configId: number
  configName?: string
  status: SessionStatus
  currentQuestionId?: number
  startedAt?: string
  pausedAt?: string
  completedAt?: string
  createdAt: string
}

export interface InterviewTurnVO {
  id: number
  sessionId: number
  questionId?: number
  turnIndex?: number
  attemptNo?: number
  speaker: Speaker
  isFollowup?: boolean
  content: string
  attachments?: InterviewAttachment[]
  isHint?: boolean
  createdAt: string
}

export interface InterviewAttachment {
  type: 'EXCALIDRAW' | 'CODE' | string
  format?: string
  language?: string
  data?: unknown
}

// ==================== 报告相关 ====================

export interface InterviewReportVO {
  id: number
  sessionId: number
  userId?: number
  status: InterviewReportStatus
  overallScore?: number
  dimensionScores?: Record<string, unknown>
  perQuestionEvaluation?: Record<string, unknown>[]
  summary?: string
  strengths?: string
  weaknesses?: string
  suggestions?: string
  evaluationError?: string
  evaluationRetryCount?: number
  generatedAt?: string
  createdAt: string
}

// ==================== 语音服务相关 ====================

export interface VoiceAsrConfig {
  id?: number
  name: string
  description?: string
  provider: VoiceServiceProvider
  apiEndpoint?: string
  authParams?: Record<string, unknown>
  language?: string
  extraParams?: Record<string, unknown>
  isEnabled?: boolean
  isDefault?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface VoiceTtsConfig {
  id?: number
  name: string
  description?: string
  provider: VoiceServiceProvider
  apiEndpoint?: string
  authParams?: Record<string, unknown>
  voiceName?: string
  extraParams?: Record<string, unknown>
  isEnabled?: boolean
  isDefault?: boolean
  createdAt?: string
  updatedAt?: string
}

// ==================== API Key 相关 ====================

export type OpenApiKeyStatus = 'ENABLED' | 'DISABLED'

export interface OpenApiKeyVO {
  id: number
  name: string
  description?: string
  apiKey: string
  scopes?: string[]
  defaultIngestStatus?: string
  status: OpenApiKeyStatus
  expiresAt?: string
  lastUsedAt?: string
  createdAt: string
  updatedAt: string
}

export interface OpenApiKeyRequest {
  name: string
  description?: string
  apiKey: string
  scopes?: string[]
  defaultIngestStatus?: string
  status?: OpenApiKeyStatus
  expiresAt?: string
}

// ==================== 元数据相关 ====================

export interface MetadataVO {
  id: number
  category: string
  code: string
  name: string
  description?: string
  extraData?: unknown
  sortOrder?: number
  isActive?: boolean
}

export interface MetadataRequest {
  category: string
  code: string
  name: string
  description?: string
  extraData?: unknown
  sortOrder?: number
  isActive?: boolean
}

export interface MetadataQueryRequest {
  category?: string
  code?: string
  name?: string
  isActive?: boolean
  page?: number
  size?: number
}

// ==================== WebSocket 消息类型 ====================

// --- Interview 消息 ---

export interface InterviewStartMessage {
  type: 'interview.start'
  interviewSessionId: number
}

export interface InterviewReconnectMessage {
  type: 'interview.reconnect'
  interviewSessionId: number
}

export interface InterviewStopMessage {
  type: 'interview.stop'
}

export interface InterviewStreamBeginMessage {
  type: 'interview.stream_begin'
}

export interface InterviewStreamChunkMessage {
  type: 'interview.stream_chunk'
  text: string
  attachments?: InterviewAttachment[]
}

export interface InterviewStreamEndMessage {
  type: 'interview.stream_end'
}

export interface InterviewInterruptMessage {
  type: 'interview.interrupt'
  interruptType: string
}

export interface InterviewStatusMessage {
  type: 'interview.status'
  state: 'connected' | 'listening' | string
}

export interface InterviewReconnectedMessage {
  type: 'interview.reconnected'
  interviewSessionId: number
  historyTurns: number
}

export interface InterviewErrorMessage {
  type: 'interview.error'
  message: string
}

export type InterviewClientMessage =
  | InterviewStartMessage
  | InterviewReconnectMessage
  | InterviewStopMessage
  | InterviewStreamBeginMessage
  | InterviewStreamChunkMessage
  | InterviewStreamEndMessage
  | InterviewInterruptMessage

export type InterviewServerMessage =
  | InterviewStatusMessage
  | InterviewReconnectedMessage
  | InterviewStreamBeginMessage
  | InterviewStreamChunkMessage
  | InterviewStreamEndMessage
  | InterviewErrorMessage

// --- ASR 消息 ---

export interface AsrStreamBeginMessage {
  type: 'asr.stream_begin'
}

export interface AsrStreamChunkMessage {
  type: 'asr.stream_chunk'
  text: string
}

export interface AsrStreamEndMessage {
  type: 'asr.stream_end'
  text: string
}

export interface AsrInterruptMessage {
  type: 'asr.interrupt'
}

export type AsrClientMessage = AsrStreamBeginMessage | AsrStreamEndMessage | AsrInterruptMessage

export type AsrServerMessage = AsrStreamBeginMessage | AsrStreamChunkMessage | AsrStreamEndMessage

// --- TTS 消息 ---

export interface TtsStreamBeginMessage {
  type: 'tts.stream_begin'
}

export interface TtsStreamChunkMessage {
  type: 'tts.stream_chunk'
  text: string
}

export interface TtsStreamEndMessage {
  type: 'tts.stream_end'
}

export interface TtsInterruptMessage {
  type: 'tts.interrupt'
}

export type TtsClientMessage = TtsStreamBeginMessage | TtsStreamChunkMessage | TtsStreamEndMessage | TtsInterruptMessage

export type TtsServerMessage = TtsStreamBeginMessage | TtsStreamEndMessage
