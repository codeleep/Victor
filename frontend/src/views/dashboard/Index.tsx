import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Button, Statistic, Row, Col, Alert, Space } from 'antd'
import {
  VideoCameraOutlined,
  FileTextOutlined,
  BarChartOutlined,
  PlusOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined
} from '@ant-design/icons'
import { interviewConfigApi, interviewSessionApi, systemApi } from '@/api'
import type { InterviewConfigVO, InterviewSessionVO } from '@/types'
import './Index.scss'

export default function Dashboard() {
  const navigate = useNavigate()
  const [configs, setConfigs] = useState<InterviewConfigVO[]>([])
  const [sessions, setSessions] = useState<InterviewSessionVO[]>([])
  const [loading, setLoading] = useState(true)
  const [initDone, setInitDone] = useState<boolean | null>(null)
  const [initing, setIniting] = useState(false)

  useEffect(() => {
    loadData()
    checkInit()
  }, [])

  const loadData = async () => {
    setLoading(true)
    try {
      const [configData, sessionData] = await Promise.all([
        interviewConfigApi.list(),
        interviewSessionApi.list()
      ])
      setConfigs(configData)
      setSessions(sessionData)
    } catch (error) {
      console.error('Failed to load dashboard data:', error)
    } finally {
      setLoading(false)
    }
  }

  const checkInit = async () => {
    try {
      const status = await systemApi.initStatus()
      setInitDone(status)
    } catch (e) {
      console.error('Failed to check init status:', e)
    }
  }

  const handleInit = async () => {
    setIniting(true)
    try {
      const result = await systemApi.init()
      if (result.skipped) {
        setInitDone(true)
      } else {
        const parts: string[] = []
        if (result.llmCreated) parts.push(`${result.llmCreated} 个LLM配置`)
        if (result.agentCreated) parts.push(`${result.agentCreated} 个Agent`)
        if (result.teamCreated) parts.push(`${result.teamCreated} 个团队`)
        setInitDone(true)
      }
    } catch (e) {
      console.error('Init failed:', e)
    } finally {
      setIniting(false)
    }
  }

  const activeSessions = sessions.filter(s => s.status === 'IN_PROGRESS')
  const completedSessions = sessions.filter(s => s.status === 'COMPLETED')

  return (
    <div className="dashboard-page">
      <div className="page-header">
        <div className="header-left">
          <h1>仪表盘</h1>
          <p>欢迎使用 Victor AI 面试助手</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/interview/config')}>
          新建面试
        </Button>
      </div>

      {initDone === false && (
        <Alert
          type="info"
          showIcon
          icon={<ThunderboltOutlined />}
          message="系统初始化"
          description="首次使用需要初始化系统 Agent、团队和 LLM 配置。初始化后可在「AI 配置」页面修改。"
          action={
            <Button type="primary" size="small" icon={<ThunderboltOutlined />} onClick={handleInit} loading={initing}>
              一键初始化
            </Button>
          }
          style={{ marginBottom: 24 }}
        />
      )}

      {initDone === true && (
        <Alert
          type="success"
          showIcon
          icon={<CheckCircleOutlined />}
          message="系统已初始化"
          description={
            <Space>
              <span>Agent、团队和 LLM 配置已就绪。</span>
              <Button type="link" size="small" onClick={() => navigate('/settings/ai')}>前往 AI 配置</Button>
            </Space>
          }
          style={{ marginBottom: 24 }}
          closable
        />
      )}

      <Row gutter={[16, 16]} className="stats-row">
        <Col xs={24} sm={8}>
          <Card className="stat-card">
            <Statistic
              title="面试配置"
              value={configs.length}
              prefix={<FileTextOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card className="stat-card">
            <Statistic
              title="进行中面试"
              value={activeSessions.length}
              prefix={<VideoCameraOutlined />}
              valueStyle={{ color: '#4A9E6E' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card className="stat-card">
            <Statistic
              title="已完成面试"
              value={completedSessions.length}
              prefix={<BarChartOutlined />}
              valueStyle={{ color: '#D97757' }}
            />
          </Card>
        </Col>
      </Row>

      <div className="dashboard-sections">
        <Card title="最近面试" className="section-card" loading={loading}>
          {sessions.length === 0 ? (
            <div className="empty-state">
              <p>暂无面试记录</p>
              <Button type="primary" onClick={() => navigate('/interview/config')}>
                开始第一次面试
              </Button>
            </div>
          ) : (
            <div className="session-list">
              {sessions.slice(0, 5).map(session => (
                <div key={session.id} className="session-item" onClick={() => {
                  if (session.status === 'COMPLETED') {
                    navigate(`/report/${session.id}`)
                  } else {
                    navigate(`/interview/room/${session.id}`)
                  }
                }}>
                  <div className="session-info">
                    <span className="session-name">{session.configName || `面试 #${session.id}`}</span>
                    <span className={`session-status ${session.status.toLowerCase()}`}>
                      {session.status === 'IN_PROGRESS' ? '进行中' :
                       session.status === 'COMPLETED' ? '已完成' :
                       session.status === 'PAUSED' ? '已暂停' : '已放弃'}
                    </span>
                  </div>
                  <span className="session-time">
                    {new Date(session.createdAt).toLocaleString('zh-CN')}
                  </span>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="快速开始" className="section-card">
          <div className="quick-actions">
            <div className="action-item" onClick={() => navigate('/interview/config')}>
              <VideoCameraOutlined />
              <span>新建面试</span>
            </div>
            <div className="action-item" onClick={() => navigate('/resource/questions')}>
              <FileTextOutlined />
              <span>题库管理</span>
            </div>
            <div className="action-item" onClick={() => navigate('/report')}>
              <BarChartOutlined />
              <span>查看报告</span>
            </div>
          </div>
        </Card>
      </div>
    </div>
  )
}
