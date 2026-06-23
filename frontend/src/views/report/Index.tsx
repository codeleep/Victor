import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Table, Button, Tag, Tooltip } from 'antd'
import { FileTextOutlined, LoadingOutlined, ReloadOutlined } from '@ant-design/icons'
import { reportApi, interviewSessionApi } from '@/api'
import type { InterviewSessionVO, SessionStatus } from '@/types'
import './Report.scss'

const isReportGenerating = (s: SessionStatus) => s === 'COMPLETED' || s === 'REPORT_GENERATING'
const isReportReady = (s: SessionStatus) => s === 'REPORT_COMPLETED'
const isReportFailed = (s: SessionStatus) => s === 'REPORT_FAILED'

// 仅展示面试已结束且涉及报告的会话
const isReportRelevant = (s: SessionStatus) =>
  isReportGenerating(s) || isReportReady(s) || isReportFailed(s)

const STATUS_TAG_MAP: Record<string, { color: string; text: string }> = {
  COMPLETED: { color: 'processing', text: '评估中' },
  REPORT_GENERATING: { color: 'processing', text: '评估中' },
  REPORT_COMPLETED: { color: 'success', text: '已出报告' },
  REPORT_FAILED: { color: 'error', text: '评估失败' },
}

export default function ReportIndex() {
  const navigate = useNavigate()
  const [sessions, setSessions] = useState<InterviewSessionVO[]>([])
  const [loading, setLoading] = useState(true)
  const [regenerating, setRegenerating] = useState<number | null>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    loadSessions()
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
    }
  }, [])

  // 存在评估中的会话时轮询,生成完成后按钮自动可点击
  useEffect(() => {
    const hasGenerating = sessions.some((s) => isReportGenerating(s.status))
    if (hasGenerating && !timerRef.current) {
      timerRef.current = setInterval(loadSessions, 4000)
    } else if (!hasGenerating && timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
  }, [sessions])

  const loadSessions = async () => {
    try {
      const data = await interviewSessionApi.list()
      setSessions(data.filter((s) => isReportRelevant(s.status)))
    } catch (error) {
      console.error('Failed to load sessions:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleRegenerate = async (id: number) => {
    try {
      setRegenerating(id)
      await reportApi.regenerateBySessionId(id)
      await loadSessions()
    } catch (error) {
      console.error('Failed to regenerate report:', error)
    } finally {
      setRegenerating(null)
    }
  }

  const renderAction = (_: unknown, record: InterviewSessionVO) => {
    if (isReportGenerating(record.status)) {
      return (
        <Tooltip title="报告生成中，请稍候">
          <Button type="link" disabled icon={<LoadingOutlined />}>
            报告生成中
          </Button>
        </Tooltip>
      )
    }
    if (isReportReady(record.status)) {
      return (
        <Button type="link" icon={<FileTextOutlined />} onClick={() => navigate(`/report/${record.id}`)}>
          查看报告
        </Button>
      )
    }
    if (isReportFailed(record.status)) {
      return (
        <Button
          type="link"
          icon={<ReloadOutlined />}
          loading={regenerating === record.id}
          onClick={() => handleRegenerate(record.id)}
        >
          重新生成报告
        </Button>
      )
    }
    return null
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '配置名称', dataIndex: 'configName', key: 'configName', render: (v: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: string) => {
        const info = STATUS_TAG_MAP[status] || { color: 'default', text: status }
        return <Tag color={info.color}>{info.text}</Tag>
      }
    },
    {
      title: '完成时间',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 180,
      render: (v: string) => (v ? new Date(v).toLocaleString('zh-CN') : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: renderAction
    }
  ]

  return (
    <div className="report-page">
      <div className="page-header">
        <div className="header-left">
          <h1>面试报告</h1>
          <p>查看已完成面试的评估报告</p>
        </div>
      </div>
      <Card className="table-card">
        <Table columns={columns} dataSource={sessions} rowKey="id" loading={loading} pagination={{ pageSize: 20 }} />
      </Card>
    </div>
  )
}