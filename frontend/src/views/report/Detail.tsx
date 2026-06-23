import { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Button, Space, Spin, message, Result, Collapse, Tag } from 'antd'
import { ArrowLeftOutlined, DownloadOutlined, ReloadOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import { reportApi } from '@/api'
import type { InterviewReportVO } from '@/types'
import './Report.scss'

const isReportGenerating = (s?: string) => s === 'PENDING' || s === 'EVALUATING'

export default function ReportDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [report, setReport] = useState<InterviewReportVO | null>(null)
  const [loading, setLoading] = useState(true)
  const [regenerating, setRegenerating] = useState(false)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    loadReport()
    return () => stopPolling()
  }, [id])

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  const startPolling = () => {
    stopPolling()
    pollRef.current = setInterval(async () => {
      if (!id) return
      try {
        const data = await reportApi.getBySessionId(Number(id))
        setReport(data)
        if (data && !isReportGenerating(data.status)) {
          stopPolling()
        }
      } catch (error) {
        console.error('Failed to poll report:', error)
      }
    }, 4000)
  }

  const loadReport = async () => {
    if (!id) return
    setLoading(true)
    stopPolling()
    try {
      const data = await reportApi.getBySessionId(Number(id))
      setReport(data)
      if (data && isReportGenerating(data.status)) {
        startPolling()
      }
    } catch (error) {
      console.error('Failed to load report:', error)
      message.error('获取报告失败')
    } finally {
      setLoading(false)
    }
  }

  const handleRegenerate = async () => {
    if (!id) return
    try {
      setRegenerating(true)
      await reportApi.regenerateBySessionId(Number(id))
      message.success('已重新提交评估')
      const data = await reportApi.getBySessionId(Number(id))
      setReport(data)
      if (data && isReportGenerating(data.status)) {
        startPolling()
      }
    } catch (error) {
      console.error('Failed to regenerate report:', error)
    } finally {
      setRegenerating(false)
    }
  }

  const handleExportPdf = async () => {
    if (!id) return
    try {
      const blob = await reportApi.exportPdf(Number(id))
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `interview-report-${id}.pdf`
      a.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Failed to export PDF:', error)
      message.error('导出 PDF 失败')
    }
  }

  const handleExportMarkdown = async () => {
    if (!id) return
    try {
      const md = await reportApi.exportMarkdown(Number(id))
      const blob = new Blob([md], { type: 'text/markdown' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `interview-report-${id}.md`
      a.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Failed to export Markdown:', error)
      message.error('导出 Markdown 失败')
    }
  }

  const getScoreColor = (score: number) => {
    if (score >= 80) return '#4A9E6E'
    if (score >= 60) return '#D4A843'
    return '#C45A4A'
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
        <Spin size="large" />
      </div>
    )
  }

  // 报告仍在生成中:禁止查看,展示生成中状态
  if (report && isReportGenerating(report.status)) {
    return (
      <div className="report-detail">
        <div className="page-header">
          <div className="header-left">
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/interview')} style={{ marginBottom: 8 }}>返回</Button>
            <h1>面试报告</h1>
            <p>面试评估详情</p>
          </div>
        </div>
        <Result
          icon={<Spin size="large" />}
          status="info"
          title="报告生成中"
          subTitle="评估团队正在分析面试记录，请稍候片刻。完成后将自动展示报告。"
        />
      </div>
    )
  }

  // 报告生成失败:展示失败原因并提供重新生成入口
  if (report && report.status === 'FAILED') {
    return (
      <div className="report-detail">
        <div className="page-header">
          <div className="header-left">
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/interview')} style={{ marginBottom: 8 }}>返回</Button>
            <h1>面试报告</h1>
            <p>面试评估详情</p>
          </div>
        </div>
        <Result
          status="error"
          title="报告生成失败"
          subTitle={report.evaluationError || '评估过程中出现异常，请重试。'}
          extra={[
            <Button
              key="retry"
              type="primary"
              icon={<ReloadOutlined />}
              loading={regenerating}
              onClick={handleRegenerate}
            >
              重新生成报告
            </Button>,
            <Button key="back" onClick={() => navigate('/interview')}>返回面试记录</Button>,
          ]}
        />
      </div>
    )
  }

  if (!report) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 0' }}>
        <p>报告不存在或尚未生成</p>
        <Button onClick={() => navigate('/interview')}>返回面试记录</Button>
      </div>
    )
  }

  return (
    <div className="report-detail">
      <div className="page-header">
        <div className="header-left">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/interview')} style={{ marginBottom: 8 }}>返回</Button>
          <h1>面试报告</h1>
          <p>面试评估详情</p>
        </div>
      </div>

      <div style={{ marginBottom: 16 }}>
        <Space>
          <Button icon={<ReloadOutlined />} loading={regenerating} onClick={handleRegenerate}>重新生成报告</Button>
          <Button icon={<DownloadOutlined />} onClick={handleExportPdf}>导出 PDF</Button>
          <Button icon={<DownloadOutlined />} onClick={handleExportMarkdown}>导出 Markdown</Button>
        </Space>
      </div>

      <div className="report-overview">
        <Card className="score-card">
          <div className="score-ring" style={{ color: getScoreColor(report.overallScore || 0) }}>
            {report.overallScore || '-'}
          </div>
          <div style={{ fontSize: 16, fontWeight: 600 }}>综合评分</div>
        </Card>

        <Card className="summary-card" title="总结">
          <MDEditor.Markdown source={report.summary || '暂无总结'} />
        </Card>
      </div>

      {report.perQuestionEvaluation && report.perQuestionEvaluation.length > 0 && (
        <Card className="section-card per-question-card" title="逐题点评">
          <Collapse
            ghost
            className="per-question-collapse"
            defaultActiveKey={report.perQuestionEvaluation.map((_, i) => String(i + 1))}
            items={report.perQuestionEvaluation.map((q, i) => {
              const idx = q.questionIndex ?? i + 1
              const score = typeof q.score === 'number' ? q.score : undefined
              return {
                key: String(idx),
                label: (
                  <div className="pq-header">
                    <span className="pq-title">
                      题目 {idx}
                      {q.questionText ? `：${q.questionText}` : ''}
                    </span>
                    {score !== undefined && (
                      <span className="pq-score" style={{ color: getScoreColor(score) }}>{score}</span>
                    )}
                  </div>
                ),
                children: (
                  <div className="pq-body">
                    {q.interactions && q.interactions.length > 0 && (
                      <div className="pq-interactions">
                        {q.interactions.map((it, j) => (
                          <div key={j} className={`pq-interaction pq-${it.speaker === 'AI' ? 'ai' : 'user'}`}>
                            <span className="pq-role">
                              {it.role}
                              {it.isFollowup && <Tag color="orange" className="pq-followup-tag">追问</Tag>}
                            </span>
                            <span className="pq-content">{it.content}</span>
                          </div>
                        ))}
                      </div>
                    )}
                    {q.feedback ? (
                      <div className="pq-feedback">
                        <MDEditor.Markdown source={q.feedback} />
                      </div>
                    ) : (
                      <div className="pq-feedback-empty">暂无点评</div>
                    )}
                  </div>
                ),
              }
            })}
          />
        </Card>
      )}

      <Card className="section-card" title="优势">
        <MDEditor.Markdown source={report.strengths || '暂无数据'} />
      </Card>

      <Card className="section-card" title="不足">
        <MDEditor.Markdown source={report.weaknesses || '暂无数据'} />
      </Card>

      <Card className="section-card" title="改进建议">
        <MDEditor.Markdown source={report.suggestions || '暂无数据'} />
      </Card>

      {report.dimensionScores && (
        <Card className="section-card" title="维度评分">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16 }}>
            {Object.entries(report.dimensionScores as Record<string, number>).map(([key, value]) => (
              <div key={key} style={{ padding: 12, background: '#F5F3EC', borderRadius: 8 }}>
                <div style={{ fontSize: 12, color: '#5A5A58', marginBottom: 4 }}>{key}</div>
                <div style={{ fontSize: 20, fontWeight: 600, color: getScoreColor(value) }}>{value}</div>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}