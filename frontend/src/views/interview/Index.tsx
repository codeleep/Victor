import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Button, Table, Tag, Space, Tooltip, message, Popconfirm } from 'antd'
import { PlusOutlined, PlayCircleOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons'
import { interviewConfigApi, interviewSessionApi } from '@/api'
import type { InterviewConfigVO, InterviewSessionVO } from '@/types'
import './Index.scss'

export default function InterviewIndex() {
  const navigate = useNavigate()
  const [configs, setConfigs] = useState<InterviewConfigVO[]>([])
  const [sessions, setSessions] = useState<InterviewSessionVO[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadConfigs()
  }, [])

  const loadConfigs = async () => {
    setLoading(true)
    try {
      const [configData, sessionData] = await Promise.all([
        interviewConfigApi.list(),
        interviewSessionApi.list()
      ])
      setConfigs(configData)
      setSessions(sessionData)
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
      const existing = sessions.find(s => s.configId === configId)
      if (existing?.status === 'IN_PROGRESS' || existing?.status === 'PAUSED') {
        navigate(`/interview/room/${existing.id}`)
        return
      }
      if (existing?.status === 'COMPLETED') {
        message.warning('该面试已完成，不能再次进入')
        return
      }
      if (existing?.status === 'ABANDONED') {
        message.warning('该面试已放弃，不能再次进入')
        return
      }
      const sessionId = await interviewSessionApi.create(configId)
      navigate(`/interview/room/${sessionId}`)
    } catch (error) {
      console.error('Failed to start interview:', error)
    }
  }

  const getSessionByConfigId = (configId: number) => sessions.find(s => s.configId === configId)

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
      render: (status: string, record: InterviewConfigVO) => {
        const map: Record<string, { color: string; text: string }> = {
          DRAFT: { color: 'default', text: '草稿' },
          GENERATING: { color: 'processing', text: '生成中' },
          GENERATE_FAILED: { color: 'error', text: '生成失败' },
          READY: { color: 'success', text: '就绪' },
          ARCHIVED: { color: 'warning', text: '已归档' }
        }
        const info = map[status] || { color: 'default', text: status }
        const tag = <Tag color={info.color}>{info.text}</Tag>
        if (status === 'GENERATE_FAILED' && record.generateError) {
          return <Tooltip title={record.generateError}>{tag}</Tooltip>
        }
        return tag
      }
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
      width: 200,
      render: (_: unknown, record: InterviewConfigVO) => (
        <Space>
          {(() => {
            const existing = getSessionByConfigId(record.id)
            const canEnterExisting = existing?.status === 'IN_PROGRESS' || existing?.status === 'PAUSED'
            const isClosedSession = existing?.status === 'COMPLETED' || existing?.status === 'ABANDONED'
            const disabled = isClosedSession || (!canEnterExisting && record.status !== 'READY')
            const text = existing?.status === 'PAUSED' ? '继续' : existing?.status === 'IN_PROGRESS' ? '进入' : '开始'
            return (
              <Button
                type="link"
                icon={<PlayCircleOutlined />}
                onClick={() => handleStart(record.id)}
                disabled={disabled}
              >
                {text}
              </Button>
            )
          })()}
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
