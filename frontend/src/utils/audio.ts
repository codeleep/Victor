/**
 * 音频采集与播放工具
 * 用于 ASR（录音→PCM）和 TTS（PCM→播放）
 * 支持自动语音检测 (VAD) 和手动录音两种模式
 */

export type RecordingMode = 'auto' | 'manual'

/**
 * 录音器 - 从麦克风采集 PCM 16kHz 16bit 单声道音频
 * 支持自动语音检测 (VAD) 和手动录音模式
 */
export class AudioRecorder {
  private audioContext: AudioContext | null = null
  private mediaStream: MediaStream | null = null
  private source: MediaStreamAudioSourceNode | null = null
  private processor: ScriptProcessorNode | null = null
  private highPassFilter: BiquadFilterNode | null = null
  private lowPassFilter: BiquadFilterNode | null = null
  private isRecording = false
  private mode: RecordingMode = 'auto'

  // VAD 状态
  private isSpeaking = false
  private silenceStart = 0
  private speechStartTime = 0
  private noiseFloor = 80

  // VAD 常量
  private static readonly SILENCE_DURATION_MS = 1500
  private static readonly MIN_SPEECH_DURATION_MS = 300
  private static readonly NOISE_FLOOR_ALPHA = 0.95
  private static readonly SPEECH_THRESHOLD_MULTIPLIER = 3.5
  private static readonly MIN_DYNAMIC_THRESHOLD = 180
  private static readonly NOISE_GATE_RATIO = 0.6

  /** 每帧 PCM 数据回调 (Int16Array)，仅在说话期间触发（自动模式）或持续触发（手动模式） */
  onAudioData: ((data: Int16Array) => void) | null = null
  /** 检测到说话开始回调（自动模式） */
  onSpeechStart: (() => void) | null = null
  /** 检测到说话结束回调（自动模式） */
  onSpeechEnd: (() => void) | null = null

  /** 设置录音模式 */
  setMode(mode: RecordingMode): void {
    this.mode = mode
  }

  /** 当前是否正在说话（VAD 检测） */
  get speaking(): boolean {
    return this.isSpeaking
  }

  async start(): Promise<void> {
    if (this.isRecording) return

    // 重置 VAD 状态
    this.isSpeaking = false
    this.silenceStart = 0
    this.speechStartTime = 0
    this.noiseFloor = 80

    this.mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        sampleRate: 16000,
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    })

    this.audioContext = new AudioContext({ sampleRate: 16000 })
    this.source = this.audioContext.createMediaStreamSource(this.mediaStream)

    // 高通滤波器：去除低频噪声
    this.highPassFilter = this.audioContext.createBiquadFilter()
    this.highPassFilter.type = 'highpass'
    this.highPassFilter.frequency.value = 80
    this.highPassFilter.Q.value = 0.7

    // 低通滤波器：去除高频噪声
    this.lowPassFilter = this.audioContext.createBiquadFilter()
    this.lowPassFilter.type = 'lowpass'
    this.lowPassFilter.frequency.value = 7000
    this.lowPassFilter.Q.value = 0.7

    this.processor = this.audioContext.createScriptProcessor(4096, 1, 1)
    this.processor.onaudioprocess = (e) => {
      if (!this.isRecording) return
      const inputData = e.inputBuffer.getChannelData(0)

      // 计算音频能量
      let sum = 0
      for (let i = 0; i < inputData.length; i++) {
        sum += inputData[i] * inputData[i]
      }
      const energy = (sum / inputData.length) * 1000000

      // 非说话状态时更新噪声基底
      if (!this.isSpeaking) {
        this.noiseFloor =
          AudioRecorder.NOISE_FLOOR_ALPHA * this.noiseFloor +
          (1 - AudioRecorder.NOISE_FLOOR_ALPHA) * energy
      }

      // 动态阈值
      const dynamicThreshold = Math.max(
        AudioRecorder.MIN_DYNAMIC_THRESHOLD,
        this.noiseFloor * AudioRecorder.SPEECH_THRESHOLD_MULTIPLIER,
      )
      const gateThreshold = this.noiseFloor * AudioRecorder.NOISE_GATE_RATIO
      const gateActive = energy < gateThreshold

      // 自动模式：VAD 语音检测
      if (this.mode === 'auto') {
        const now = Date.now()

        if (energy > dynamicThreshold) {
          if (!this.isSpeaking) {
            this.isSpeaking = true
            this.speechStartTime = now
            this.onSpeechStart?.()
          }
          this.silenceStart = 0
        } else if (this.isSpeaking) {
          if (this.silenceStart === 0) {
            this.silenceStart = now
          } else if (now - this.silenceStart > AudioRecorder.SILENCE_DURATION_MS) {
            this.isSpeaking = false
            if (now - this.speechStartTime > AudioRecorder.MIN_SPEECH_DURATION_MS) {
              this.onSpeechEnd?.()
            }
            this.silenceStart = 0
            return // 说话结束的静音帧不发送
          }
        } else {
          return // 非说话状态不发送
        }
      }

      // 噪声门：低能量帧衰减
      const processedData = new Float32Array(inputData.length)
      for (let i = 0; i < inputData.length; i++) {
        processedData[i] = gateActive ? inputData[i] * 0.1 : inputData[i]
      }

      const int16 = float32ToInt16(processedData)
      this.onAudioData?.(int16)
    }

    // 连接：source → highPass → lowPass → processor → destination
    this.source.connect(this.highPassFilter)
    this.highPassFilter.connect(this.lowPassFilter)
    this.lowPassFilter.connect(this.processor)
    this.processor.connect(this.audioContext.destination)

    this.isRecording = true
  }

  stop(): void {
    this.isRecording = false
    this.isSpeaking = false
    this.processor?.disconnect()
    this.lowPassFilter?.disconnect()
    this.highPassFilter?.disconnect()
    this.source?.disconnect()
    this.mediaStream?.getTracks().forEach((t) => t.stop())
    this.audioContext?.close()
    this.processor = null
    this.lowPassFilter = null
    this.highPassFilter = null
    this.source = null
    this.mediaStream = null
    this.audioContext = null
  }

  get recording(): boolean {
    return this.isRecording
  }
}

