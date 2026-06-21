import React, { useState } from 'react'

/**
 * 工具事件（与后端 interview_turn.tool_events 对齐）
 * call/result 经合并后：每条代表一次工具调用，含入参与结果。
 */
export interface ToolEventItem {
  id: number
  /** 工具原始名，如 advance_to_next_question / resource_query */
  name: string
  /** 结构化入参 */
  args?: Record<string, unknown>
  /** 结果文本 */
  result?: string
  /** running=进行中, success=已完成, error=出错 */
  status: 'running' | 'success' | 'error'
  /** 是否展开详情 */
  expanded: boolean
}

/** 任务类型：按工具名映射，决定图标/标题/参数渲染 */
export type TaskType = 'question' | 'query' | 'tool'

interface TaskTypeMeta {
  /** 任务类型标签 */
  label: string
  /** 头部图标 */
  icon: string
  /** 主题色 */
  color: string
  /** 由工具名+入参生成人话标题 */
  title: (name: string, args?: Record<string, unknown>) => string
  /** 参数 key → 中文标签映射；未列出的 key 按原样展示 */
  argLabels?: Record<string, string>
}

/**
 * 类型注册表：新增任务类型只需在此追加一项。
 * 例如新增「数据库迁移」任务块，加一条 migrate 的映射即可。
 */
const TASK_REGISTRY: Record<string, TaskTypeMeta> = {
  advance_to_next_question: {
    label: '题目推进',
    icon: '➡️',
    color: '#4CAF50',
    title: () => '推进到下一道面试题',
  },
  resource_query: {
    label: '资料查询',
    icon: '🔍',
    color: '#2196F3',
    title: (_name, args) => {
      const t = args?.resource_type as string | undefined
      const map: Record<string, string> = { job: '岗位信息', resume: '简历信息', experience: '经历信息' }
      return `查询${map[t ?? ''] ?? '候选人资料'}`
    },
    argLabels: { resource_type: '资料类型', limit: '返回数量' },
  },
}

const DEFAULT_META: TaskTypeMeta = {
  label: '工具调用',
  icon: '🔧',
  color: '#607D8B',
  title: (name) => `调用工具 ${name}`,
}

/** 解析工具名对应的任务元信息 */
export function resolveTaskMeta(name: string): TaskTypeMeta {
  return TASK_REGISTRY[name] ?? DEFAULT_META
}

/** 解析任务类型（供外部按类型分组/统计） */
export function resolveTaskType(name: string): TaskType {
  if (name === 'advance_to_next_question') return 'question'
  if (name === 'resource_query') return 'query'
  return 'tool'
}

/** 将参数值格式化为可读字符串（不再对对象整体 JSON.stringify） */
function formatArgValue(val: unknown): string {
  if (val === null || val === undefined) return ''
  if (typeof val === 'string') return val
  if (typeof val === 'number' || typeof val === 'boolean') return String(val)
  if (Array.isArray(val)) return val.map((v) => (typeof v === 'object' ? JSON.stringify(v) : String(v))).join(', ')
  // 对象：按 key=value 平铺
  try {
    return Object.entries(val as Record<string, unknown>)
      .map(([k, v]) => `${k}=${formatArgValue(v)}`)
      .join(', ')
  } catch {
    return String(val)
  }
}

/** 检测字符串是否为 JSON 对象/数组 */
function tryParseJson(text: string | undefined): unknown | null {
  if (!text || typeof text !== 'string') return null
  const trimmed = text.trim()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null
  try {
    const parsed = JSON.parse(trimmed)
    if (parsed && typeof parsed === 'object') return parsed
  } catch {
    return null
  }
  return null
}

/** 将 JSON 美化并按 token 着色(key/string/number/boolean) */
function renderJsonHighlight(obj: unknown): React.ReactNode {
  const json = JSON.stringify(obj, null, 2)
  // 按 JSON 词法分割, 为每段着色
  return json.split(/(\"(?:[^"\\]|\\.)*\"\s*:)|(\"(?:[^"\\]|\\.)*\")|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b|(true|false|null)\b/).filter((part) => part !== undefined).map((part, i) => {
    if (/^\".*\"\s*:/.test(part)) return <span key={i} className="json-key">{part}</span>
    if (/^\".*\"$/.test(part)) return <span key={i} className="json-string">{part}</span>
    if (/^-?\d/.test(part)) return <span key={i} className="json-number">{part}</span>
    if (/^(true|false|null)$/.test(part)) return <span key={i} className="json-bool">{part}</span>
    return <span key={i}>{part}</span>
  })
}

