import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Table, Button } from 'antd'
import { FileTextOutlined } from '@ant-design/icons'
import { interviewSessionApi } from '@/api'
import type { InterviewSessionVO } from '@/types'
import './Report.scss'

export default function ReportIndex() {
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
      setSessions(data.filter(s => s.status === 'COMPLETED'))
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
      title: '完成时间',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 180,
      render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-'
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: unknown, record: InterviewSessionVO) => (
        <Button type="link" icon={<FileTextOutlined />} onClick={() => navigate(`/report/${record.id}`)}>
          查看报告
        </Button>
      )
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
