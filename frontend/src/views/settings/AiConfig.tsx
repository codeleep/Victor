import { useEffect, useState } from 'react'
import { Card, Table, Button, Tag, Space, Tabs, Modal, Form, Input, Select, InputNumber, Switch, App, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, RocketOutlined } from '@ant-design/icons'
import { agentApi, agentTeamApi, agentLlmConfigApi, systemApi } from '@/api'
import { useMetadataStore } from '@/stores/metadata'
import type { AgentVO, AgentRequest, AgentTeamVO, TeamRequest, TeamMemberDTO, AgentLlmConfigVO, AgentLlmConfigRequest } from '@/types'
import './Settings.scss'

function AgentTeamPanel() {
  const { message } = App.useApp()
  const [data, setData] = useState<AgentTeamVO[]>([])
  const [agents, setAgents] = useState<AgentVO[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingSystem, setEditingSystem] = useState(false)
  const [selectedAgentIds, setSelectedAgentIds] = useState<number[]>([])
  const [mainAgentId, setMainAgentId] = useState<number | undefined>(undefined)
  const [form] = Form.useForm()

  useEffect(() => { fetchData(); loadAgents() }, [])

  const fetchData = async () => {
    setLoading(true)
    try { setData(await agentTeamApi.list()) } catch (e) { console.error(e) } finally { setLoading(false) }
  }

  const loadAgents = async () => {
    try { setAgents(await agentApi.list()) } catch (e) { console.error(e) }
  }

  const handleAdd = () => { setEditingId(null); setEditingSystem(false); form.resetFields(); setSelectedAgentIds([]); setMainAgentId(undefined); setModalOpen(true) }
  const handleEdit = (row: AgentTeamVO) => {
    setEditingId(row.id)
    setEditingSystem(!!row.isSystem)
    form.setFieldsValue({ name: row.name, description: row.description, executionMode: row.executionMode, mainAgentId: row.mainAgentId })
    const memberIds = (row.members || []).map((m: Record<string, unknown>) => m.agentId as number)
    setSelectedAgentIds(memberIds)
    setMainAgentId(row.mainAgentId)
    setModalOpen(true)
  }
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const members: TeamMemberDTO[] = selectedAgentIds.map((agentId, i) => ({ agentId, priority: i }))
      let payload: TeamRequest
      if (editingSystem) {
        // 系统团队只提交 description、mainAgentId 和 members
        payload = { name: values.name, description: values.description, mainAgentId, members }
      } else {
        payload = { ...values, mainAgentId, members }
      }
      if (editingId) { await agentTeamApi.update(editingId, payload); message.success('更新成功') }
      else { await agentTeamApi.create(payload); message.success('创建成功') }
      setModalOpen(false); fetchData()
    } catch (e) { console.error(e) }
  }
  const handleDelete = async (id: number) => {
    try { await agentTeamApi.delete(id); message.success('删除成功'); fetchData() } catch (e) { console.error(e) }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '描述', dataIndex: 'description', key: 'description', render: (v: string) => v || '-' },
    { title: '主Agent', dataIndex: 'mainAgentName', key: 'mainAgentName', width: 120, render: (v: string) => v || '-' },
    { title: '执行模式', dataIndex: 'executionMode', width: 100, render: (v: string) => ({ PARALLEL: '并行', SEQUENTIAL: '串行' }[v] || v || '-') },
    { title: '成员数', width: 80, render: (_: unknown, r: AgentTeamVO) => r.members?.length || 0 },
    { title: '系统', dataIndex: 'isSystem', key: 'isSystem', width: 80, render: (v: boolean) => v ? <Tag color="blue">系统</Tag> : <Tag>自定义</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160, render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
    {
      title: '操作', key: 'action', width: 150,
      render: (_: unknown, record: AgentTeamVO) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)} disabled={record.isSystem}>
            <Button type="link" danger icon={<DeleteOutlined />} disabled={record.isSystem}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增团队</Button>
      </div>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={false} />
      <Modal title={editingId ? '编辑团队' : '新增团队'} open={modalOpen} onOk={handleSubmit} onCancel={() => setModalOpen(false)} width={640}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: !editingSystem }]}>
            <Input placeholder="请输入团队名称" disabled={editingSystem} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="请输入描述" />
          </Form.Item>
          {!editingSystem && (
            <Form.Item name="executionMode" label="执行模式">
              <Select
                placeholder="选择执行模式"
                options={[{ label: '并行', value: 'PARALLEL' }, { label: '串行', value: 'SEQUENTIAL' }]}
              />
            </Form.Item>
          )}
          <Form.Item label="主 Agent">
            <Select
              value={mainAgentId}
              onChange={(v) => { setMainAgentId(v); setSelectedAgentIds(prev => prev.filter(id => id !== v)) }}
              placeholder="选择主 Agent"
              allowClear
              optionFilterProp="label"
              options={agents.map(a => ({ label: `${a.name}${a.type ? ` (${a.type})` : ''}`, value: a.id }))}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item label="子 Agent（团队成员）">
            <Select
              mode="multiple"
              value={selectedAgentIds}
              onChange={setSelectedAgentIds}
              placeholder="选择子 Agent 加入团队"
              optionFilterProp="label"
              options={agents.filter(a => a.id !== mainAgentId).map(a => ({ label: `${a.name}${a.type ? ` (${a.type})` : ''}`, value: a.id }))}
              style={{ width: '100%' }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

function AgentPanel() {
  const { message } = App.useApp()
  const metadataStore = useMetadataStore()
  const [data, setData] = useState<AgentVO[]>([])
  const [llmOptions, setLlmOptions] = useState<{ label: string; value: number }[]>([])
  const [llmOptionsLoading, setLlmOptionsLoading] = useState(false)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingSystem, setEditingSystem] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => { fetchData() }, [])

  const fetchData = async () => {
    setLoading(true)
    try { setData(await agentApi.list()) } catch (e) { console.error(e) } finally { setLoading(false) }
  }

  const loadLlmOptions = async () => {
    setLlmOptionsLoading(true)
    try {
      const configs = await agentLlmConfigApi.list()
      setLlmOptions(configs.map(c => ({
        label: `${c.name} (${c.modelName}${c.protocol ? ` / ${c.protocol}` : ''})`,
        value: c.id
      })))
    } catch (e) { console.error(e) } finally { setLlmOptionsLoading(false) }
  }

  const handleAdd = async () => {
    setEditingId(null); setEditingSystem(false); form.resetFields()
    await loadLlmOptions()
    setModalOpen(true)
  }
  const handleEdit = async (row: AgentVO) => {
    setEditingId(row.id)
    setEditingSystem(!!row.isSystem)
    form.setFieldsValue({ name: row.name, role: row.role, systemPrompt: row.systemPrompt, type: row.type, llmConfigId: row.llmConfigId })
    await loadLlmOptions()
    setModalOpen(true)
  }
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingId) {
        let payload: AgentRequest
        if (editingSystem) {
          // 系统Agent只提交 systemPrompt 和 llmConfigId
          payload = { name: values.name, systemPrompt: values.systemPrompt, llmConfigId: values.llmConfigId }
        } else {
          payload = values as AgentRequest
        }
        await agentApi.update(editingId, payload)
        message.success('更新成功')
      } else {
        await agentApi.create(values as AgentRequest)
        message.success('创建成功')
      }
      setModalOpen(false); fetchData()
    } catch (e) { console.error(e) }
  }
  const handleDelete = async (id: number) => {
    try { await agentApi.delete(id); message.success('删除成功'); fetchData() } catch (e) { console.error(e) }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: 'Key', dataIndex: 'key', key: 'key', width: 180, render: (v: string) => <code>{v || '-'}</code> },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (type: string) => <Tag>{metadataStore.getNameByCode('AGENT_TYPE', type) || type || '-'}</Tag> },
    { title: 'LLM 配置', dataIndex: 'llmConfigName', width: 150, render: (v: string) => v || '-' },
    { title: '系统', dataIndex: 'isSystem', key: 'isSystem', width: 80, render: (v: boolean) => v ? <Tag color="blue">系统</Tag> : <Tag>自定义</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160, render: (v: string) => new Date(v).toLocaleString('zh-CN') },
    {
      title: '操作', key: 'action', width: 150,
      render: (_: unknown, record: AgentVO) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)} disabled={record.isSystem}>
            <Button type="link" danger icon={<DeleteOutlined />} disabled={record.isSystem}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增 Agent</Button>
      </div>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={false} />
      <Modal title={editingId ? '编辑 Agent' : '新增 Agent'} open={modalOpen} onOk={handleSubmit} onCancel={() => setModalOpen(false)} width={700}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: !editingSystem }]}>
            <Input placeholder="请输入 Agent 名称" disabled={editingSystem} />
          </Form.Item>
          {!editingSystem && (
            <>
              <Form.Item name="role" label="角色">
                <Input placeholder="请输入角色描述" />
              </Form.Item>
              <Form.Item name="type" label="类型">
                <Select options={metadataStore.getOptions('AGENT_TYPE')} placeholder="选择类型" />
              </Form.Item>
            </>
          )}
          <Form.Item name="llmConfigId" label="LLM 配置">
            <Select
              placeholder="选择 LLM 模型配置"
              allowClear
              showSearch
              loading={llmOptionsLoading}
              notFoundContent={llmOptionsLoading ? '加载中...' : '暂无 LLM 配置'}
              optionFilterProp="label"
              options={llmOptions}
            />
          </Form.Item>
          <Form.Item name="systemPrompt" label="系统提示词">
            <Input.TextArea rows={6} placeholder="请输入系统提示词" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

