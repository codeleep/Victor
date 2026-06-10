import { useEffect, useRef, useState } from 'react'
import { Card, Table, Button, Tag, Space, Tabs, Modal, Form, Input, Select, Switch, App, Popconfirm } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import JSONEditor from 'jsoneditor'
import 'jsoneditor/dist/jsoneditor.css'
import { voiceApi } from '@/api'
import type { VoiceAsrConfig, VoiceTtsConfig } from '@/types'
import './Settings.scss'

const EMPTY_EXTRA_PARAMS: Record<string, unknown> = {}

const providerOptions = [
  { label: '阿里云', value: 'ALIYUN' },
  { label: '腾讯云', value: 'TENCENT' },
  { label: '通义千问', value: 'QWEN' },
  { label: '豆包', value: 'DOUBAO' },
  { label: 'Azure', value: 'AZURE' },
  { label: 'OpenAI', value: 'OPENAI' },
]

function normalizeExtraParams(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return EMPTY_EXTRA_PARAMS
  }
  return value as Record<string, unknown>
}

function getExtraParamsPayload(value: unknown): Record<string, unknown> | undefined {
  const params = normalizeExtraParams(value)
  return Object.keys(params).length > 0 ? params : undefined
}

function ExtraParamsEditor({
  value,
  onChange,
  onReady,
}: {
  value: unknown
  onChange: (value: Record<string, unknown>) => void
  onReady?: (sync: () => Record<string, unknown>) => void
}) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const editorRef = useRef<JSONEditor | null>(null)
  const latestValueRef = useRef<Record<string, unknown>>(normalizeExtraParams(value))
  const onChangeRef = useRef(onChange)
  const onReadyRef = useRef(onReady)

  useEffect(() => {
    onChangeRef.current = onChange
    onReadyRef.current = onReady
  }, [onChange, onReady])

  useEffect(() => {
    if (!containerRef.current) return

    const editor = new JSONEditor(containerRef.current, {
      mode: 'code',
      modes: ['code', 'tree'],
      name: 'extra_params',
      language: 'zh-CN',
      mainMenuBar: true,
      navigationBar: false,
      statusBar: false,
      search: false,
      onChange: () => {
        try {
          const nextValue = normalizeExtraParams(editor.get())
          latestValueRef.current = nextValue
          onChangeRef.current(nextValue)
        } catch {
          // Code mode can be temporarily invalid while typing.
        }
      },
    }, latestValueRef.current)

    editorRef.current = editor
    onReadyRef.current?.(() => {
      const nextValue = normalizeExtraParams(editor.get())
      latestValueRef.current = nextValue
      onChangeRef.current(nextValue)
      return nextValue
    })
    return () => {
      editor.destroy()
      editorRef.current = null
      onReadyRef.current?.(() => normalizeExtraParams(latestValueRef.current))
    }
  }, [])

  useEffect(() => {
    const nextValue = normalizeExtraParams(value)
    if (JSON.stringify(nextValue) === JSON.stringify(latestValueRef.current)) {
      return
    }
    latestValueRef.current = nextValue
    editorRef.current?.set(nextValue)
  }, [value])

  return (
    <Form.Item label="拓展参数">
      <div ref={containerRef} style={{ height: 260 }} />
    </Form.Item>
  )
}

