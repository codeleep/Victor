import { useEffect, useRef, useState, useCallback, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Button, Input, Select, App, Drawer, Tabs, Tooltip } from 'antd'
import {
  ArrowLeftOutlined, SendOutlined, BulbOutlined, AudioOutlined, StopOutlined,
  EditOutlined, CodeOutlined, FileTextOutlined, LoadingOutlined, ReloadOutlined
} from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import { Excalidraw } from '@excalidraw/excalidraw'
import Editor from '@monaco-editor/react'
import { interviewSessionApi, reportApi } from '@/api'
import { createInterviewWs, createAsrWs, createTtsWs, WsConnection } from '@/utils/websocket'
import { AudioRecorder, AudioPlayer } from '@/utils/audio'
import type {
  InterviewServerMessage, InterviewStreamChunkMessage, InterviewReconnectedMessage,
  InterviewErrorMessage, InterviewStatusMessage, InterviewSessionVO,
  InterviewTurnVO, SessionStatus,
  AsrServerMessage, AsrStreamChunkMessage, AsrStreamEndMessage, TtsServerMessage,
  InterviewAttachment
} from '@/types'
import './InterviewRoom.scss'
import { TaskBlock, type ToolEventItem } from './components/TaskBlock'

interface ThinkingBlock { kind: 'thinking'; text: string; agentName?: string }
interface ToolBlock { kind: 'tool'; tool: ToolEventItem }
interface AnswerBlock { kind: 'answer'; text: string }
type TurnBlock = ThinkingBlock | ToolBlock | AnswerBlock

interface ChatMessage {
  id: number
  sessionId: number
  speaker: 'AI' | 'USER' | 'INTERVIEWER' | 'CANDIDATE'
  content: string
  attachments?: InterviewAttachment[]
  isHint?: boolean
  createdAt: string
  turnBlocks?: TurnBlock[]
  reasoning?: string
  toolEvents?: ToolEventItem[]
}

// 渲染 AI 回合的有序内容块序列(参考 gemini-cli 布局): 思考边框气泡 + 工具时间线 + 回答 Markdown。
// 历史回放: 折叠的推理过程区(thinking文本 + 工具时间线), 默认折叠, 点击展开
// 将历史回放的 reasoning(思考+工具合并文本) + toolEvents + content 还原为 TurnBlock 序列,
// 使历史与实时统一用 TurnBlocksView 渲染。顺序: 思考块 → 工具块 → 回答块。
function reasoningToBlocks(reasoning: string | undefined, tools: ToolEventItem[] | undefined, content: string): TurnBlock[] {
  const blocks: TurnBlock[] = []
  if (reasoning) {
    // 后端 buildReasoning 会在思考文本后追加 "### 工具调用", 取该标题之前的纯思考部分
    const marker = reasoning.indexOf('### 工具调用')
    const thinking = (marker >= 0 ? reasoning.slice(0, marker) : reasoning).trim()
    if (thinking) blocks.push({ kind: 'thinking', text: thinking })
  }
  if (tools && tools.length > 0) {
    for (const t of tools) {
      blocks.push({ kind: 'tool', tool: t })
    }
  }
  if (content && content.trim()) {
    blocks.push({ kind: 'answer', text: content })
  }
  return blocks
}