function LlmPanel() {
  const { message } = App.useApp()
  const [configs, setConfigs] = useState<AgentLlmConfigVO[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form] = Form.useForm()

  useEffect(() => { loadConfigs() }, [])

  const loadConfigs = async () => {
    setLoading(true)
    try { setConfigs(await agentLlmConfigApi.list()) } catch (e) { console.error(e) } finally { setLoading(false) }
  }

  const handleAdd = () => {
    setEditingId(null); form.resetFields()
    form.setFieldsValue({ protocol: 'OPENAI', modelType: 'INFERENCE', temperature: 0.7, maxTokens: 2048, isEnabled: true, isDefault: false })
    setModalOpen(true)
  }
  const handleEdit = (row: AgentLlmConfigVO) => {
    setEditingId(row.id)
    form.setFieldsValue({ name: row.name, description: row.description, provider: row.provider, apiEndpoint: row.apiEndpoint, protocol: row.protocol, modelName: row.modelName, modelType: row.modelType, temperature: row.temperature, maxTokens: row.maxTokens, isEnabled: row.isEnabled, isDefault: row.isDefault, apiKey: (row.authParams as Record<string, unknown>)?.apiKey || '' })
    setModalOpen(true)
  }
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const { apiKey, ...rest } = values
      const payload: AgentLlmConfigRequest = { ...rest, authParams: apiKey ? { apiKey } : undefined }
      if (editingId) { await agentLlmConfigApi.update(editingId, payload); message.success('更新成功') }
      else { await agentLlmConfigApi.create(payload); message.success('创建成功') }
      setModalOpen(false); loadConfigs()
    } catch (e) { console.error(e) }
  }
  const handleDelete = async (id: number) => {
    try { await agentLlmConfigApi.delete(id); message.success('删除成功'); loadConfigs() } catch (e) { console.error(e) }
  }
  const [testing, setTesting] = useState<number | null>(null)
  const handleTest = async (id: number) => {
    setTesting(id)
    try {
      await agentLlmConfigApi.testConnection(id)
      message.success('连接成功')
    } catch (e) {
      message.error('连接测试失败')
    } finally {
      setTesting(null)
    }
  }
  const handleSetDefault = async (id: number) => {
    try { await agentLlmConfigApi.setDefault(id); message.success('已设为默认'); loadConfigs() } catch (e) { console.error(e) }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '提供商', dataIndex: 'provider', key: 'provider', width: 100, render: (v: string) => v || '-' },
    { title: '协议', dataIndex: 'protocol', key: 'protocol', width: 100 },
    { title: '模型', dataIndex: 'modelName', key: 'modelName', width: 150 },
    {
      title: '状态', width: 120,
      render: (_: unknown, r: AgentLlmConfigVO) => (
        <Space>{r.isDefault && <Tag color="blue">默认</Tag>}{r.isEnabled ? <Tag color="green">启用</Tag> : <Tag>禁用</Tag>}</Space>
      )
    },
    {
      title: '操作', key: 'action', width: 260,
      render: (_: unknown, r: AgentLlmConfigVO) => (
        <Space>
          <Button type="link" loading={testing === r.id} onClick={() => handleTest(r.id)}>测试</Button>
          {!r.isDefault && <Button type="link" onClick={() => handleSetDefault(r.id)}>设为默认</Button>}
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(r)}>编辑</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(r.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增配置</Button>
      </div>
      <Table columns={columns} dataSource={configs} rowKey="id" loading={loading} pagination={false} />
      <Modal title={editingId ? '编辑模型配置' : '新增模型配置'} open={modalOpen} onOk={handleSubmit} onCancel={() => setModalOpen(false)} width={700}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input placeholder="请输入配置名称" /></Form.Item>
          <Form.Item name="provider" label="提供商"><Input placeholder="如: openai, anthropic" /></Form.Item>
          <Form.Item name="apiEndpoint" label="API 端点" rules={[{ required: true }]}><Input placeholder="请输入 API 端点" /></Form.Item>
          <Form.Item name="apiKey" label="API Key"><Input.Password placeholder="请输入 API Key" /></Form.Item>
          <Form.Item name="protocol" label="协议" rules={[{ required: true }]}>
            <Select options={[{ label: 'OpenAI', value: 'OPENAI' }, { label: 'Claude', value: 'CLAUDE' }, { label: '通义千问', value: 'QWEN' }, { label: '火山方舟', value: 'VOLCENGINE' }]} />
          </Form.Item>
          <Form.Item name="modelName" label="模型名称" rules={[{ required: true }]}><Input placeholder="如: gpt-4, claude-3-opus" /></Form.Item>
          <Form.Item name="modelType" label="模型类型">
            <Select options={[{ label: '推理模型', value: 'INFERENCE' }, { label: '嵌入模型', value: 'EMBEDDING' }]} />
          </Form.Item>
          <Space size="large">
            <Form.Item name="temperature" label="Temperature"><InputNumber min={0} max={2} step={0.1} style={{ width: 120 }} /></Form.Item>
            <Form.Item name="maxTokens" label="Max Tokens"><InputNumber min={1} max={128000} style={{ width: 120 }} /></Form.Item>
          </Space>
          <Form.Item name="description" label="描述"><Input.TextArea rows={3} placeholder="请输入描述" /></Form.Item>
          <Space size="large">
            <Form.Item name="isEnabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
            <Form.Item name="isDefault" label="默认" valuePropName="checked"><Switch /></Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}
export default function AiConfig() {
  const { message } = App.useApp()
  const [initLoading, setInitLoading] = useState(false)
  const [initialized, setInitialized] = useState<boolean | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    systemApi.initStatus().then(setInitialized).catch(() => setInitialized(false))
  }, [])

  const handleInit = async () => {
    setInitLoading(true)
    try {
      const result = await systemApi.init()
      if (result.skipped) {
        message.info('系统已初始化，无需重复操作')
      } else {
        message.success('系统初始化成功')
      }
      setInitialized(true)
      setRefreshKey(k => k + 1)
    } catch (e) {
      message.error('系统初始化失败')
    } finally {
      setInitLoading(false)
    }
  }

  const items = [
    { key: 'teams', label: 'Agent 团队', children: <AgentTeamPanel key={`team-${refreshKey}`} /> },
    { key: 'agents', label: 'Agent 配置', children: <AgentPanel key={`agent-${refreshKey}`} /> },
    { key: 'llm', label: 'LLM 配置', children: <LlmPanel key={`llm-${refreshKey}`} /> },
  ]

  return (
    <div className="settings-page">
      <div className="page-header">
        <div className="header-left">
          <h1>AI 配置</h1>
          <p>管理 Agent、团队和 LLM 模型配置</p>
        </div>
        <div className="header-right">
          <Button
            type="primary"
            icon={<RocketOutlined />}
            loading={initLoading}
            onClick={handleInit}
          >
            {initialized ? '重新初始化' : '一键初始化'}
          </Button>
        </div>
      </div>
      <Card className="table-card">
        <Tabs items={items} />
      </Card>
    </div>
  )
}


