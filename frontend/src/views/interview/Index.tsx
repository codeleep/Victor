import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Button, Table, Tag, Space, Tooltip, message, Popconfirm } from 'antd'
import { PlusOutlined, PlayCircleOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, FileTextOutlined, LoadingOutlined } from '@ant-design/icons'
import { interviewConfigApi, interviewSessionApi, reportApi } from '@/api'
import type { InterviewConfigVO, InterviewConfigStatus } from '@/types'
import './Index.scss'

const STATUS_TAG_MAP: Record<string, { color: string; text: string }> = {
  DRAFT: { color: 'default', text: '草稿' },
  GENERATING: { color: 'processing', text: '生成中' },
  GENERATE_FAILED: { color: 'error', text: '生成失败' },
  READY: { color: 'success', text: '就绪' },
  IN_PROGRESS: { color: 'processing', text: '进行中' },
  PAUSED: { color: 'warning', text: '已暂停' },
  COMPLETED: { color: 'processing', text: '评估中' },
  REPORT_GENERATING: { color: 'processing', text: '评估中' },
  REPORT_COMPLETED: { color: 'success', text: '已出报告' },
  REPORT_FAILED: { color: 'error', text: '评估失败' },
  ABANDONED: { color: 'default', text: '已放弃' },
  ARCHIVED: { color: 'warning', text: '已归档' },
}

const isReportGenerating = (s: InterviewConfigStatus) => s === 'COMPLETED' || s === 'REPORT_GENERATING'
const isReportReady = (s: InterviewConfigStatus) => s === 'REPORT_COMPLETED'
const isReportFailed = (s: InterviewConfigStatus) => s === 'REPORT_FAILED'
const isInterviewing = (s: InterviewConfigStatus) => s === 'IN_PROGRESS' || s === 'PAUSED'

export default function InterviewIndex() {
  const navigate = useNavigate()
  const [configs, setConfigs] = useState<InterviewConfigVO[]>([])
  const [loading, setLoading] = useState(true)
  const [regeneratingReport, setRegeneratingReport] = useState<number | null>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    loadConfigs()
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
    }
  }, [])

  // 存在评估中的会话时,轮询刷新状态,生成完成后按钮自动可点击
  useEffect(() => {
    const hasGenerating = configs.some((c) => isReportGenerating(c.status))
    if (hasGenerating && !timerRef.current) {
      timerRef.current = setInterval(loadConfigs, 4000)
    } else if (!hasGenerating && timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
  }, [configs])

  const loadConfigs = async () => {
    try {
      const configData = await interviewConfigApi.list()
      setConfigs(configData)
    } catch (error) {
      console.error('Failed to load configs:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await interviewConfigApi.delete(id)
      message.success('删除成功')
      loadConfigs()
    } catch (error) {
      console.error('Failed to delete config:', error)
    }
  }

  const handleRegenerate = async (id: number) => {
    try {
      await interviewConfigApi.publish(id)
      message.success('已重新提交生成')
      loadConfigs()
    } catch (error) {
      console.error('Failed to regenerate:', error)
    }
  }

  const handleArchive = async (id: number) => {
    try {
      await interviewConfigApi.archive(id)
      message.success('已归档')
      loadConfigs()
    } catch (error) {
      console.error('Failed to archive config:', error)
    }
  }

  const handleStart = async (configId: number) => {
    try {
      const config = configs.find((c) => c.id === configId)
      if (isInterviewing(config?.status as InterviewConfigStatus)) {
        navigate(`/interview/room/${configId}`)
        return
      }
      if (config?.status !== 'READY') {
        message.warning('当前状态无法开始面试')
        return
      }
      const sessionId = await interviewSessionApi.create(configId)
      navigate(`/interview/room/${sessionId}`)
    } catch (error) {
      console.error('Failed to start interview:', error)
    }
  }

  const handleViewReport = (id: number) => {
    navigate(`/report/${id}`)
  }

  const handleRegenerateReport = async (id: number) => {
    try {
      setRegeneratingReport(id)
      await reportApi.regenerateBySessionId(id)
      message.success('已重新提交评估')
      await loadConfigs()
    } catch (error) {
      console.error('Failed to regenerate report:', error)
    } finally {
      setRegeneratingReport(null)
    }
  }

  const renderStatus = (status: string, record: InterviewConfigVO) => {
    const info = STATUS_TAG_MAP[status] || { color: 'default', text: status }
    const tag = <Tag color={info.color}>{info.text}</Tag>
    if (status === 'GENERATE_FAILED' && record.generateError) {
      return <Tooltip title={record.generateError}>{tag}</Tooltip>
    }
    return tag
  }

  const renderStartButton = (record: InterviewConfigVO) => {
    if (isInterviewing(record.status)) {
      return (
        <Button
          type="link"
          icon={<PlayCircleOutlined />}
          onClick={() => handleStart(record.id)}
        >
          {record.status === 'PAUSED' ? '继续' : '进入'}
        </Button>
      )
    }
    if (record.status === 'READY') {
      return (
        <Button
          type="link"
          icon={<PlayCircleOutlined />}
          onClick={() => handleStart(record.id)}
        >
          开始
        </Button>
      )
    }
    return null
  }

  const renderReportButton = (record: InterviewConfigVO) => {
    if (isReportGenerating(record.status)) {
      // 评估中:禁止点击进入报告
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
        <Button
          type="link"
          icon={<FileTextOutlined />}
          onClick={() => handleViewReport(record.id)}
        >
          查看报告
        </Button>
      )
    }
    if (isReportFailed(record.status)) {
      return (
        <Button
          type="link"
          icon={<ReloadOutlined />}
          loading={regeneratingReport === record.id}
          onClick={() => handleRegenerateReport(record.id)}
        >
          重新生成报告
        </Button>
      )
    }
    if (record.status === 'ABANDONED') {
      return (
        <Button type="link" disabled>
          已放弃
        </Button>
      )
    }
    return null
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '配置名称', dataIndex: 'name', key: 'name' },
    {
      title: '面试模式',
      dataIndex: 'mode',
      key: 'mode',
      width: 100,
      render: (mode: string) => (
        <Tag color={mode === 'VOICE' ? 'blue' : 'green'}>
          {mode === 'VOICE' ? '语音' : '文字'}
        </Tag>
      )
    },
    { title: '岗位', dataIndex: 'jobName', key: 'jobName', render: (v: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string, record: InterviewConfigVO) => renderStatus(status, record)
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString('zh-CN')
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_: unknown, record: InterviewConfigVO) => (
        <Space>
          {renderStartButton(record)}
          {renderReportButton(record)}
          {record.status === 'GENERATE_FAILED' && (
            <Button type="link" icon={<ReloadOutlined />} onClick={() => handleRegenerate(record.id)}>
              重新生成
            </Button>
          )}
          <Button type="link" icon={<EditOutlined />} onClick={() => navigate('/interview/config')}>
            编辑
          </Button>
          {record.status === 'READY' && (
            <Popconfirm title="确定归档？" onConfirm={() => handleArchive(record.id)}>
              <Button type="link">归档</Button>
            </Popconfirm>
          )}
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div className="interview-index-page">
      <div className="page-header">
        <div className="header-left">
          <h1>面试记录</h1>
          <p>管理面试配置和记录</p>
        </div>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/interview/config')}>
            新建配置
          </Button>
        </Space>
      </div>
      <Card className="table-card">
        <Table
          columns={columns}
          dataSource={configs}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
        />
      </Card>
    </div>
  )
}