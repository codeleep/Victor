import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Select, Input, InputNumber, Switch, Steps, Tag, Space, Table, Modal, Tabs, App } from 'antd'
import MarkdownView from '@/components/MarkdownView'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { interviewConfigApi, jobApi, resumeApi, agentTeamApi, questionApi, experienceApi } from '@/api'
import type { JobVO, ResumeVO, AgentTeamVO, InterviewMode, RecallStrategy, QuestionVO, ExperienceVO, InterviewConfigRequest, RecallPreviewItem } from '@/types'
import './ConfigWizard.scss'

interface RoundConfig {
  name: string
  type: string
  questionCount: number
  difficulty: string
}

interface RecallItem {
  id: number
  type: 'QUESTION' | 'EXPERIENCE'
  title: string
  sourceId?: number
  sourceType?: string
  recallMethod?: string
  recallScore?: number
  sortOrder?: number
  reason?: string
}

export default function ConfigWizard() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [loading, setLoading] = useState(false)
  const [jobs, setJobs] = useState<JobVO[]>([])
  const [resumes, setResumes] = useState<ResumeVO[]>([])
  const [teams, setTeams] = useState<AgentTeamVO[]>([])
  const [recallItems, setRecallItems] = useState<RecallItem[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [modalTab, setModalTab] = useState<'QUESTION' | 'EXPERIENCE'>('QUESTION')
  const [allQuestions, setAllQuestions] = useState<QuestionVO[]>([])
  const [allExperiences, setAllExperiences] = useState<ExperienceVO[]>([])
  const [modalLoading, setModalLoading] = useState(false)
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set())
  const [recallTab, setRecallTab] = useState<'QUESTION' | 'EXPERIENCE'>('QUESTION')
  const [detailItem, setDetailItem] = useState<RecallItem | null>(null)
  const [recallLoading, setRecallLoading] = useState(false)
  const [recallSignature, setRecallSignature] = useState('')

  const [form, setForm] = useState({
    name: '',
    mode: 'TEXT' as InterviewMode,
    jobId: undefined as number | undefined,
    resumeId: undefined as number | undefined,
    rounds: [{ name: '技术面试', type: 'TECHNICAL', questionCount: 5, difficulty: 'MEDIUM' }] as RoundConfig[],
    durationMinutes: 45,
    hintEnabled: true,
    questionTeamId: undefined as number | undefined,
    interviewTeamId: undefined as number | undefined,
    evaluationTeamId: undefined as number | undefined,
    recallStrategy: 'AI' as RecallStrategy,
    maxRecallCount: 50,
  })

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    try {
      const [jobData, resumeData, teamData] = await Promise.all([
        jobApi.list({ page: 0, size: 100 }),
        resumeApi.list(),
        agentTeamApi.list()
      ])
      setJobs(jobData.records)
      setResumes(resumeData)
      setTeams(teamData)

      // 根据 team key 设置默认团队
      const questionTeam = teamData.find((t: AgentTeamVO) => t.key === 'system-team-question')
      const interviewTeam = teamData.find((t: AgentTeamVO) => t.key === 'system-team-interview')
      const evaluationTeam = teamData.find((t: AgentTeamVO) => t.key === 'system-team-evaluation')
      setForm(prev => ({
        ...prev,
        questionTeamId: prev.questionTeamId ?? questionTeam?.id,
        interviewTeamId: prev.interviewTeamId ?? interviewTeam?.id,
        evaluationTeamId: prev.evaluationTeamId ?? evaluationTeam?.id,
      }))
    } catch (error) {
      console.error('Failed to load data:', error)
    }
  }

  const loadModalData = async () => {
    setModalLoading(true)
    try {
      const [questionData, experienceData] = await Promise.all([
        questionApi.list({ page: 0, size: 200 }),
        experienceApi.list()
      ])
      setAllQuestions(questionData.records)
      setAllExperiences(experienceData)
    } catch (error) {
      console.error('Failed to load resource data:', error)
    } finally {
      setModalLoading(false)
    }
  }

  const buildTeamConfig = (): string[] => {
    const keys: string[] = []
    const qTeam = teams.find(t => t.id === form.questionTeamId)
    if (qTeam?.key) keys.push(qTeam.key)
    const iTeam = teams.find(t => t.id === form.interviewTeamId)
    if (iTeam?.key) keys.push(iTeam.key)
    const eTeam = teams.find(t => t.id === form.evaluationTeamId)
    if (eTeam?.key) keys.push(eTeam.key)
    return keys
  }

  const buildConfigRequest = (): InterviewConfigRequest => ({
    name: form.name || 'Recall Preview',
    mode: form.mode,
    jobId: form.jobId,
    resumeId: form.resumeId,
    rounds: form.rounds as unknown as Record<string, unknown>[],
    durationMinutes: form.durationMinutes,
    hintEnabled: form.hintEnabled,
    teamConfig: buildTeamConfig(),
    recallStrategy: form.recallStrategy,
    maxRecallCount: form.maxRecallCount,
  })

  const mapRecallPreviewItem = (item: RecallPreviewItem): RecallItem => ({
    id: item.sourceId ?? item.source_id ?? item.id,
    type: item.type,
    title: item.title,
    sourceId: item.sourceId ?? item.source_id ?? item.id,
    sourceType: item.sourceType ?? item.source_type ?? item.type,
    recallMethod: item.recallMethod ?? item.recall_method ?? 'AUTO_KEYWORD',
    recallScore: item.recallScore ?? item.recall_score,
    sortOrder: item.sortOrder ?? item.sort_order,
    reason: item.reason,
  })

  const runAutoRecall = async () => {
    const requestData = buildConfigRequest()
    const signature = JSON.stringify({
      jobId: requestData.jobId,
      resumeId: requestData.resumeId,
      rounds: requestData.rounds,
      recallStrategy: requestData.recallStrategy,
      maxRecallCount: requestData.maxRecallCount,
    })
    if (signature === recallSignature && recallItems.length > 0) return

    setRecallLoading(true)
    try {
      const items = await interviewConfigApi.recallPreview(requestData)
      setRecallItems(items.map(mapRecallPreviewItem))
      setRecallSignature(signature)
    } catch (error) {
      console.error('Failed to preview recall items:', error)
      message.error('召回资料失败，请稍后重试')
    } finally {
      setRecallLoading(false)
    }
  }

  useEffect(() => {
    if (step === 4) {
      loadModalData()
      runAutoRecall()
    }
  }, [step])

  const openAddModal = () => {
    setSelectedKeys(new Set())
    setModalOpen(true)
    loadModalData()
  }

  const handleModalConfirm = () => {
    const existingKeys = new Set(recallItems.map(i => `${i.type}-${i.id}`))
    const newItems: RecallItem[] = []

    if (modalTab === 'QUESTION') {
      allQuestions.forEach(q => {
        const key = `QUESTION-${q.id}`
        if (selectedKeys.has(key) && !existingKeys.has(key)) {
          newItems.push({ id: q.id, type: 'QUESTION', title: q.title, sourceId: q.id, sourceType: 'QUESTION', recallMethod: 'MANUAL' })
        }
      })
    } else {
      allExperiences.forEach(e => {
        const key = `EXPERIENCE-${e.id}`
        if (selectedKeys.has(key) && !existingKeys.has(key)) {
          newItems.push({ id: e.id, type: 'EXPERIENCE', title: e.title, sourceId: e.id, sourceType: 'EXPERIENCE', recallMethod: 'MANUAL' })
        }
      })
    }

    if (newItems.length === 0) {
      message.warning('未选择新项目或所选项目已存在')
      return
    }

    setRecallItems(prev => [...prev, ...newItems])
    setModalOpen(false)
    message.success(`已添加 ${newItems.length} 项`)
  }

  const removeRecallItem = (index: number) => {
    setRecallItems(prev => prev.filter((_, i) => i !== index))
  }

  const addRound = () => {
    setForm(prev => ({
      ...prev,
      rounds: [...prev.rounds, { name: '', type: 'TECHNICAL', questionCount: 3, difficulty: 'MEDIUM' }]
    }))
  }

  const removeRound = (index: number) => {
    setForm(prev => ({
      ...prev,
      rounds: prev.rounds.filter((_, i) => i !== index)
    }))
  }

  const updateRound = (index: number, field: keyof RoundConfig, value: any) => {
    setForm(prev => ({
      ...prev,
      rounds: prev.rounds.map((r, i) => i === index ? { ...r, [field]: value } : r)
    }))
  }

  const handleSubmit = async () => {
    if (!form.name.trim()) {
      message.error('请输入配置名称')
      return
    }
    setLoading(true)
    try {
      const configId = await interviewConfigApi.create({
        name: form.name,
        mode: form.mode,
        jobId: form.jobId,
        resumeId: form.resumeId,
        rounds: form.rounds as unknown as Record<string, unknown>[],
        durationMinutes: form.durationMinutes,
        hintEnabled: form.hintEnabled,
        teamConfig: buildTeamConfig(),
        recallStrategy: form.recallStrategy,
        maxRecallCount: form.maxRecallCount,
        recallItems: recallItems.map((item, index) => ({
          id: item.id,
          type: item.type,
          source_id: item.sourceId ?? item.id,
          source_type: item.sourceType ?? item.type,
          title: item.title,
          recall_method: item.recallMethod ?? 'MANUAL',
          recall_score: item.recallScore,
          sort_order: index + 1,
          reason: item.reason,
        })) as Record<string, unknown>[],
      })
      await interviewConfigApi.publish(configId)
      message.success('已提交题目生成，配置 READY 后可开始面试')
      navigate('/interview')
    } catch (error) {
      console.error('Failed to create config:', error)
    } finally {
      setLoading(false)
    }
  }

  const stepsItems = [
    { title: '基础设置' },
    { title: '进阶设置' },
    { title: '高级设置' },
    { title: '召回资料' },
    { title: '确认创建' },
  ]

  const stepTitles = ['', '基础设置', '进阶设置', '高级设置', '召回资料编辑', '确认并创建']

  const recallColumns = [
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '匹配度',
      dataIndex: 'recallScore',
      width: 90,
      render: (v?: number) => typeof v === 'number' ? `${Math.round(v * 100)}%` : '-',
    },
    {
      title: '',
      width: 100,
      render: (_: any, record: RecallItem) => (
        <Space size={0}>
          <Button type="text" size="small" onClick={() => setDetailItem(record)}>查看</Button>
          <Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={() => removeRecallItem(recallItems.indexOf(record))} />
        </Space>
      ),
    },
  ]

  const existingKeys = new Set(recallItems.map(i => `${i.type}-${i.id}`))

  return (
    <div className="config-wizard">
      <div className="wizard-sidebar">
        <Steps
          current={step - 1}
          direction="vertical"
          size="small"
          items={stepsItems}
          className="wizard-steps"
        />
      </div>

      <div className="wizard-content">
        <div className="content-header">
          <h2>{stepTitles[step]}</h2>
        </div>

        <div className="content-body">
          {step === 1 && (
            <div className="setup-form">
              <div className="form-group">
                <label className="form-label">面试模式</label>
                <div className="mode-selector">
                  <div
                    className={`mode-option ${form.mode === 'VOICE' ? 'selected' : ''}`}
                    onClick={() => setForm(prev => ({ ...prev, mode: 'VOICE' }))}
                  >
                    <div className="mode-icon">🎙️</div>
                    <div className="mode-name">语音面试</div>
                    <div className="mode-desc">实时语音对话</div>
                  </div>
                  <div
                    className={`mode-option ${form.mode === 'TEXT' ? 'selected' : ''}`}
                    onClick={() => setForm(prev => ({ ...prev, mode: 'TEXT' }))}
                  >
                    <div className="mode-icon">💬</div>
                    <div className="mode-name">文字面试</div>
                    <div className="mode-desc">文字交流，支持代码</div>
                  </div>
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">目标岗位</label>
                <Select
                  value={form.jobId}
                  onChange={v => setForm(prev => ({ ...prev, jobId: v }))}
                  placeholder="选择岗位"
                  style={{ width: '100%' }}
                  allowClear
                  options={jobs.map(j => ({ label: j.name, value: j.id }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">关联简历</label>
                <Select
                  value={form.resumeId}
                  onChange={v => setForm(prev => ({ ...prev, resumeId: v }))}
                  placeholder="选择简历（可选）"
                  style={{ width: '100%' }}
                  allowClear
                  options={resumes.map(r => ({ label: r.name, value: r.id }))}
                />
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="setup-form">
              <div className="form-group">
                <label className="form-label">面试轮次</label>
                {form.rounds.map((round, index) => (
                  <div key={index} className="round-item">
                    <Input
                      value={round.name}
                      onChange={e => updateRound(index, 'name', e.target.value)}
                      placeholder="轮次名称"
                      style={{ flex: 1 }}
                    />
                    <Select
                      value={round.type}
                      onChange={v => updateRound(index, 'type', v)}
                      style={{ width: 110 }}
                      options={[
                        { label: '技术面试', value: 'TECHNICAL' },
                        { label: '行为面试', value: 'BEHAVIORAL' },
                        { label: '综合面试', value: 'COMPREHENSIVE' },
                      ]}
                    />
                    <InputNumber
                      value={round.questionCount}
                      onChange={v => updateRound(index, 'questionCount', v || 1)}
                      min={1}
                      max={20}
                      addonAfter="题"
                      style={{ width: 90 }}
                    />
                    <Select
                      value={round.difficulty}
                      onChange={v => updateRound(index, 'difficulty', v)}
                      style={{ width: 90 }}
                      options={[
                        { label: '简单', value: 'EASY' },
                        { label: '中等', value: 'MEDIUM' },
                        { label: '困难', value: 'HARD' },
                      ]}
                    />
                    {form.rounds.length > 1 && (
                      <Button type="text" danger icon={<DeleteOutlined />} onClick={() => removeRound(index)} />
                    )}
                  </div>
                ))}
                <Button type="dashed" icon={<PlusOutlined />} onClick={addRound} block size="small">添加轮次</Button>
              </div>
              <div className="form-row">
                <div className="form-group" style={{ flex: 1 }}>
                  <label className="form-label">面试时长</label>
                  <div className="duration-options">
                    {[30, 45, 60, 90].map(d => (
                      <Button
                        key={d}
                        size="small"
                        type={form.durationMinutes === d ? 'primary' : 'default'}
                        onClick={() => setForm(prev => ({ ...prev, durationMinutes: d }))}
                      >
                        {d}分钟
                      </Button>
                    ))}
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">提示功能</label>
                  <Switch size="small" checked={form.hintEnabled} onChange={v => setForm(prev => ({ ...prev, hintEnabled: v }))} />
                </div>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="setup-form">
              <div className="form-group">
                <label className="form-label">出题团队</label>
                <Select
                  value={form.questionTeamId}
                  onChange={v => setForm(prev => ({ ...prev, questionTeamId: v }))}
                  placeholder="选择出题团队"
                  style={{ width: '100%' }}
                  allowClear
                  options={teams.map(t => ({ label: t.name, value: t.id }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">面试团队</label>
                <Select
                  value={form.interviewTeamId}
                  onChange={v => setForm(prev => ({ ...prev, interviewTeamId: v }))}
                  placeholder="选择面试团队"
                  style={{ width: '100%' }}
                  allowClear
                  options={teams.map(t => ({ label: t.name, value: t.id }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">评估团队</label>
                <Select
                  value={form.evaluationTeamId}
                  onChange={v => setForm(prev => ({ ...prev, evaluationTeamId: v }))}
                  placeholder="选择评估团队"
                  style={{ width: '100%' }}
                  allowClear
                  options={teams.map(t => ({ label: t.name, value: t.id }))}
                />
              </div>
              <div className="form-row">
                <div className="form-group" style={{ flex: 1 }}>
                  <label className="form-label">召回策略</label>
                  <Select
                    value={form.recallStrategy}
                    onChange={v => setForm(prev => ({ ...prev, recallStrategy: v }))}
                    style={{ width: '100%' }}
                    options={[
                      { label: '向量检索', value: 'VECTOR' },
                      { label: '关键词检索', value: 'KEYWORD' },
                      { label: '混合检索', value: 'HYBRID' },
                      { label: 'AI智能召回', value: 'AI' },
                    ]}
                  />
                </div>
                <div className="form-group" style={{ width: 150 }}>
                  <label className="form-label">最大召回数</label>
                  <InputNumber
                    value={form.maxRecallCount}
                    onChange={v => setForm(prev => ({ ...prev, maxRecallCount: v || 20 }))}
                    min={5}
                    max={100}
                    style={{ width: '100%' }}
                  />
                </div>
              </div>
            </div>
          )}

          {step === 4 && (
            <div className="setup-form" style={{ maxWidth: 'none' }}>
              <div className="recall-header">
                <span>{recallLoading ? '正在召回资料...' : '已召回'} <strong>{recallItems.length}</strong> 项资料</span>
                <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openAddModal}>添加资料</Button>
              </div>
              <Tabs
                activeKey={recallTab}
                onChange={v => setRecallTab(v as any)}
                items={[
                  {
                    key: 'QUESTION',
                    label: `题目 (${recallItems.filter(i => i.type === 'QUESTION').length})`,
                    children: (
                      <Table
                        dataSource={recallItems.filter(i => i.type === 'QUESTION')}
                        columns={recallColumns}
                        loading={recallLoading}
                        rowKey={r => `QUESTION-${r.id}`}
                        size="small"
                        pagination={{ pageSize: 10, showSizeChanger: false, showTotal: t => `共 ${t} 条` }}
                        locale={{ emptyText: '暂无题目，点击"添加资料"选择' }}
                      />
                    ),
                  },
                  {
                    key: 'EXPERIENCE',
                    label: `经历 (${recallItems.filter(i => i.type === 'EXPERIENCE').length})`,
                    children: (
                      <Table
                        dataSource={recallItems.filter(i => i.type === 'EXPERIENCE')}
                        columns={recallColumns}
                        loading={recallLoading}
                        rowKey={r => `EXPERIENCE-${r.id}`}
                        size="small"
                        pagination={{ pageSize: 10, showSizeChanger: false, showTotal: t => `共 ${t} 条` }}
                        locale={{ emptyText: '暂无经历，点击"添加资料"选择' }}
                      />
                    ),
                  },
                ]}
              />
            </div>
          )}

          {step === 5 && (
            <div className="setup-form">
              <div className="form-group">
                <label className="form-label">配置名称</label>
                <Input
                  value={form.name}
                  onChange={e => setForm(prev => ({ ...prev, name: e.target.value }))}
                  placeholder="给这次面试配置起个名字"
                  size="large"
                />
              </div>
              <div className="summary-section">
                <h4>配置摘要</h4>
                <div className="summary-grid">
                  <div className="summary-item">
                    <span className="summary-label">面试模式</span>
                    <span className="summary-value">{form.mode === 'VOICE' ? '语音' : '文字'}</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">目标岗位</span>
                    <span className="summary-value">{jobs.find(j => j.id === form.jobId)?.name || '未选择'}</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">面试轮次</span>
                    <span className="summary-value">{form.rounds.length} 轮</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">面试时长</span>
                    <span className="summary-value">{form.durationMinutes} 分钟</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">出题团队</span>
                    <span className="summary-value">{teams.find(t => t.id === form.questionTeamId)?.name || '未选择'}</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">面试团队</span>
                    <span className="summary-value">{teams.find(t => t.id === form.interviewTeamId)?.name || '未选择'}</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">评估团队</span>
                    <span className="summary-value">{teams.find(t => t.id === form.evaluationTeamId)?.name || '未选择'}</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">召回资料</span>
                    <span className="summary-value">{recallItems.length} 项</span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="content-actions">
          {step > 1 && <Button size="small" onClick={() => setStep(step - 1)}>上一步</Button>}
          <div style={{ flex: 1 }} />
          {step < 5 && <Button type="primary" size="small" onClick={() => setStep(step + 1)} loading={step === 4 && recallLoading} disabled={step === 4 && recallLoading}>下一步</Button>}
          {step === 5 && <Button type="primary" size="small" onClick={handleSubmit} loading={loading}>创建并生成题目</Button>}
        </div>
      </div>

      <Modal
        title="添加召回资料"
        open={modalOpen}
        onOk={handleModalConfirm}
        onCancel={() => setModalOpen(false)}
        width={720}
        okText={`添加 (${selectedKeys.size})`}
        cancelText="取消"
        destroyOnHidden
      >
        <Tabs
          activeKey={modalTab}
          onChange={v => { setModalTab(v as any); setSelectedKeys(new Set()) }}
          items={[
            {
              key: 'QUESTION',
              label: '题目',
              children: (
                <Table
                  dataSource={allQuestions}
                  loading={modalLoading}
                  rowKey={r => `QUESTION-${r.id}`}
                  size="small"
                  pagination={{ pageSize: 10, showSizeChanger: false, showTotal: t => `共 ${t} 条` }}
                  rowSelection={{
                    selectedRowKeys: Array.from(selectedKeys).filter(k => k.startsWith('QUESTION-')),
                    onChange: keys => {
                      setSelectedKeys(prev => {
                        const next = new Set(Array.from(prev).filter(k => !k.startsWith('QUESTION-')))
                        keys.forEach(k => next.add(k as string))
                        return next
                      })
                    },
                    getCheckboxProps: r => ({ disabled: existingKeys.has(`QUESTION-${r.id}`) }),
                  }}
                  columns={[
                    { title: '标题', dataIndex: 'title', ellipsis: true },
                    { title: '类型', dataIndex: 'type', width: 100, render: (v: string) => ({ TECHNICAL: '技术', BEHAVIORAL: '行为', SHORT_ANSWER: '简答', MULTIPLE_CHOICE: '选择', CODING: '编程' } as Record<string, string>)[v] || v },
                    { title: '难度', dataIndex: 'difficulty', width: 80, render: (v: string) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' } as Record<string, string>)[v] || v },
                    { title: '标签', dataIndex: 'tags', width: 150, render: (tags: string[]) => tags?.slice(0, 2).map(t => <Tag key={t}>{t}</Tag>) },
                  ]}
                />
              ),
            },
            {
              key: 'EXPERIENCE',
              label: '经历',
              children: (
                <Table
                  dataSource={allExperiences}
                  loading={modalLoading}
                  rowKey={r => `EXPERIENCE-${r.id}`}
                  size="small"
                  pagination={{ pageSize: 10, showSizeChanger: false, showTotal: t => `共 ${t} 条` }}
                  rowSelection={{
                    selectedRowKeys: Array.from(selectedKeys).filter(k => k.startsWith('EXPERIENCE-')),
                    onChange: keys => {
                      setSelectedKeys(prev => {
                        const next = new Set(Array.from(prev).filter(k => !k.startsWith('EXPERIENCE-')))
                        keys.forEach(k => next.add(k as string))
                        return next
                      })
                    },
                    getCheckboxProps: r => ({ disabled: existingKeys.has(`EXPERIENCE-${r.id}`) }),
                  }}
                  columns={[
                    { title: '标题', dataIndex: 'title', ellipsis: true },
                    { title: '类型', dataIndex: 'type', width: 100, render: (v: string) => ({ PROJECT: '项目', WORK: '工作', EDUCATION: '教育', OTHER: '其他' } as Record<string, string>)[v] || v },
                    { title: '技能', dataIndex: 'skills', width: 180, render: (skills: string[]) => skills?.slice(0, 3).map(s => <Tag key={s}>{s}</Tag>) },
                    { title: '时间', width: 160, render: (_, r) => r.startDate ? `${r.startDate} ~ ${r.endDate || '至今'}` : '-' },
                  ]}
                />
              ),
            },
          ]}
        />
      </Modal>

      <Modal
        title="查看详情"
        open={!!detailItem}
        onCancel={() => setDetailItem(null)}
        footer={<Button onClick={() => setDetailItem(null)}>关闭</Button>}
        width="60%"
      >
        {detailItem && (() => {
          if (detailItem.type === 'QUESTION') {
            const q = allQuestions.find(x => x.id === detailItem.id)
            if (!q) return <div>未找到题目信息</div>
            return (
              <div>
                <p><strong>标题：</strong>{q.title}</p>
                <p><strong>类型：</strong>{({ TECHNICAL: '技术', BEHAVIORAL: '行为', SHORT_ANSWER: '简答', MULTIPLE_CHOICE: '选择', CODING: '编程' } as Record<string, string>)[q.type] || q.type}</p>
                <p><strong>难度：</strong>{({ EASY: '简单', MEDIUM: '中等', HARD: '困难' } as Record<string, string>)[q.difficulty] || q.difficulty}</p>
                <p><strong>标签：</strong>{q.tags?.map(t => <Tag key={t}>{t}</Tag>) || '-'}</p>
                {q.description && (<div><strong>描述：</strong><MarkdownView source={q.description} /></div>)}
                {q.referenceAnswer && (<div><strong>参考答案：</strong><MarkdownView source={q.referenceAnswer} /></div>)}
              </div>
            )
          } else {
            const e = allExperiences.find(x => x.id === detailItem.id)
            if (!e) return <div>未找到经历信息</div>
            return (
              <div>
                <p><strong>标题：</strong>{e.title}</p>
                <p><strong>类型：</strong>{({ PROJECT: '项目', WORK: '工作', EDUCATION: '教育', OTHER: '其他' } as Record<string, string>)[e.type] || e.type}</p>
                <p><strong>时间：</strong>{e.startDate ? `${e.startDate} ~ ${e.endDate || '至今'}` : '-'}</p>
                <p><strong>技能：</strong>{e.skills?.map(s => <Tag key={s}>{s}</Tag>) || '-'}</p>
                {e.description && (<div><strong>描述：</strong><MarkdownView source={e.description} /></div>)}
              </div>
            )
          }
        })()}
      </Modal>
    </div>
  )
}
