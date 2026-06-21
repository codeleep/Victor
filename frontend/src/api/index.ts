import request from '@/utils/request'
import { instance } from '@/utils/request'
import type {
  UserVO,
  UserLoginRequest,
  UserRegisterRequest,
  UserUpdateRequest,
  PasswordChangeRequest,
  QuestionVO,
  QuestionRequest,
  QuestionQueryRequest,
  JobVO,
  JobRequest,
  JobQueryRequest,
  ResumeVO,
  ExperienceVO,
  ExperienceRequest,
  AgentVO,
  AgentRequest,
  ToolVO,
  AgentTeamVO,
  TeamRequest,
  AgentLlmConfigVO,
  AgentLlmConfigRequest,
  InterviewConfigVO,
  InterviewConfigRequest,
  RecallPreviewItem,
  InterviewSessionVO,
  InterviewTurnVO,
  InterviewReportVO,
  VoiceAsrConfig,
  VoiceTtsConfig,
  MetadataVO,
  MetadataRequest,
  MetadataQueryRequest,
  OpenApiKeyVO,
  OpenApiKeyRequest,
  Page
} from '@/types'

// ==================== Auth API ====================

export const authApi = {
  login(data: UserLoginRequest): Promise<UserVO> {
    return request.post<UserVO>('/auth/login', data)
  },
  register(data: UserRegisterRequest): Promise<UserVO> {
    return request.post<UserVO>('/auth/register', data)
  }
}

// ==================== User API ====================

export const userApi = {
  getCurrentUser(): Promise<UserVO> {
    return request.get<UserVO>('/users/me')
  },
  updateCurrentUser(data: UserUpdateRequest): Promise<UserVO> {
    return request.put<UserVO>('/users/me', data)
  },
  changePassword(data: PasswordChangeRequest): Promise<void> {
    return request.put<void>('/users/me/password', data)
  }
}

// ==================== Question API ====================

export const questionApi = {
  list(params?: QuestionQueryRequest): Promise<Page<QuestionVO>> {
    return request.get<Page<QuestionVO>>('/questions', { params })
  },
  getById(id: number): Promise<QuestionVO> {
    return request.get<QuestionVO>(`/questions/${id}`)
  },
  create(data: QuestionRequest): Promise<QuestionVO> {
    return request.post<QuestionVO>('/questions', data)
  },
  update(id: number, data: QuestionRequest): Promise<QuestionVO> {
    return request.put<QuestionVO>(`/questions/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/questions/${id}`)
  },
  approve(id: number): Promise<void> {
    return request.post<void>(`/questions/${id}/approve`)
  },
  reject(id: number): Promise<void> {
    return request.post<void>(`/questions/${id}/reject`)
  }
}

// ==================== Job API ====================

export const jobApi = {
  list(params?: JobQueryRequest): Promise<Page<JobVO>> {
    return request.get<Page<JobVO>>('/jobs', { params })
  },
  getById(id: number): Promise<JobVO> {
    return request.get<JobVO>(`/jobs/${id}`)
  },
  create(data: JobRequest): Promise<JobVO> {
    return request.post<JobVO>('/jobs', data)
  },
  update(id: number, data: JobRequest): Promise<JobVO> {
    return request.put<JobVO>(`/jobs/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/jobs/${id}`)
  },
  approve(id: number): Promise<void> {
    return request.post<void>(`/jobs/${id}/approve`)
  },
  reject(id: number): Promise<void> {
    return request.post<void>(`/jobs/${id}/reject`)
  }
}

// ==================== Resume API ====================

export const resumeApi = {
  list(): Promise<ResumeVO[]> {
    return request.get<ResumeVO[]>('/resumes')
  },
  getById(id: number): Promise<ResumeVO> {
    return request.get<ResumeVO>(`/resumes/${id}`)
  },
  upload(file: File, name: string): Promise<ResumeVO> {
    return request.upload<ResumeVO>('/resumes/upload', file, { name })
  },
  parse(id: number): Promise<void> {
    return request.post<void>(`/resumes/${id}/parse`)
  },
  update(id: number, data: { rawText: string }): Promise<void> {
    return request.put<void>(`/resumes/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/resumes/${id}`)
  },
  approve(id: number): Promise<void> {
    return request.post<void>(`/resumes/${id}/approve`)
  },
  reject(id: number): Promise<void> {
    return request.post<void>(`/resumes/${id}/reject`)
  }
}

// ==================== Experience API ====================