/**
 * 音频播放器 - 播放 PCM 24kHz 16bit 单声道数据
 */
export class AudioPlayer {
  private static readonly SAMPLE_RATE = 24000
  private audioContext: AudioContext | null = null
  private buffers: AudioBuffer[] = []
  private isPlaying = false
  private scheduledTime = 0

  /** 播放开始回调 */
  onPlayStart: (() => void) | null = null
  /** 播放结束回调 */
  onPlayEnd: (() => void) | null = null

  private ensureContext(): AudioContext {
    if (!this.audioContext) {
      this.audioContext = new AudioContext({ sampleRate: AudioPlayer.SAMPLE_RATE })
    }
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume()
    }
    return this.audioContext
  }

  /**
   * 添加 PCM 数据块并播放
   * @param pcmData Int16Array PCM 数据
   */
  feedPcm(pcmData: Int16Array): void {
    const ctx = this.ensureContext()
    const float32 = int16ToFloat32(pcmData)
    const buffer = ctx.createBuffer(1, float32.length, AudioPlayer.SAMPLE_RATE)
    buffer.getChannelData(0).set(float32)
    this.buffers.push(buffer)

    if (!this.isPlaying) {
      this.startPlayback()
    } else {
      // 播放进行中，持续调度新到达的 buffer
      this.scheduleBuffers()
    }
  }

  /**
   * 标记流结束，等待当前缓冲播放完毕
   */
  finish(): void {
    // 播放完剩余 buffer 后触发 onPlayEnd
    // 延迟 200ms 以等待 WebSocket Blob→ArrayBuffer 异步转换完成
    const checkDone = () => {
      if (this.buffers.length === 0 && this.scheduledTime <= (this.audioContext?.currentTime ?? 0) + 0.1) {
        this.isPlaying = false
        this.onPlayEnd?.()
      } else {
        setTimeout(checkDone, 100)
      }
    }
    if (!this.isPlaying) {
      this.onPlayEnd?.()
    } else {
      setTimeout(checkDone, 200)
    }
  }

  private startPlayback(): void {
    const ctx = this.ensureContext()
    this.isPlaying = true
    this.scheduledTime = ctx.currentTime
    this.onPlayStart?.()
    this.scheduleBuffers()
  }

  private scheduleBuffers(): void {
    const ctx = this.audioContext
    if (!ctx) return

    while (this.buffers.length > 0) {
      const buffer = this.buffers.shift()!
      const source = ctx.createBufferSource()
      source.buffer = buffer
      source.connect(ctx.destination)

      if (this.scheduledTime < ctx.currentTime) {
        this.scheduledTime = ctx.currentTime
      }
      source.start(this.scheduledTime)
      this.scheduledTime += buffer.duration
    }
  }

  /**
   * 停止播放并清空缓冲
   */
  stop(): void {
    this.isPlaying = false
    this.buffers = []
    this.audioContext?.close()
    this.audioContext = null
  }

  get playing(): boolean {
    return this.isPlaying
  }
}

// --- 工具函数 ---

function float32ToInt16(float32: Float32Array): Int16Array {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]))
    int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
  }
  return int16
}

function int16ToFloat32(int16: Int16Array): Float32Array {
  const float32 = new Float32Array(int16.length)
  for (let i = 0; i < int16.length; i++) {
    float32[i] = int16[i] / (int16[i] < 0 ? 0x8000 : 0x7fff)
  }
  return float32
}