/** 工具结果区: JSON 则高亮渲染, 否则纯文本 <pre> */
function ToolResult({ result }: { result: string | undefined }) {
  if (result === undefined) return null
  const parsed = tryParseJson(result)
  if (parsed !== null) {
    return <pre className="task-block-pre json-result">{renderJsonHighlight(parsed)}</pre>
  }
  return <pre className="task-block-pre">{result}</pre>
}

interface TaskBlockProps {
  tool: ToolEventItem
  onToggle: () => void
}

/**
 * 单个任务块：头部（图标+标题+状态+下拉）+ 可展开详情（参数字段行+结果代码块）。
 * 按任务类型动态渲染，参数按字段语义展示而非整体序列化。
 */
export function TaskBlock({ tool, onToggle }: TaskBlockProps) {
  const meta = resolveTaskMeta(tool.name)
  const title = meta.title(tool.name, tool.args)
  const statusLabel =
    tool.status === 'running' ? '运行中' : tool.status === 'error' ? '失败' : '已完成'
  const statusColor =
    tool.status === 'running' ? '#FFC107' : tool.status === 'error' ? '#F44336' : '#4CAF50'

  const argEntries = tool.args ? Object.entries(tool.args).filter(([, v]) => v !== undefined) : []

  return (
    <div className="task-block">
      <button className="task-block-header" onClick={onToggle}>
        <span className="task-block-icon" style={{ color: meta.color }}>{meta.icon}</span>
        <span className="task-block-title">{title}</span>
        <span className="task-block-tag" style={{ background: `${meta.color}1A`, color: meta.color }}>{meta.label}</span>
        <span className="task-block-status" style={{ color: statusColor }}>
          {tool.status === 'success' ? '✅' : tool.status === 'error' ? '❌' : '⏳'} {statusLabel}
        </span>
        <span className="task-block-chevron">{tool.expanded ? '▾' : '▸'}</span>
      </button>
      {tool.expanded && (
        <div className="task-block-body">
          {argEntries.length > 0 && (
            <div className="task-block-section">
              <div className="task-block-section-label">参数</div>
              <div className="task-block-fields">
                {argEntries.map(([key, val]) => (
                  <div className="task-block-field" key={key}>
                    <span className="task-block-field-key">{meta.argLabels?.[key] ?? key}</span>
                    <span className="task-block-field-value">{formatArgValue(val)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
          {tool.result !== undefined && (
            <div className="task-block-section">
              <div className="task-block-section-label">结果</div>
              <ToolResult result={tool.result} />
            </div>
          )}
        </div>
      )}
    </div>
  )
}

interface TaskTimelineProps {
  tools: ToolEventItem[]
  onToggle: (id: number) => void
}

/**
 * 任务时间线：按顺序渲染多个 TaskBlock，底部汇总耗时与完成情况。
 */
export function TaskTimeline({ tools, onToggle }: TaskTimelineProps) {
  const [expanded, setExpanded] = useState(true)
  if (!tools.length) return null
  const done = tools.filter((t) => t.status === 'success').length
  const total = tools.length

  return (
    <div className="task-timeline">
      <div className="task-timeline-bar">
        <span className="task-timeline-title">执行过程</span>
        <span className="task-timeline-summary">{done}/{total} 完成</span>
        <button className="task-timeline-toggle" onClick={() => setExpanded((v) => !v)}>
          {expanded ? '收起' : '展开'}
        </button>
      </div>
      {expanded && (
        <div className="task-timeline-list">
          {tools.map((tool, idx) => (
            <div className="task-timeline-item" key={tool.id}>
              <div className="task-timeline-node" />
              <div className="task-timeline-content">
                <TaskBlock tool={tool} onToggle={() => onToggle(tool.id)} />
              </div>
              {idx < total - 1 && <div className="task-timeline-line" />}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}