export const experienceApi = {
  list(type?: string): Promise<ExperienceVO[]> {
    return request.get<ExperienceVO[]>('/experiences', { params: { type } })
  },
  getById(id: number): Promise<ExperienceVO> {
    return request.get<ExperienceVO>(`/experiences/${id}`)
  },
  create(data: ExperienceRequest): Promise<ExperienceVO> {
    return request.post<ExperienceVO>('/experiences', data)
  },
  update(id: number, data: ExperienceRequest): Promise<ExperienceVO> {
    return request.put<ExperienceVO>(`/experiences/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/experiences/${id}`)
  },
  approve(id: number): Promise<void> {
    return request.post<void>(`/experiences/${id}/approve`)
  },
  reject(id: number): Promise<void> {
    return request.post<void>(`/experiences/${id}/reject`)
  }
}

// ==================== Agent API ====================

export const agentApi = {
  list(type?: string): Promise<AgentVO[]> {
    return request.get<AgentVO[]>('/agents', { params: { type } })
  },
  getById(id: number): Promise<AgentVO> {
    return request.get<AgentVO>(`/agents/${id}`)
  },
  create(data: AgentRequest): Promise<AgentVO> {
    return request.post<AgentVO>('/agents', data)
  },
  update(id: number, data: AgentRequest): Promise<AgentVO> {
    return request.put<AgentVO>(`/agents/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/agents/${id}`)
  },
  listTools(): Promise<ToolVO[]> {
    return request.get<ToolVO[]>('/agents/tools')
  }
}

// ==================== Agent Team API ====================

export const agentTeamApi = {
  list(): Promise<AgentTeamVO[]> {
    return request.get<AgentTeamVO[]>('/agent-teams')
  },
  getById(id: number): Promise<AgentTeamVO> {
    return request.get<AgentTeamVO>(`/agent-teams/${id}`)
  },
  create(data: TeamRequest): Promise<AgentTeamVO> {
    return request.post<AgentTeamVO>('/agent-teams', data)
  },
  update(id: number, data: TeamRequest): Promise<AgentTeamVO> {
    return request.put<AgentTeamVO>(`/agent-teams/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/agent-teams/${id}`)
  }
}

// ==================== LLM Config API ====================

export const agentLlmConfigApi = {
  list(): Promise<AgentLlmConfigVO[]> {
    return request.get<AgentLlmConfigVO[]>('/llm-configs')
  },
  getById(id: number): Promise<AgentLlmConfigVO> {
    return request.get<AgentLlmConfigVO>(`/llm-configs/${id}`)
  },
  create(data: AgentLlmConfigRequest): Promise<AgentLlmConfigVO> {
    return request.post<AgentLlmConfigVO>('/llm-configs', data)
  },
  update(id: number, data: AgentLlmConfigRequest): Promise<AgentLlmConfigVO> {
    return request.put<AgentLlmConfigVO>(`/llm-configs/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/llm-configs/${id}`)
  },
  setDefault(id: number): Promise<void> {
    return request.put<void>(`/llm-configs/${id}/default`)
  },
  testConnection(id: number): Promise<void> {
    return request.post<void>(`/llm-configs/${id}/test`)
  }
}
// ==================== Interview Config API ====================

