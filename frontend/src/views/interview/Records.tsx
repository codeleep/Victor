import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Table, Tag, Button, Space } from 'antd'
import { EyeOutlined, FileTextOutlined, SettingOutlined } from '@ant-design/icons'
import { interviewSessionApi } from '@/api'
import type { InterviewSessionVO } from '@/types'
import './Index.scss'

const canEnterSession = (status: InterviewSessionVO['status']) => {
  return status !== 'COMPLETED' && status !== 'ABANDONED'
}

export default function Records() {
  const navigate = useNavigate()
  const [sessions, setSessions] = useState<InterviewSessionVO[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadSessions()
  }, [])

  const loadSessions = async () => {
    setLoading(true)
    try {
      const data = await interviewSessionApi.list()
      setSessions(data)
    } catch (error) {
      console.error('Failed to load sessions:', error)
    } finally {
      setLoading(false)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '配置名称', dataIndex: 'configName', key: 'configName', render: (v: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const map: Record<string, { color: string; text: string }> = {
          IN_PROGRESS: { color: 'processing', text: '进行中' },
          PAUSED: { color: 'warning', text: '已暂停' },
          COMPLETED: { color: 'success', text: '已完成' },
          ABANDONED: { color: 'error', text: '已放弃' }
        }
        const info = map[status] || { color: 'default', text: status }
        return <Tag color={info.color}>{info.text}</Tag>
      }
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 160,
      render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-'
    },
    {
      title: '完成时间',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 160,
      render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-'
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
      width: 150,
      render: (_: unknown, record: InterviewSessionVO) => (
        <Space>
          {canEnterSession(record.status) ? (
            <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/interview/room/${record.id}`)}>
              {record.status === 'PAUSED' ? '继续' : '进入'}
            </Button>
          ) : record.status === 'COMPLETED' ? (
            <Button type="link" icon={<FileTextOutlined />} onClick={() => navigate(`/report/${record.id}`)}>
              报告
            </Button>
          ) : (
            <Button type="link" disabled>
              已放弃
            </Button>
          )}
        </Space>
      )
    }
  ]

  return (
    <div className="interview-index-page">
      <div className="page-header">
        <div className="header-left">
          <h1>面试记录</h1>
          <p>查看所有面试会话记录</p>
        </div>
        <Button icon={<SettingOutlined />} onClick={() => navigate('/interview/records')}>
          配置管理
        </Button>
      </div>
      <Card className="table-card">
        <Table
          columns={columns}
          dataSource={sessions}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>
    </div>
  )
}
