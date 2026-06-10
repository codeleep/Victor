import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Button, Space, Spin, message } from 'antd'
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import { interviewSessionApi, reportApi } from '@/api'
import type { InterviewReportVO } from '@/types'
import './Report.scss'

export default function ReportDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [report, setReport] = useState<InterviewReportVO | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadReport()
  }, [id])

  const loadReport = async () => {
    if (!id) return
    setLoading(true)
    try {
      const data = await interviewSessionApi.getReport(Number(id))
      setReport(data)
    } catch (error) {
      console.error('Failed to load report:', error)
      message.error('获取报告失败')
    } finally {
      setLoading(false)
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

  if (!report) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 0' }}>
        <p>报告不存在或尚未生成</p>
        <Button onClick={() => navigate('/report')}>返回报告列表</Button>
      </div>
    )
  }

  return (
    <div className="report-detail">
      <div className="page-header">
        <div className="header-left">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/report')} style={{ marginBottom: 8 }}>返回</Button>
          <h1>面试报告</h1>
          <p>面试评估详情</p>
        </div>
      </div>

      <div style={{ marginBottom: 16 }}>
        <Space>
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