function TurnBlocksView({ blocks, streaming }: { blocks: TurnBlock[]; streaming?: boolean }) {
  const [toolExpanded, setToolExpanded] = useState<Record<number | string, boolean>>({})
  const toggleTool = (id: number | string) => setToolExpanded(prev => ({ ...prev, [id]: !prev[id] }))
  if (blocks.length === 0) return null

  // 拆分: 过程块(思考 + 工具) 与 回答块; 回答始终可见, 过程整体可折叠
  const processBlocks = blocks.filter(b => b.kind === 'thinking' || b.kind === 'tool')
  const answerBlocks = blocks.filter(b => b.kind === 'answer')
  const hasProcess = processBlocks.length > 0
  const toolCount = processBlocks.filter(b => b.kind === 'tool').length
  const thinkingCount = processBlocks.filter(b => b.kind === 'thinking').length
  const answerText = answerBlocks.map(b => (b as AnswerBlock).text).join('')

  // 流式时过程始终展开; 完成后默认折叠, 可点击展开
  const [processOpen, setProcessOpen] = useState(false)
  const processExpanded = streaming ? true : processOpen

  return (
    <div className="turn-blocks">
      {hasProcess && (
        <div className="turn-process-card">
          <button className="turn-process-header" type="button" onClick={() => !streaming && setProcessOpen(v => !v)}>
            <span className="reasoning-chevron">{processExpanded ? '▾' : '▸'}</span>
            <span className="turn-process-title">{streaming ? '正在思考...' : '推理过程'}</span>
            <span className="turn-process-summary">
              {thinkingCount > 0 && `${thinkingCount} 步思考`}
              {thinkingCount > 0 && toolCount > 0 && ' · '}
              {toolCount > 0 && `${toolCount} 个工具调用`}
            </span>
          </button>
          {processExpanded && (
            <div className="turn-process-body">
              {processBlocks.map((block, idx) => {
                if (block.kind === 'thinking') {
                  return (
                    <div className="turn-thinking" key={`t-${idx}`}>
                      <MDEditor.Markdown source={block.text} style={{ background: 'transparent' }} />
                    </div>
                  )
                }
                // tool: 直接渲染 TaskBlock(带节点连线), 不再套 TaskTimeline 外壳
                const tool = (block as ToolBlock).tool
                return (
                  <div className="turn-tool-item" key={`tool-${idx}`}>
                    <div className="turn-tool-node" />
                    <div className="turn-tool-content">
                      <TaskBlock tool={{ ...tool, expanded: !!toolExpanded[tool.id] }} onToggle={() => toggleTool(tool.id)} />
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}
      {answerText && (
        <div className="message-content">
          <MDEditor.Markdown source={answerText} style={{ background: 'transparent' }} />
          {streaming && <span className="cursor-blink">|</span>}
        </div>
      )}
    </div>
  )
}

function normalizeAttachment(attachment: unknown): InterviewAttachment | undefined {
  if (!attachment || typeof attachment !== 'object') return undefined
  const item = attachment as Record<string, unknown>
  if (typeof item.type !== 'string') return undefined
  return {
    type: item.type,
    format: typeof item.format === 'string' ? item.format : undefined,
    language: typeof item.language === 'string' ? item.language : undefined,
    data: item.data
  }
}

function normalizeAttachments(attachments?: unknown[]): InterviewAttachment[] {
  if (!attachments?.length) return []
  return attachments
    .map(normalizeAttachment)
    .filter((attachment): attachment is InterviewAttachment => Boolean(attachment))
}

function splitLegacyDrawingContent(content: string): { content: string; attachments?: InterviewAttachment[] } {
  const marker = '候选人附加了绘图数据'
  const markerIndex = content.indexOf(marker)
  if (markerIndex < 0) {
    return { content }
  }

  const jsonMatch = content.slice(markerIndex).match(/```json\s*([\s\S]*?)\s*```/)
  if (!jsonMatch?.[1]) {
    return { content }
  }

  try {
    const drawingData = JSON.parse(jsonMatch[1])
    return {
      content: content.slice(0, markerIndex).trim(),
      attachments: [{ type: 'EXCALIDRAW', format: 'json', data: drawingData }]
    }
  } catch {
    return { content }
  }
}

function stringifyAttachmentData(data: unknown): string {
  if (data == null) return ''
  return typeof data === 'string' ? data : JSON.stringify(data)
}

function getAttachmentLabel(attachment: InterviewAttachment) {
  if (attachment.type === 'EXCALIDRAW') return '绘图'
  if (attachment.type === 'CODE') return '代码'
  return '附件'
}

function stripMarkdown(text: string): string {
  return text
    .replace(/#{1,6}\s/g, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/\*(.*?)\*/g, '$1')
    .replace(/`(.*?)`/g, '$1')
    .replace(/```[\s\S]*?```/g, '')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/^\s*[-*+]\s/gm, '')
    .replace(/^\s*\d+\.\s/gm, '')
    .replace(/^\s*>\s/gm, '')
    .replace(/\n{2,}/g, '\n')
    .trim()
}

function DrawingPreview({ drawing, uiOptions, fitKey = 0 }: { drawing: string; uiOptions: any; fitKey?: number }) {
  const apiRef = useRef<any>(null)
  const drawingData = useMemo(() => {
    try {
      return JSON.parse(drawing)
    } catch {
      return null
    }
  }, [drawing])
  const visibleElements = useMemo(() => {
    return drawingData?.elements?.filter((element: any) => !element.isDeleted) ?? []
  }, [drawingData])

  const fitDrawing = useCallback((api: any) => {
    if (!api || visibleElements.length === 0) return
    const fit = () => {
      api.refresh?.()
      api.scrollToContent(visibleElements, {
        fitToViewport: true,
        viewportZoomFactor: 0.74,
        animate: false
      })
    }
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(fit)
    })
    window.setTimeout(fit, 120)
    window.setTimeout(fit, 320)
  }, [visibleElements])

  useEffect(() => {
    fitDrawing(apiRef.current)
  }, [fitDrawing, fitKey])

  if (!drawingData) {
    return <div className="drawing-preview-error">绘图数据无法解析</div>
  }

  return (
    <Excalidraw
      initialData={drawingData}
      viewModeEnabled
      excalidrawAPI={(api) => {
        apiRef.current = api
        fitDrawing(api)
      }}
      UIOptions={uiOptions}
    />
  )
}

function CodePreview({ attachment }: { attachment: InterviewAttachment }) {
  const value = stringifyAttachmentData(attachment.data)
  return (
    <Editor
      value={value}
      language={attachment.language || 'plaintext'}
      theme="vs-dark"
      options={{
        readOnly: true,
        minimap: { enabled: false },
        fontSize: 14,
        lineNumbersMinChars: 3,
        wordWrap: 'on',
        scrollBeyondLastLine: false,
        automaticLayout: true
      }}
    />
  )
}

export default function InterviewRoom() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const sessionId = Number(id)

  // State
  const [session, setSession] = useState<InterviewSessionVO | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [inputText, setInputText] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [aiStreaming, setAiStreaming] = useState(false)
  const [aiTurnBlocks, setAiTurnBlocks] = useState<TurnBlock[]>([])
  const [sessionStatus, setSessionStatus] = useState<SessionStatus>('IN_PROGRESS')
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [wsConnected, setWsConnected] = useState(false)
  const [voiceWsConnected, setVoiceWsConnected] = useState(false)
  const [isRecording, setIsRecording] = useState(false)
  const [asrSubtitle, setAsrSubtitle] = useState('')
  const [recordingMode, setRecordingMode] = useState<'auto' | 'manual'>('auto')
  const [showAttachmentEditor, setShowAttachmentEditor] = useState(false)
  const [attachmentEditorTab, setAttachmentEditorTab] = useState<'drawing' | 'code'>('drawing')
  const [hasDrawing, setHasDrawing] = useState(false)
  const [drawingCanvasKey, setDrawingCanvasKey] = useState(0)
  const [codeText, setCodeText] = useState('')
  const [codeLanguage, setCodeLanguage] = useState('java')
  const [previewAttachments, setPreviewAttachments] = useState<InterviewAttachment[]>([])
  const [previewAttachmentKey, setPreviewAttachmentKey] = useState('0')
  const [previewFitKey, setPreviewFitKey] = useState(0)

  // Refs
  const conversationRef = useRef<HTMLDivElement>(null)
  const interviewWsRef = useRef<WsConnection | null>(null)
  const asrWsRef = useRef<WsConnection | null>(null)
  const ttsWsRef = useRef<WsConnection | null>(null)
  const recorderRef = useRef<AudioRecorder | null>(null)
  const playerRef = useRef<AudioPlayer | null>(null)
  const excalidrawDataRef = useRef<any>(null)
  const timerRef = useRef<number | null>(null)
  const waitingFirstQuestionRef = useRef(true)
  const aiTurnBlocksRef = useRef<TurnBlock[]>([])
  const toolSeqRef = useRef(0)
  const canInputRef = useRef(false)
  const isLoadingRef = useRef(false)
  const isVoiceModeRef = useRef(false)
  const sessionStatusRef = useRef<SessionStatus>('IN_PROGRESS')
  const reportPollRef = useRef<number | null>(null)

  const [isVoiceMode, setIsVoiceMode] = useState(false)
  const canInput = sessionStatus === 'IN_PROGRESS' && wsConnected

  const drawingUIOptions = useMemo(() => ({
    canvasActions: {
      changeViewBackgroundColor: false
    }
  } as const), [])

  const drawingPreviewUIOptions = useMemo(() => ({
    canvasActions: {
      changeViewBackgroundColor: false,
      export: false,
      loadScene: false,
      saveToActiveFile: false,
      toggleTheme: false
    }
  } as const), [])

  const codeLanguageOptions = useMemo(() => [
    { label: 'Java', value: 'java' },
    { label: 'TypeScript', value: 'typescript' },
    { label: 'JavaScript', value: 'javascript' },
    { label: 'Python', value: 'python' },
    { label: 'SQL', value: 'sql' },
    { label: 'JSON', value: 'json' },
    { label: 'Markdown', value: 'markdown' },
    { label: 'Text', value: 'plaintext' }
  ], [])

  const handleDrawingChange = useCallback((elements: readonly any[], state: any) => {
    const visibleElements = elements.filter(element => !element.isDeleted)
    excalidrawDataRef.current = {
      elements: visibleElements,
      appState: {
        viewBackgroundColor: state.viewBackgroundColor
      }
    }
    setHasDrawing(prev => {
      const next = visibleElements.length > 0
      return prev === next ? prev : next
    })
  }, [])

  useEffect(() => {
    canInputRef.current = canInput
  }, [canInput])

  useEffect(() => {
    isLoadingRef.current = isLoading
  }, [isLoading])

  useEffect(() => {
    isVoiceModeRef.current = isVoiceMode
  }, [isVoiceMode])

  useEffect(() => {
    sessionStatusRef.current = sessionStatus
  }, [sessionStatus])

  // Scroll to bottom
  const scrollToBottom = useCallback(() => {
    setTimeout(() => {
      if (conversationRef.current) {
        conversationRef.current.scrollTop = conversationRef.current.scrollHeight
      }
    }, 50)
  }, [])

  // Format time
  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }

  // Load session
  useEffect(() => {
    loadSession()
    return () => {
      cleanup()
    }
  }, [sessionId])

  const loadSession = async () => {
    try {
      const data = await interviewSessionApi.getById(sessionId)
      setSession(data)
      setSessionStatus(data.status)

      // 进入时若报告仍在生成中,启动轮询直至就绪/失败
      if (data.status === 'COMPLETED' || data.status === 'REPORT_GENERATING') {
        startReportPolling()
      }

      // Detect voice mode from config
      let voiceMode = false
      if (data.configId) {
        try {
          const { interviewConfigApi } = await import('@/api')
          const config = await interviewConfigApi.getById(data.configId)
          voiceMode = config.mode === 'VOICE'
          setIsVoiceMode(voiceMode)
          isVoiceModeRef.current = voiceMode
        } catch (e) {
          console.error('Failed to load config:', e)
        }
      }

      // Load history
      const history = await interviewSessionApi.getHistory(sessionId)
      const historyMessages: ChatMessage[] = history.map((h: InterviewTurnVO) => {
        const legacy = splitLegacyDrawingContent(h.content)
        const rawTools = (h.toolEvents ?? []).map((t, i) => ({
          id: i + 1,
          name: t.name,
          args: t.args,
          result: t.result,
          status: 'success' as const,
          expanded: false,
        }))
        // 还原为 TurnBlock 序列, 与实时渲染统一
        const turnBlocks = (h.speaker === 'AI' || h.speaker === 'INTERVIEWER')
          ? reasoningToBlocks(h.reasoning, rawTools.length > 0 ? rawTools : undefined, legacy.content)
          : undefined
        return {
          id: h.id,
          sessionId: h.sessionId,
          speaker: h.speaker as ChatMessage['speaker'],
          content: legacy.content,
          attachments: normalizeAttachments(h.attachments).length > 0 ? normalizeAttachments(h.attachments) : legacy.attachments,
          isHint: h.isHint,
          createdAt: h.createdAt,
          turnBlocks: turnBlocks && turnBlocks.length > 0 ? turnBlocks : undefined,
        }
      })
      setMessages(historyMessages)
      scrollToBottom()

      if (data.status === 'IN_PROGRESS' && data.startedAt) {
        startTimer(data.startedAt)
        waitingFirstQuestionRef.current = historyMessages.length === 0
        connectAllWs(voiceMode)
      } else if (data.status === 'PAUSED') {
        setPausedElapsed(data.startedAt, data.pausedAt)
      }
    } catch (error) {
      console.error('Failed to load session:', error)
      message.error('获取面试信息失败')
    }
  }

  const cleanup = () => {
    if (timerRef.current) clearInterval(timerRef.current)
    stopReportPolling()
    interviewWsRef.current?.close()
    asrWsRef.current?.close()
    ttsWsRef.current?.close()
    recorderRef.current?.stop()
    playerRef.current?.stop()
  }

  // Timer
  const startTimer = (startedAt: string) => {
    const start = new Date(startedAt).getTime()
    const update = () => {
      setElapsedSeconds(Math.floor((Date.now() - start) / 1000))
    }
    update()
    timerRef.current = window.setInterval(update, 1000)
  }

  const setPausedElapsed = (startedAt?: string, pausedAt?: string) => {
    if (!startedAt) return
    const start = new Date(startedAt).getTime()
    const end = pausedAt ? new Date(pausedAt).getTime() : Date.now()
    setElapsedSeconds(Math.max(0, Math.floor((end - start) / 1000)))
  }

  // WebSocket connections
  const connectAllWs = (voiceMode = isVoiceModeRef.current) => {
    connectInterviewWs()
    if (voiceMode) {
      connectAsrWs()
      connectTtsWs()
    }
  }

  const sendToInterview = (text: string, attachments: InterviewAttachment[] = []) => {
    if (!interviewWsRef.current?.connected) {
      message.error('WebSocket 未连接')
      return false
    }
    isLoadingRef.current = true
    setIsLoading(true)
    interviewWsRef.current.sendJson({ type: 'interview.stream_begin' })
    interviewWsRef.current.sendJson({ type: 'interview.stream_chunk', text, attachments })
    interviewWsRef.current.sendJson({ type: 'interview.stream_end' })
    return true
  }

  const submitUserMessage = (rawText: string, attachments: InterviewAttachment[] = []) => {
    const text = rawText.trim()
    const hasAttachments = attachments.length > 0

    if (!text && !hasAttachments) return false
    if (!canInputRef.current || isLoadingRef.current) return false

    const userMsg: ChatMessage = {
      id: Date.now(),
      sessionId,
      speaker: 'USER',
      content: text,
      attachments,
      createdAt: new Date().toISOString(),
    }

    setMessages(prev => [...prev, userMsg])
    scrollToBottom()
    sendToInterview(text, attachments)
    return true
  }

  const connectAsrWs = () => {
    if (asrWsRef.current?.connected) return
    if (!asrWsRef.current) {
      const ws = createAsrWs({ autoReconnect: false })
      ws.onOpen(() => setVoiceWsConnected(true))
      ws.onClose(() => setVoiceWsConnected(false))
      ws.onText((data) => {
        const msg = data as AsrServerMessage
        if (msg.type === 'asr.stream_chunk') {
          setAsrSubtitle(prev => prev + (msg as AsrStreamChunkMessage).text)
        } else if (msg.type === 'asr.stream_end') {
          const finalText = (msg as AsrStreamEndMessage).text?.trim()
          if (finalText) {
            const sent = submitUserMessage(finalText)
            if (!sent) {
              setInputText(prev => `${prev}${prev.trim() ? '\n' : ''}${finalText}`)
            }
          }
          setAsrSubtitle('')
        }
      })
      asrWsRef.current = ws
    }
    asrWsRef.current.connect()
  }

  const connectTtsWs = () => {
    if (ttsWsRef.current?.connected) return
    if (!ttsWsRef.current) {
      const ws = createTtsWs({ autoReconnect: false })
      ws.onOpen(() => {})
      ws.onClose(() => {})
      ws.onBinary((data) => {
        if (!playerRef.current) {
          playerRef.current = new AudioPlayer()
          playerRef.current.onPlayEnd = () => {}
        }
        playerRef.current.feedPcm(new Int16Array(data))
      })
      ws.onText((data) => {
        const msg = data as TtsServerMessage
        if (msg.type === 'tts.stream_begin') {
          playerRef.current?.stop()
        } else if (msg.type === 'tts.stream_end') {
          playerRef.current?.finish()
        }
      })
      ttsWsRef.current = ws
    }
    ttsWsRef.current.connect()
  }

  const connectInterviewWs = () => {
    if (interviewWsRef.current?.connected) return

    if (!interviewWsRef.current) {
      const ws = createInterviewWs({ autoReconnect: false })
      ws.onOpen(() => {
        setWsConnected(true)
        ws.sendJson({ type: 'interview.start', interviewSessionId: sessionId })
      })
      ws.onClose(() => {
        setWsConnected(false)
        if (sessionStatusRef.current === 'IN_PROGRESS') {
          handlePause()
        }
      })
      ws.onText((data) => handleInterviewMessage(data as InterviewServerMessage))
      interviewWsRef.current = ws
    }
    interviewWsRef.current.connect()
  }

  // 将一个 stream chunk 按 kind 追加到有序内容块序列(参考 gemini-cli 布局)
  // 同类型连续合并; 工具 call/result 配对合并为单个 ToolEventItem
  const appendChunkToBlocks = (blocks: TurnBlock[], chunk: InterviewStreamChunkMessage, seq: () => number): TurnBlock[] => {
    const kind = chunk.kind ?? 'answer'
    const next = blocks.slice()
    if (kind === 'thinking') {
      const text = chunk.text ?? ''
      const last = next[next.length - 1]
      if (last && last.kind === 'thinking') {
        next[next.length - 1] = { ...last, text: last.text + text }
      } else {
        next.push({ kind: 'thinking', text })
      }
      return next
    }
    if (kind === 'answer') {
      const text = chunk.text ?? ''
      const last = next[next.length - 1]
      if (last && last.kind === 'answer') {
        next[next.length - 1] = { ...last, text: last.text + text }
      } else {
        next.push({ kind: 'answer', text })
      }
      return next
    }
    // tool_call / tool_result: 按 tool.id 去重(流式增量会重复下发同一工具调用,取最新覆盖)
    const tool = chunk.tool
    const name = tool?.name ?? '未知工具'
    const toolId = tool?.id
    if (kind === 'tool_call') {
      // 已存在同 id 的工具块: 用最新 args 覆盖(流式参数逐步累积完整)
      const existIdx = toolId ? next.findIndex(b => b.kind === 'tool' && String(b.tool.id) === toolId) : -1
      if (existIdx >= 0) {
        const old = next[existIdx] as ToolBlock
        next[existIdx] = { kind: 'tool', tool: { ...old.tool, name, args: tool?.args ?? old.tool.args } }
      } else {
        next.push({
          kind: 'tool',
          tool: { id: toolId ?? seq(), name, args: tool?.args, status: 'running', expanded: false },
        })
      }
      return next
    }
    // tool_result: 优先按 id 配对,其次按同名 running 的 call, 补充结果
    const idIdx = toolId ? next.findIndex(b => b.kind === 'tool' && String(b.tool.id) === toolId) : -1
    const idx = idIdx >= 0
      ? -1
      : [...next].reverse().findIndex(b => b.kind === 'tool' && b.tool.name === name && b.tool.status === 'running')
    if (idIdx >= 0) {
      const tb = next[idIdx] as ToolBlock
      next[idIdx] = {
        kind: 'tool',
        tool: { ...tb.tool, result: tool?.result, status: 'success' },
      }
    } else if (idx >= 0) {
      const realIdx = next.length - 1 - idx
      const tb = next[realIdx] as ToolBlock
      next[realIdx] = {
        kind: 'tool',
        tool: { ...tb.tool, result: tool?.result, status: 'success' },
      }
    } else {
      // 未找到配对 call(丢失), 直接作为单独工具块
      next.push({
        kind: 'tool',
        tool: { id: seq(), name, result: tool?.result, status: 'success', expanded: false },
      })
    }
    return next
  }

  // 从内容块序列提取 answer 文本(用于落库 content 与 TTS)
  const extractAnswerText = (blocks: TurnBlock[]): string =>
    blocks.filter(b => b.kind === 'answer').map(b => (b as AnswerBlock).text).join('').trim()

  // Interview message handler
  const handleInterviewMessage = (msg: InterviewServerMessage) => {
    switch (msg.type) {
      case 'interview.status': {
        const m = msg as InterviewStatusMessage
        if (m.state === 'listening') {
          isLoadingRef.current = false
          setIsLoading(false)
          if (waitingFirstQuestionRef.current) {
            waitingFirstQuestionRef.current = false
            sendToInterview('请开始面试')
          }
        }
        break
      }
      case 'interview.stream_begin':
        aiTurnBlocksRef.current = []
        toolSeqRef.current = 0
        setAiStreaming(true)
        setAiTurnBlocks([])
        isLoadingRef.current = false
        setIsLoading(false)
        break
      case 'interview.stream_chunk': {
        const m = msg as InterviewStreamChunkMessage
        // 按 kind 分流到不同内容块: thinking/tool/answer 各成独立区块
        const blocks = appendChunkToBlocks(aiTurnBlocksRef.current, m, () => ++toolSeqRef.current)
        aiTurnBlocksRef.current = blocks
        setAiTurnBlocks(blocks)
        scrollToBottom()
        break
      }
      case 'interview.stream_end': {
        const blocks = aiTurnBlocksRef.current
        const answerText = extractAnswerText(blocks)
        if (answerText || blocks.length > 0) {
          const aiMsg: ChatMessage = {
            id: Date.now(),
            sessionId,
            speaker: 'AI',
            content: answerText,
            turnBlocks: blocks.length > 0 ? blocks : undefined,
            createdAt: new Date().toISOString(),
          }
          setMessages(prev => [...prev, aiMsg])

          // TTS: 只朗读 answer 块文本, thinking/tool 不朗读
          if (isVoiceModeRef.current && ttsWsRef.current?.connected && answerText) {
            const ttsText = stripMarkdown(answerText)
            if (ttsText) {
              ttsWsRef.current.sendJson({ type: 'tts.stream_begin' })
              ttsWsRef.current.sendJson({ type: 'tts.stream_chunk', text: ttsText })
              ttsWsRef.current.sendJson({ type: 'tts.stream_end' })
            }
          }
        }
        setAiStreaming(false)
        aiTurnBlocksRef.current = []
        setAiTurnBlocks([])
        scrollToBottom()
        break
      }
      case 'interview.reconnected': {
        const m = msg as InterviewReconnectedMessage
        message.info(`重连成功，历史对话 ${m.historyTurns} 轮`)
        break
      }
      case 'interview.error': {
        const m = msg as InterviewErrorMessage
        message.error(m.message)
        isLoadingRef.current = false
        setIsLoading(false)
        setAiStreaming(false)
        break
      }
    }
  }

  const sendTextMessage = () => {
    const text = inputText.trim()
    const drawing = excalidrawDataRef.current
    const hasDrawingData = drawing?.elements?.length > 0
    const code = codeText.trim()
    const hasCodeData = code.length > 0

    const attachments: InterviewAttachment[] = []
    if (hasDrawingData) {
      attachments.push({ type: 'EXCALIDRAW', format: 'json', data: drawing })
    }
    if (hasCodeData) {
      attachments.push({ type: 'CODE', format: 'text', language: codeLanguage, data: code })
    }

    if (!submitUserMessage(text, attachments)) return

    setInputText('')
    if (hasDrawingData) {
      excalidrawDataRef.current = null
      setHasDrawing(false)
      setDrawingCanvasKey(prev => prev + 1)
    }
    if (hasCodeData) {
      setCodeText('')
    }
    setShowAttachmentEditor(false)
  }

  // Session controls
  const handlePause = async () => {
    try {
      await interviewSessionApi.pause(sessionId)
      sessionStatusRef.current = 'PAUSED'
      setSessionStatus('PAUSED')
    } catch (error) {
      console.error('Failed to pause:', error)
    }
  }

  const handleResume = async () => {
    try {
      await interviewSessionApi.resume(sessionId)
      sessionStatusRef.current = 'IN_PROGRESS'
      setSessionStatus('IN_PROGRESS')
      connectAllWs()
    } catch (error) {
      console.error('Failed to resume:', error)
    }
  }

  const handleCancel = async () => {
    try {
      await interviewSessionApi.cancel(sessionId)
      sessionStatusRef.current = 'ABANDONED'
      setSessionStatus('ABANDONED')
      cleanup()
    } catch (error) {
      console.error('Failed to cancel:', error)
    }
  }

  const handleComplete = async () => {
    try {
      await interviewSessionApi.complete(sessionId)
      sessionStatusRef.current = 'COMPLETED'
      setSessionStatus('COMPLETED')
      cleanup()
      // 面试结束后异步生成报告,轮询状态直到报告就绪/失败
      startReportPolling()
    } catch (error) {
      console.error('Failed to complete:', error)
    }
  }

  const goToReport = () => {
    navigate(`/report/${sessionId}`)
  }

  const stopReportPolling = () => {
    if (reportPollRef.current) {
      clearInterval(reportPollRef.current)
      reportPollRef.current = null
    }
  }

  // 轮询会话状态,报告生成中 -> 就绪/失败时停止
  const startReportPolling = () => {
    stopReportPolling()
    reportPollRef.current = window.setInterval(async () => {
      try {
        const data = await interviewSessionApi.getById(sessionId)
        setSessionStatus(data.status)
        if (data.status !== 'COMPLETED' && data.status !== 'REPORT_GENERATING') {
          stopReportPolling()
        }
      } catch (error) {
        console.error('Failed to poll report status:', error)
      }
    }, 4000)
  }

  const handleRegenerateReport = async () => {
    try {
      await reportApi.regenerateBySessionId(sessionId)
      setSessionStatus('REPORT_GENERATING')
      startReportPolling()
    } catch (error) {
      console.error('Failed to regenerate report:', error)
    }
  }

  const requestHint = async () => {
    try {
      setIsLoading(true)
      const lastAiMsg = [...messages].reverse().find(m => m.speaker === 'AI' && !m.isHint)
      const hint = await interviewSessionApi.getHint(sessionId, lastAiMsg?.content)
      const hintMsg: ChatMessage = {
        id: Date.now(),
        sessionId,
        speaker: 'AI',
        content: hint,
        isHint: true,
        createdAt: new Date().toISOString(),
      }
      setMessages(prev => [...prev, hintMsg])
      scrollToBottom()
    } catch (error) {
      console.error('Failed to get hint:', error)
    } finally {
      isLoadingRef.current = false
      setIsLoading(false)
    }
  }

  const interruptAi = () => {
    playerRef.current?.stop()
    if (interviewWsRef.current?.connected) {
      interviewWsRef.current.sendJson({ type: 'interview.interrupt', interruptType: 'LLM_RESPONSE' })
      setAiStreaming(false)
      aiTurnBlocksRef.current = []
      setAiTurnBlocks([])
      isLoadingRef.current = false
      setIsLoading(false)
    }
  }

  // Voice recording
  const startRecording = async () => {
    if (!asrWsRef.current?.connected) {
      message.error('ASR 未连接')
      return
    }
    try {
      recorderRef.current = new AudioRecorder()
      recorderRef.current.setMode(recordingMode)
      recorderRef.current.onAudioData = (data) => {
        if (asrWsRef.current?.connected) {
          asrWsRef.current.sendBinary(new Uint8Array(data.buffer))
        }
      }
      recorderRef.current.onSpeechStart = () => {
        setAsrSubtitle('')
      }
      recorderRef.current.onSpeechEnd = () => {
        if (recordingMode === 'auto') {
          stopRecording()
        }
      }
      await recorderRef.current.start()
      setIsRecording(true)
      asrWsRef.current.sendJson({ type: 'asr.stream_begin' })
    } catch (e) {
      console.error('Failed to start recording:', e)
      message.error('启动录音失败')
    }
  }

  const stopRecording = () => {
    recorderRef.current?.stop()
    recorderRef.current = null
    setIsRecording(false)
    if (asrWsRef.current?.connected) {
      asrWsRef.current.sendJson({ type: 'asr.stream_end' })
    }
  }

  // Render
  const getAvatarText = (msg: ChatMessage) => {
    return msg.speaker === 'AI' || msg.speaker === 'INTERVIEWER' ? 'AI' : '我'
  }

  const getSenderName = (msg: ChatMessage) => {
    return msg.speaker === 'AI' || msg.speaker === 'INTERVIEWER' ? '面试官' : '我'
  }

  const getMessageClass = (msg: ChatMessage) => {
    return msg.speaker === 'AI' || msg.speaker === 'INTERVIEWER' ? 'ai' : 'user'
  }

  const getStatusLabel = () => {
    return sessionStatus === 'IN_PROGRESS' ? '进行中' :
      sessionStatus === 'PAUSED' ? '已暂停' :
      sessionStatus === 'COMPLETED' || sessionStatus === 'REPORT_GENERATING' ? '评估中' :
      sessionStatus === 'REPORT_COMPLETED' ? '已完成' :
      sessionStatus === 'REPORT_FAILED' ? '评估失败' : '已放弃'
  }

  const openAttachmentDrawer = (attachments: InterviewAttachment[], index: number) => {
    setPreviewAttachments(attachments)
    setPreviewAttachmentKey(String(index))
    setPreviewFitKey(prev => prev + 1)
  }

  const connectionLabel = wsConnected ? '主连接正常' : '主连接断开'

  return (
    <div className="interview-room">
      <div className="interview-main">
        <div className="interview-header">
          <div className="interview-info">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/interview')}>
              退出
            </Button>
            <div className="room-title-block">
              <span>{isVoiceMode ? '语音模拟面试' : '文字模拟面试'}</span>
              <strong>{session?.configName || `面试 #${sessionId}`}</strong>
            </div>
            <div className={`interview-status ${sessionStatus.toLowerCase()}`}>
              <span className="status-dot" />
              {getStatusLabel()}
            </div>
            <div className="ws-indicators">
              <div className={`ws-indicator ${wsConnected ? 'connected' : 'disconnected'}`}>
                {connectionLabel}
              </div>
            </div>
          </div>
          <div className="interview-timer">{formatTime(elapsedSeconds)}</div>
        </div>

        <div className="interview-content">
          <div className="conversation-area" ref={conversationRef}>
            {messages.map((msg, index) => (
              <div key={msg.id || index} className={`message ${getMessageClass(msg)}`}>
                <div className="message-avatar">{getAvatarText(msg)}</div>
                <div className="message-body">
                  <div className="message-sender">
                    {getSenderName(msg)}
                    {msg.isHint && <span className="hint-tag">提示</span>}
                  </div>
                  {(msg.speaker === 'AI' || msg.speaker === 'INTERVIEWER') && msg.turnBlocks && msg.turnBlocks.length > 0 ? (
                    <TurnBlocksView blocks={msg.turnBlocks} />
                  ) : (msg.content || !msg.attachments?.length) ? (
                    <div className="message-content">
                      <MDEditor.Markdown
                        source={msg.content}
                        style={{ background: 'transparent' }}
                      />
                    </div>
                  ) : null}
                  {msg.speaker === 'USER' && Boolean(msg.attachments?.length) && (
                    <div className="attachment-chip-row">
                      {msg.attachments?.map((attachment, attachmentIndex) => (
                        <Button
                          key={`${attachment.type}-${attachmentIndex}`}
                          className="attachment-chip"
                          icon={attachment.type === 'CODE' ? <CodeOutlined /> : <EditOutlined />}
                          onClick={() => openAttachmentDrawer(msg.attachments || [], attachmentIndex)}
                        >
                          {getAttachmentLabel(attachment)}
                        </Button>
                      ))}
                    </div>
                  )}
                  <div className="message-meta">
                    <span>{new Date(msg.createdAt).toLocaleTimeString('zh-CN')}</span>
                  </div>
                </div>
              </div>
            ))}

            {aiStreaming && (
              <div className="message ai">
                <div className="message-avatar">AI</div>
                <div className="message-body">
                  <div className="message-sender">面试官</div>
                  {aiTurnBlocks.length > 0 ? (
                    <TurnBlocksView blocks={aiTurnBlocks} streaming />
                  ) : (
                    <div className="message-content streaming">
                      <span className="cursor-blink">|</span>
                    </div>
                  )}
                </div>
              </div>
            )}

            {isLoading && !aiStreaming && (
              <div className="message ai">
                <div className="message-avatar">AI</div>
                <div className="message-body">
                  <div className="message-sender">面试官</div>
                  <div className="message-content loading-dots">
                    <span /><span /><span />
                  </div>
                </div>
              </div>
            )}
          </div>

          {isVoiceMode && asrSubtitle && (
            <div className="subtitle-area">
              <AudioOutlined className="subtitle-icon" />
              <span className="subtitle-text">{asrSubtitle}</span>
            </div>
          )}

          <div className="input-area">
            {showAttachmentEditor && (
              <div className={`attachment-editor-panel ${(hasDrawing || codeText.trim()) ? 'has-attachment' : ''}`}>
                <Tabs
                  activeKey={attachmentEditorTab}
                  onChange={(key) => setAttachmentEditorTab(key as 'drawing' | 'code')}
                  tabBarExtraContent={
                    <Button size="small" type="text" onClick={() => setShowAttachmentEditor(false)}>关闭</Button>
                  }
                  items={[
                    {
                      key: 'drawing',
                      label: '绘图',
                      icon: <EditOutlined />,
                      children: (
                        <div className="attachment-editor-pane">
                          <div className="attachment-editor-status">
                            <span>绘图板</span>
                            {hasDrawing && <em>已绘制</em>}
                          </div>
                          <div className="drawing-canvas">
                            <Excalidraw
                              key={drawingCanvasKey}
                              onChange={handleDrawingChange}
                              UIOptions={drawingUIOptions}
                            />
                          </div>
                        </div>
                      )
                    },
                    {
                      key: 'code',
                      label: '代码',
                      icon: <CodeOutlined />,
                      children: (
                        <div className="attachment-editor-pane">
                          <div className="code-toolbar">
                            <div className="attachment-editor-status">
                              <span>代码</span>
                              {codeText.trim() && <em>已编辑</em>}
                            </div>
                            <Select
                              value={codeLanguage}
                              onChange={setCodeLanguage}
                              size="small"
                              options={codeLanguageOptions}
                              style={{ width: 128 }}
                            />
                          </div>
                          <div className="code-editor">
                            <Editor
                              value={codeText}
                              language={codeLanguage}
                              theme="vs-dark"
                              onChange={(value) => setCodeText(value || '')}
                              options={{
                                minimap: { enabled: false },
                                fontSize: 14,
                                lineNumbersMinChars: 3,
                                wordWrap: 'on',
                                scrollBeyondLastLine: false,
                                automaticLayout: true
                              }}
                            />
                          </div>
                        </div>
                      )
                    }
                  ]}
                />
              </div>
            )}

            <div className="input-wrapper">
              <div className="input-container">
                <Input.TextArea
                  value={inputText}
                  onChange={e => setInputText(e.target.value)}
                  placeholder={canInput ? '输入你的回答...' : '等待连接...'}
                  disabled={!canInput}
                  autoSize={{ minRows: 1, maxRows: 4 }}
                  onPressEnter={e => {
                    if (!e.shiftKey) {
                      e.preventDefault()
                      sendTextMessage()
                    }
                  }}
                />
              </div>
              <div className="input-actions">
                <Button
                  icon={<EditOutlined />}
                  onClick={() => {
                    setAttachmentEditorTab('drawing')
                    setShowAttachmentEditor(prev => attachmentEditorTab === 'drawing' ? !prev : true)
                  }}
                  className={showAttachmentEditor && attachmentEditorTab === 'drawing' ? 'active' : ''}
                  title="绘图"
                />
                <Button
                  icon={<CodeOutlined />}
                  onClick={() => {
                    setAttachmentEditorTab('code')
                    setShowAttachmentEditor(prev => attachmentEditorTab === 'code' ? !prev : true)
                  }}
                  className={showAttachmentEditor && attachmentEditorTab === 'code' ? 'active' : ''}
                  title="代码"
                />
                {isVoiceMode && (
                  <Button
                    type={isRecording ? 'primary' : 'default'}
                    danger={isRecording}
                    icon={isRecording ? <StopOutlined /> : <AudioOutlined />}
                    onClick={isRecording ? stopRecording : startRecording}
                    disabled={!voiceWsConnected || !canInput}
                    title={isRecording ? '停止录音' : '开始录音'}
                  />
                )}
                {aiStreaming && (
                  <Button icon={<StopOutlined />} onClick={interruptAi} title="打断" />
                )}
                <Button icon={<BulbOutlined />} onClick={requestHint} disabled={isLoading} title="提示" />
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  onClick={sendTextMessage}
                  disabled={(!inputText.trim() && !hasDrawing && !codeText.trim()) || !canInput || isLoading}
                />
              </div>
            </div>
            {isVoiceMode && (
              <div className="voice-hint">
                {isRecording ? '正在聆听，录音结束后会自动发送识别结果' : '录音识别完成后会自动发送，无需再点发送'}
              </div>
            )}

            <div className="action-buttons">
              {sessionStatus === 'IN_PROGRESS' && (
                <>
                  <Button onClick={handlePause}>暂停</Button>
                  <Button type="primary" onClick={handleComplete}>完成面试</Button>
                  <Button danger onClick={handleCancel}>放弃面试</Button>
                </>
              )}
              {sessionStatus === 'PAUSED' && (
                <Button type="primary" onClick={handleResume}>继续面试</Button>
              )}
              {(sessionStatus === 'COMPLETED' || sessionStatus === 'REPORT_GENERATING') && (
                <Tooltip title="报告生成中，请稍候">
                  <Button type="primary" icon={<LoadingOutlined />} disabled>查看报告</Button>
                </Tooltip>
              )}
              {sessionStatus === 'REPORT_COMPLETED' && (
                <Button type="primary" icon={<FileTextOutlined />} onClick={goToReport}>查看报告</Button>
              )}
              {sessionStatus === 'REPORT_FAILED' && (
                <>
                  <Tooltip title="报告生成失败">
                    <Button type="primary" icon={<FileTextOutlined />} disabled>查看报告</Button>
                  </Tooltip>
                  <Button icon={<ReloadOutlined />} onClick={handleRegenerateReport}>重新生成报告</Button>
                </>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="interview-sidebar">
        <div className="side-hero">
          <span>当前房间</span>
          <strong>{getStatusLabel()}</strong>
          <p>{isRecording ? '正在采集语音回答' : aiStreaming ? '面试官正在回复' : '等待下一次回答'}</p>
        </div>

        <div className="side-panel">
          <div className="side-panel-header">面试进度</div>
          <div className="side-panel-body">
            <div className="info-item">
              <span className="info-label">当前状态</span>
              <span className="info-value">{getStatusLabel()}</span>
            </div>
            <div className="info-item">
              <span className="info-label">已用时间</span>
              <span className="info-value">{formatTime(elapsedSeconds)}</span>
            </div>
            <div className="info-item">
              <span className="info-label">对话轮次</span>
              <span className="info-value">{messages.length}</span>
            </div>
          </div>
        </div>

        <div className="side-panel">
          <div className="side-panel-header">面试信息</div>
          <div className="side-panel-body">
            <div className="info-item">
              <span className="info-label">面试模式</span>
              <span className="info-value">{isVoiceMode ? '语音' : '文字'}</span>
            </div>
            <div className="info-item">
              <span className="info-label">Interview WS</span>
              <span className={`info-value ${wsConnected ? 'text-success' : 'text-error'}`}>
                {wsConnected ? '已连接' : '未连接'}
              </span>
            </div>
            {isVoiceMode && (
              <>
                <div className="info-item">
                  <span className="info-label">ASR WS</span>
                  <span className={`info-value ${voiceWsConnected ? 'text-success' : 'text-error'}`}>
                    {voiceWsConnected ? '已连接' : '未连接'}
                  </span>
                </div>
                <div className="info-item">
                  <span className="info-label">录音模式</span>
                  <span className="info-value">
                    <Select
                      value={recordingMode}
                      onChange={setRecordingMode}
                      size="small"
                      style={{ width: 100 }}
                      options={[
                        { label: '自动', value: 'auto' },
                        { label: '手动', value: 'manual' }
                      ]}
                    />
                  </span>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      <Drawer
        title="附件"
        placement="left"
        width="min(92vw, 980px)"
        open={previewAttachments.length > 0}
        onClose={() => setPreviewAttachments([])}
        afterOpenChange={(open) => {
          if (open) {
            setPreviewFitKey(prev => prev + 1)
          }
        }}
        className="drawing-drawer"
        destroyOnClose
      >
        {previewAttachments.length > 0 && (
          <div className="attachment-drawer-content">
            <Tabs
              activeKey={previewAttachmentKey}
              onChange={(key) => {
                setPreviewAttachmentKey(key)
                setPreviewFitKey(prev => prev + 1)
              }}
              items={previewAttachments.map((attachment, index) => ({
                key: String(index),
                label: getAttachmentLabel(attachment),
                icon: attachment.type === 'CODE' ? <CodeOutlined /> : <EditOutlined />,
                children: attachment.type === 'EXCALIDRAW' ? (
                  <div className="drawing-drawer-canvas">
                    <DrawingPreview
                      drawing={stringifyAttachmentData(attachment.data)}
                      uiOptions={drawingPreviewUIOptions}
                      fitKey={previewFitKey}
                    />
                  </div>
                ) : attachment.type === 'CODE' ? (
                  <div className="code-drawer-editor">
                    <CodePreview attachment={attachment} />
                  </div>
                ) : (
                  <pre className="attachment-raw">{stringifyAttachmentData(attachment.data)}</pre>
                )
              }))}
            />
          </div>
        )}
      </Drawer>
    </div>
  )
}