export const interviewConfigApi = {
  list(): Promise<InterviewConfigVO[]> {
    return request.get<InterviewConfigVO[]>('/interview-configs')
  },
  getById(id: number): Promise<InterviewConfigVO> {
    return request.get<InterviewConfigVO>(`/interview-configs/${id}`)
  },
  create(data: InterviewConfigRequest): Promise<number> {
    return request.post<number>('/interview-configs', data)
  },
  update(id: number, data: InterviewConfigRequest): Promise<void> {
    return request.put<void>(`/interview-configs/${id}`, data)
  },
  recallPreview(data: InterviewConfigRequest): Promise<RecallPreviewItem[]> {
    return request.post<RecallPreviewItem[]>('/interview-configs/recall-preview', data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/interview-configs/${id}`)
  },
  publish(id: number): Promise<void> {
    return request.post<void>(`/interview-configs/${id}/publish`)
  },
  archive(id: number): Promise<void> {
    return request.post<void>(`/interview-configs/${id}/archive`)
  }
}

// ==================== Interview Session API ====================

export const interviewSessionApi = {
  list(): Promise<InterviewSessionVO[]> {
    return request.get<InterviewSessionVO[]>('/interview-sessions')
  },
  getById(id: number): Promise<InterviewSessionVO> {
    return request.get<InterviewSessionVO>(`/interview-sessions/${id}`)
  },
  create(configId: number): Promise<number> {
    return request.post<number>(`/interview-sessions?configId=${configId}`)
  },
  start(id: number): Promise<void> {
    return request.post<void>(`/interview-sessions/${id}/start`)
  },
  submitAnswer(id: number, answer: string): Promise<string> {
    return request.post<string>(`/interview-sessions/${id}/answer`, answer, {
      headers: { 'Content-Type': 'text/plain' }
    })
  },
  skipQuestion(id: number): Promise<void> {
    return request.post<void>(`/interview-sessions/${id}/skip`)
  },
  pause(id: number): Promise<void> {
    return request.post<void>(`/interview-sessions/${id}/pause`)
  },
  resume(id: number): Promise<void> {
    return request.post<void>(`/interview-sessions/${id}/resume`)
  },
  cancel(id: number): Promise<void> {
    return request.post<void>(`/interview-sessions/${id}/cancel`)
  },
  complete(id: number): Promise<void> {
    return request.post<void>(`/interview-sessions/${id}/complete`)
  },
  getNextQuestion(id: number): Promise<string> {
    return request.get<string>(`/interview-sessions/${id}/next-question`)
  },
  getHint(id: number, currentQuestion?: string): Promise<string> {
    const params = currentQuestion ? `?currentQuestion=${encodeURIComponent(currentQuestion)}` : ''
    return request.get<string>(`/interview-sessions/${id}/hint${params}`)
  },
  getSummary(id: number): Promise<string> {
    return request.get<string>(`/interview-sessions/${id}/summary`)
  },
  getHistory(id: number): Promise<InterviewTurnVO[]> {
    return request.get<InterviewTurnVO[]>(`/interview-sessions/${id}/history`)
  },
  getReport(id: number): Promise<InterviewReportVO> {
    return request.get<InterviewReportVO>(`/interview-sessions/${id}/report`)
  },
  async streamNextQuestion(id: number, onMessage: (chunk: string) => void): Promise<void> {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/v1/interview-sessions/${id}/next-question/stream`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!response.ok) throw new Error('Failed to stream question')
    const reader = response.body?.getReader()
    if (!reader) return
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      onMessage(decoder.decode(value, { stream: true }))
    }
  },
  async streamSubmitAnswer(id: number, answer: string, onMessage: (chunk: string) => void): Promise<void> {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/v1/interview-sessions/${id}/answer/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'text/plain',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: answer
    })
    if (!response.ok) throw new Error('Failed to stream answer')
    const reader = response.body?.getReader()
    if (!reader) return
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      onMessage(decoder.decode(value, { stream: true }))
    }
  },
  async streamGetHint(id: number, currentQuestion: string, onMessage: (chunk: string) => void): Promise<void> {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/v1/interview-sessions/${id}/hint/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'text/plain',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: currentQuestion
    })
    if (!response.ok) throw new Error('Failed to stream hint')
    const reader = response.body?.getReader()
    if (!reader) return
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      onMessage(decoder.decode(value, { stream: true }))
    }
  },
  async streamGetSummary(id: number, onMessage: (chunk: string) => void): Promise<void> {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/v1/interview-sessions/${id}/summary/stream`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!response.ok) throw new Error('Failed to stream summary')
    const reader = response.body?.getReader()
    if (!reader) return
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      onMessage(decoder.decode(value, { stream: true }))
    }
  }
}

// ==================== Report API ====================

export const reportApi = {
  generate(sessionId: number): Promise<number> {
    return request.post<number>(`/reports/generate/${sessionId}`)
  },
  getBySessionId(sessionId: number): Promise<InterviewReportVO> {
    return request.get<InterviewReportVO>(`/reports/session/${sessionId}`)
  },
  getById(id: number): Promise<InterviewReportVO> {
    return request.get<InterviewReportVO>(`/reports/${id}`)
  },
  exportPdf(sessionId: number): Promise<Blob> {
    return request.get(`/reports/export/pdf/${sessionId}`, {
      responseType: 'blob'
    }) as unknown as Promise<Blob>
  },
  exportMarkdown(sessionId: number): Promise<string> {
    return request.get(`/reports/export/markdown/${sessionId}`, {
      responseType: 'text'
    }) as unknown as Promise<string>
  }
}

// ==================== Voice API ====================

export const voiceApi = {
  listAsrConfigs(): Promise<VoiceAsrConfig[]> {
    return request.get<VoiceAsrConfig[]>('/voice/asr-configs')
  },
  getAsrConfig(id: number): Promise<VoiceAsrConfig> {
    return request.get<VoiceAsrConfig>(`/voice/asr-configs/${id}`)
  },
  createAsrConfig(data: VoiceAsrConfig): Promise<number> {
    return request.post<number>('/voice/asr-configs', data)
  },
  updateAsrConfig(id: number, data: VoiceAsrConfig): Promise<void> {
    return request.put<void>(`/voice/asr-configs/${id}`, data)
  },
  deleteAsrConfig(id: number): Promise<void> {
    return request.delete<void>(`/voice/asr-configs/${id}`)
  },
  setDefaultAsr(id: number): Promise<void> {
    return request.post<void>(`/voice/asr-configs/${id}/set-default`)
  },
  testAsr(id: number): Promise<string> {
    return request.post<string>(`/voice/asr-configs/${id}/test`)
  },
  listTtsConfigs(): Promise<VoiceTtsConfig[]> {
    return request.get<VoiceTtsConfig[]>('/voice/tts-configs')
  },
  getTtsConfig(id: number): Promise<VoiceTtsConfig> {
    return request.get<VoiceTtsConfig>(`/voice/tts-configs/${id}`)
  },
  createTtsConfig(data: VoiceTtsConfig): Promise<number> {
    return request.post<number>('/voice/tts-configs', data)
  },
  updateTtsConfig(id: number, data: VoiceTtsConfig): Promise<void> {
    return request.put<void>(`/voice/tts-configs/${id}`, data)
  },
  deleteTtsConfig(id: number): Promise<void> {
    return request.delete<void>(`/voice/tts-configs/${id}`)
  },
  setDefaultTts(id: number): Promise<void> {
    return request.post<void>(`/voice/tts-configs/${id}/set-default`)
  },
  testTts(id: number): Promise<Blob> {
    return instance.post(`/voice/tts-configs/${id}/test`, undefined, {
      responseType: 'blob'
    }).then(res => res.data)
  },
  asr(file: File, configId?: number): Promise<string> {
    return request.upload<string>('/voice/asr', file, configId ? { configId } : undefined)
  },
  tts(text: string, configId?: number): Promise<Blob> {
    const params = configId ? `?configId=${configId}` : ''
    return request.post(`/voice/tts${params}`, text, {
      headers: { 'Content-Type': 'text/plain' },
      responseType: 'blob'
    }) as unknown as Promise<Blob>
  }
}

// ==================== API Key API ====================

export const apiKeyApi = {
  list(): Promise<OpenApiKeyVO[]> {
    return request.get<OpenApiKeyVO[]>('/api-keys')
  },
  getById(id: number): Promise<OpenApiKeyVO> {
    return request.get<OpenApiKeyVO>(`/api-keys/${id}`)
  },
  create(data: OpenApiKeyRequest): Promise<OpenApiKeyVO> {
    return request.post<OpenApiKeyVO>('/api-keys', data)
  },
  update(id: number, data: OpenApiKeyRequest): Promise<OpenApiKeyVO> {
    return request.put<OpenApiKeyVO>(`/api-keys/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/api-keys/${id}`)
  },
  validate(apiKey: string): Promise<boolean> {
    return request.get<boolean>('/api-keys/validate', { params: { apiKey } })
  }
}

// ==================== System Init API ====================

export const systemApi = {
  initStatus(): Promise<boolean> {
    return request.get<boolean>('/system/init/status')
  },
  init(): Promise<Record<string, number>> {
    return request.post<Record<string, number>>('/system/init')
  }
}

// ==================== Metadata API ====================

export const metadataApi = {
  list(category?: string): Promise<MetadataVO[]> {
    return request.get<MetadataVO[]>('/metadata', { params: { category } })
  },
  pageList(params: MetadataQueryRequest): Promise<Page<MetadataVO>> {
    return request.get<Page<MetadataVO>>('/metadata/page', { params })
  },
  getCategories(): Promise<string[]> {
    return request.get<string[]>('/metadata/categories')
  },
  getById(id: number): Promise<MetadataVO> {
    return request.get<MetadataVO>(`/metadata/${id}`)
  },
  create(data: MetadataRequest): Promise<MetadataVO> {
    return request.post<MetadataVO>('/metadata', data)
  },
  update(id: number, data: MetadataRequest): Promise<MetadataVO> {
    return request.put<MetadataVO>(`/metadata/${id}`, data)
  },
  delete(id: number): Promise<void> {
    return request.delete<void>(`/metadata/${id}`)
  }
}