function AsrPanel() {
  const { message } = App.useApp()
  const [data, setData] = useState<VoiceAsrConfig[]>([])
  const [loading, setLoading] = useState(false)
  const [testing, setTesting] = useState<number | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form] = Form.useForm()
  const extraParams = Form.useWatch('extraParams', form)
  const extraParamsSyncRef = useRef<(() => Record<string, unknown>) | null>(null)

  useEffect(() => { fetchData() }, [])

  const fetchData = async () => {
    setLoading(true)
    try {
      setData(await voiceApi.listAsrConfigs())
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  const handleAdd = () => {
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({ extraParams: EMPTY_EXTRA_PARAMS, isEnabled: true, isDefault: false })
    setModalOpen(true)
  }

  const handleEdit = (row: VoiceAsrConfig) => {
    setEditingId(row.id!)
    form.setFieldsValue({
      name: row.name,
      provider: row.provider,
      language: row.language,
      apiEndpoint: row.apiEndpoint,
      description: row.description,
      apiKey: row.authParams?.apiKey || '',
      extraParams: normalizeExtraParams(row.extraParams),
      isEnabled: row.isEnabled,
      isDefault: row.isDefault,
    })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      try {
        extraParamsSyncRef.current?.()
      } catch {
        message.error('拓展参数不是合法的 JSON')
        return
      }
      const { apiKey, ...rest } = values
      const payload: VoiceAsrConfig = {
        ...rest,
        authParams: apiKey ? { apiKey } : undefined,
        extraParams: getExtraParamsPayload(form.getFieldValue('extraParams')),
      }

      if (editingId) {
        await voiceApi.updateAsrConfig(editingId, payload)
        message.success('更新成功')
      } else {
        await voiceApi.createAsrConfig(payload)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchData()
    } catch (e) {
      console.error(e)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await voiceApi.deleteAsrConfig(id)
      message.success('删除成功')
      fetchData()
    } catch (e) {
      console.error(e)
    }
  }

  const handleTest = async (id: number) => {
    setTesting(id)
    try {
      const result = await voiceApi.testAsr(id)
      message.success(`测试成功: ${result}`)
    } catch (e) {
      console.error(e)
      message.error('测试失败')
    } finally {
      setTesting(null)
    }
  }

  const handleSetDefault = async (id: number) => {
    try {
      await voiceApi.setDefaultAsr(id)
      message.success('已设为默认')
      fetchData()
    } catch (e) {
      console.error(e)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '提供商', dataIndex: 'provider', key: 'provider', width: 120 },
    { title: '语言', dataIndex: 'language', key: 'language', width: 100, render: (v: string) => v || '-' },
    {
      title: '状态',
      width: 100,
      render: (_: unknown, r: VoiceAsrConfig) => (
        <Space>{r.isDefault && <Tag color="blue">默认</Tag>}{r.isEnabled ? <Tag color="green">启用</Tag> : <Tag>禁用</Tag>}</Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_: unknown, r: VoiceAsrConfig) => (
        <Space>
          <Button type="link" loading={testing === r.id} onClick={() => handleTest(r.id!)}>测试</Button>
          <Button type="link" onClick={() => handleEdit(r)}>编辑</Button>
          {!r.isDefault && <Button type="link" onClick={() => handleSetDefault(r.id!)}>设为默认</Button>}
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(r.id!)}>
            <Button type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增 ASR</Button>
      </div>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={false} />
      <Modal title={editingId ? '编辑 ASR 配置' : '新增 ASR 配置'} open={modalOpen} onOk={handleSubmit} onCancel={() => setModalOpen(false)} width={720}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input placeholder="请输入配置名称" /></Form.Item>
          <Form.Item name="provider" label="提供商" rules={[{ required: true }]}><Select options={providerOptions} /></Form.Item>
          <Form.Item name="language" label="语言"><Input placeholder="如 zh-CN" /></Form.Item>
          <Form.Item name="apiEndpoint" label="API 端点"><Input placeholder="请输入 API 端点" /></Form.Item>
          <Form.Item name="apiKey" label="API Key"><Input.Password placeholder="请输入 API Key" /></Form.Item>
          <ExtraParamsEditor
            value={extraParams}
            onChange={(nextValue) => form.setFieldValue('extraParams', nextValue)}
            onReady={(sync) => { extraParamsSyncRef.current = sync }}
          />
          <Form.Item name="description" label="描述"><Input.TextArea rows={3} placeholder="请输入描述" /></Form.Item>
          <Space>
            <Form.Item name="isEnabled" label="启用" valuePropName="checked" style={{ marginBottom: 0 }}><Switch /></Form.Item>
            <Form.Item name="isDefault" label="默认" valuePropName="checked" style={{ marginBottom: 0 }}><Switch /></Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}

function TtsPanel() {
  const { message } = App.useApp()
  const [data, setData] = useState<VoiceTtsConfig[]>([])
  const [loading, setLoading] = useState(false)
  const [testing, setTesting] = useState<number | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form] = Form.useForm()
  const extraParams = Form.useWatch('extraParams', form)
  const extraParamsSyncRef = useRef<(() => Record<string, unknown>) | null>(null)

  useEffect(() => { fetchData() }, [])

  const fetchData = async () => {
    setLoading(true)
    try {
      setData(await voiceApi.listTtsConfigs())
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  const handleAdd = () => {
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({ extraParams: EMPTY_EXTRA_PARAMS, isEnabled: true, isDefault: false })
    setModalOpen(true)
  }

  const handleEdit = (row: VoiceTtsConfig) => {
    setEditingId(row.id!)
    form.setFieldsValue({
      name: row.name,
      provider: row.provider,
      voiceName: row.voiceName,
      apiEndpoint: row.apiEndpoint,
      description: row.description,
      apiKey: row.authParams?.apiKey || '',
      extraParams: normalizeExtraParams(row.extraParams),
      isEnabled: row.isEnabled,
      isDefault: row.isDefault,
    })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      try {
        extraParamsSyncRef.current?.()
      } catch {
        message.error('拓展参数不是合法的 JSON')
        return
      }
      const { apiKey, ...rest } = values
      const payload: VoiceTtsConfig = {
        ...rest,
        authParams: apiKey ? { apiKey } : undefined,
        extraParams: getExtraParamsPayload(form.getFieldValue('extraParams')),
      }

      if (editingId) {
        await voiceApi.updateTtsConfig(editingId, payload)
        message.success('更新成功')
      } else {
        await voiceApi.createTtsConfig(payload)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchData()
    } catch (e) {
      console.error(e)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await voiceApi.deleteTtsConfig(id)
      message.success('删除成功')
      fetchData()
    } catch (e) {
      console.error(e)
    }
  }

  const handleTest = async (id: number) => {
    setTesting(id)
    try {
      const token = localStorage.getItem('token')
      const resp = await fetch(`/api/v1/voice/tts-configs/${id}/test`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      const blob = await resp.blob()
      console.log('[TTS] blob size:', blob.size, 'type:', blob.type)
      const url = URL.createObjectURL(blob)
      const audio = new Audio(url)
      audio.onended = () => URL.revokeObjectURL(url)
      audio.onerror = (e) => {
        console.error('[TTS] audio error:', e)
        message.error('音频播放失败')
        URL.revokeObjectURL(url)
      }
      audio.play().catch((e) => {
        console.error('[TTS] play rejected:', e)
        message.error('浏览器拒绝播放')
      })
      message.success('正在播放测试语音')
    } catch (e) {
      console.error('[TTS] failed:', e)
      message.error('测试失败')
    } finally {
      setTesting(null)
    }
  }

  const handleSetDefault = async (id: number) => {
    try {
      await voiceApi.setDefaultTts(id)
      message.success('已设为默认')
      fetchData()
    } catch (e) {
      console.error(e)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '提供商', dataIndex: 'provider', key: 'provider', width: 120 },
    { title: '语音', dataIndex: 'voiceName', key: 'voiceName', width: 160, render: (v: string) => v || '-' },
    {
      title: '状态',
      width: 100,
      render: (_: unknown, r: VoiceTtsConfig) => (
        <Space>{r.isDefault && <Tag color="blue">默认</Tag>}{r.isEnabled ? <Tag color="green">启用</Tag> : <Tag>禁用</Tag>}</Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_: unknown, r: VoiceTtsConfig) => (
        <Space>
          <Button type="link" loading={testing === r.id} onClick={() => handleTest(r.id!)}>测试</Button>
          <Button type="link" onClick={() => handleEdit(r)}>编辑</Button>
          {!r.isDefault && <Button type="link" onClick={() => handleSetDefault(r.id!)}>设为默认</Button>}
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(r.id!)}>
            <Button type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增 TTS</Button>
      </div>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={false} />
      <Modal title={editingId ? '编辑 TTS 配置' : '新增 TTS 配置'} open={modalOpen} onOk={handleSubmit} onCancel={() => setModalOpen(false)} width={720}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input placeholder="请输入配置名称" /></Form.Item>
          <Form.Item name="provider" label="提供商" rules={[{ required: true }]}><Select options={providerOptions} /></Form.Item>
          <Form.Item name="voiceName" label="语音名称"><Input placeholder="请输入语音名称" /></Form.Item>
          <Form.Item name="apiEndpoint" label="API 端点"><Input placeholder="请输入 API 端点" /></Form.Item>
          <Form.Item name="apiKey" label="API Key"><Input.Password placeholder="请输入 API Key" /></Form.Item>
          <ExtraParamsEditor
            value={extraParams}
            onChange={(nextValue) => form.setFieldValue('extraParams', nextValue)}
            onReady={(sync) => { extraParamsSyncRef.current = sync }}
          />
          <Form.Item name="description" label="描述"><Input.TextArea rows={3} placeholder="请输入描述" /></Form.Item>
          <Space>
            <Form.Item name="isEnabled" label="启用" valuePropName="checked" style={{ marginBottom: 0 }}><Switch /></Form.Item>
            <Form.Item name="isDefault" label="默认" valuePropName="checked" style={{ marginBottom: 0 }}><Switch /></Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}

export default function VoiceConfig() {
  const items = [
    { key: 'asr', label: 'ASR 配置', children: <AsrPanel /> },
    { key: 'tts', label: 'TTS 配置', children: <TtsPanel /> },
  ]

  return (
    <div className="settings-page">
      <div className="page-header">
        <div className="header-left">
          <h1>语音配置</h1>
          <p>管理 ASR 和 TTS 语音服务配置</p>
        </div>
      </div>
      <Card className="table-card">
        <Tabs items={items} />
      </Card>
    </div>
  )
}
