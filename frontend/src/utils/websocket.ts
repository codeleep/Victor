/**
 * WebSocket 连接管理工具
 * 封装 Interview / ASR / TTS 三个 WebSocket 连接
 */

type MessageHandler = (data: unknown) => void
type BinaryHandler = (data: ArrayBuffer) => void
type EventHandler = () => void

interface WsOptions {
  /** 自动重连 */
  autoReconnect?: boolean
  /** 重连间隔 (ms) */
  reconnectInterval?: number
  /** 最大重连次数 */
  maxReconnectAttempts?: number
}

/**
 * 通用 WebSocket 连接管理器
 */
class WsConnection {
  private ws: WebSocket | null = null
  private url: string
  private options: Required<WsOptions>
  private reconnectAttempts = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private manualClose = false

  private textHandlers: MessageHandler[] = []
  private binaryHandlers: BinaryHandler[] = []
  private openHandlers: EventHandler[] = []
  private closeHandlers: EventHandler[] = []
  private errorHandlers: ((err: Event) => void)[] = []

  constructor(url: string, options: WsOptions = {}) {
    this.url = url
    this.options = {
      autoReconnect: true,
      reconnectInterval: 3000,
      maxReconnectAttempts: 5,
      ...options,
    }
  }

  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) return

    this.manualClose = false
    const token = localStorage.getItem('token')
    const separator = this.url.includes('?') ? '&' : '?'
    const fullUrl = token ? `${this.url}${separator}token=${token}` : this.url

    this.ws = new WebSocket(fullUrl)

    this.ws.onopen = () => {
      this.reconnectAttempts = 0
      this.openHandlers.forEach((h) => h())
    }

    this.ws.onmessage = (event) => {
      if (typeof event.data === 'string') {
        try {
          const parsed = JSON.parse(event.data)
          this.textHandlers.forEach((h) => h(parsed))
        } catch {
          this.textHandlers.forEach((h) => h(event.data))
        }
      } else if (event.data instanceof ArrayBuffer) {
        this.binaryHandlers.forEach((h) => h(event.data))
      } else if (event.data instanceof Blob) {
        event.data.arrayBuffer().then((buf) => {
          this.binaryHandlers.forEach((h) => h(buf))
        })
      }
    }

    this.ws.onclose = () => {
      this.closeHandlers.forEach((h) => h())
      if (!this.manualClose && this.options.autoReconnect) {
        this.tryReconnect()
      }
    }

    this.ws.onerror = (err) => {
      this.errorHandlers.forEach((h) => h(err))
    }
  }

  private tryReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) return
    this.reconnectAttempts++
    this.reconnectTimer = setTimeout(() => {
      this.connect()
    }, this.options.reconnectInterval)
  }

  sendJson(data: unknown): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }

  sendBinary(data: ArrayBuffer | Uint8Array): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(data instanceof Uint8Array ? data.buffer : data)
    }
  }

  close(): void {
    this.manualClose = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
  }

  get connected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN
  }

  onText(handler: MessageHandler): () => void {
    this.textHandlers.push(handler)
    return () => {
      this.textHandlers = this.textHandlers.filter((h) => h !== handler)
    }
  }

  onBinary(handler: BinaryHandler): () => void {
    this.binaryHandlers.push(handler)
    return () => {
      this.binaryHandlers = this.binaryHandlers.filter((h) => h !== handler)
    }
  }

  onOpen(handler: EventHandler): () => void {
    this.openHandlers.push(handler)
    return () => {
      this.openHandlers = this.openHandlers.filter((h) => h !== handler)
    }
  }

  onClose(handler: EventHandler): () => void {
    this.closeHandlers.push(handler)
    return () => {
      this.closeHandlers = this.closeHandlers.filter((h) => h !== handler)
    }
  }

  onError(handler: (err: Event) => void): () => void {
    this.errorHandlers.push(handler)
    return () => {
      this.errorHandlers = this.errorHandlers.filter((h) => h !== handler)
    }
  }
}

function getWsBaseUrl(): string {
  const loc = window.location
  const protocol = loc.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${loc.host}`
}

/**
 * Interview WebSocket - 纯文本 JSON 通信
 */
export function createInterviewWs(options?: WsOptions): WsConnection {
  return new WsConnection(`${getWsBaseUrl()}/ws/interview`, options)
}

/**
 * ASR WebSocket - 接收 binary + JSON
 */
export function createAsrWs(options?: WsOptions): WsConnection {
  return new WsConnection(`${getWsBaseUrl()}/ws/asr`, options)
}

/**
 * TTS WebSocket - 发送 JSON, 接收 binary
 */
export function createTtsWs(options?: WsOptions): WsConnection {
  return new WsConnection(`${getWsBaseUrl()}/ws/tts`, options)
}

export { WsConnection }
