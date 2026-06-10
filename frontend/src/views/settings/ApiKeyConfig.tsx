import { useEffect, useState } from 'react'
import { Card, Table, Button, Tag, Space, Modal, Form, Input, Select, Popover, message, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, CopyOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { apiKeyApi } from '@/api'
import type { OpenApiKeyVO, OpenApiKeyRequest } from '@/types'
import './Settings.scss'

export default function ApiKeyConfig() {
  const [data, setData] = useState<OpenApiKeyVO[]>([])
  const [loading, setLoading] = useState(false)
  const [validating, setValidating] = useState<number | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form] = Form.useForm()

  useEffect(() => {
    fetchData()
  }, [])

  const fetchData = async () => {
    setLoading(true)
    try {
      const res = await apiKeyApi.list()
      setData(res)
    } catch (error) {
      console.error('Failed to fetch API keys:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleAdd = () => {
    setEditingId(null)
    form.resetFields()
    setModalOpen(true)
  }

  const handleEdit = (row: OpenApiKeyVO) => {
    setEditingId(row.id)
    form.setFieldsValue({
      name: row.name,
      description: row.description,
      apiKey: row.apiKey,
      scopes: row.scopes,
      status: row.status,
    })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingId) {
        await apiKeyApi.update(editingId, values as OpenApiKeyRequest)
        message.success('更新成功')
      } else {
        await apiKeyApi.create(values as OpenApiKeyRequest)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchData()
    } catch (error) {
      console.error('Failed to save API key:', error)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await apiKeyApi.delete(id)
      message.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('Failed to delete API key:', error)
    }
  }

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  }

  const handleValidate = async (apiKey: string, id: number) => {
    setValidating(id)
    try {
      const valid = await apiKeyApi.validate(apiKey)
      if (valid) {
        message.success('API Key 验证通过')
      } else {
        message.error('API Key 验证失败')
      }
    } catch (e) {
      message.error('验证请求失败')
    } finally {
      setValidating(null)
    }
  }

  const maskKey = (key: string) => {
    if (key.length <= 8) return key
    return key.slice(0, 4) + '****' + key.slice(-4)
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: 'API Key',
      dataIndex: 'apiKey',
      key: 'apiKey',
      render: (key: string) => (
        <Space>
          <span style={{ fontFamily: 'monospace' }}>{maskKey(key)}</span>
          <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => handleCopy(key)} />
        </Space>
      )
    },
    {
      title: '权限',
      dataIndex: 'scopes',
      key: 'scopes',
      render: (scopes: string[]) => (
        <Space size={[0, 4]} wrap>
          {scopes?.slice(0, 3).map(s => <Tag key={s}>{s}</Tag>)}
          {(scopes?.length || 0) > 3 && (
            <Popover content={<Space size={[0, 4]} wrap>{scopes!.slice(3).map(s => <Tag key={s}>{s}</Tag>)}</Space>}>
              <Tag style={{ cursor: 'pointer' }}>+{scopes!.length - 3}</Tag>
            </Popover>
          )}
        </Space>
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => (
        <Tag color={status === 'ENABLED' ? 'green' : 'default'}>
          {status === 'ENABLED' ? '启用' : '禁用'}
        </Tag>
      )
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
      render: (_: unknown, record: OpenApiKeyVO) => (
        <Space>
          <Button type="link" icon={<CheckCircleOutlined />} loading={validating === record.id} onClick={() => handleValidate(record.apiKey, record.id)}>验证</Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div className="settings-page">
      <div className="page-header">
        <div className="header-left">
          <h1>API Key 管理</h1>
          <p>管理 Open API 密钥</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增 API Key</Button>
      </div>

      <Card className="table-card">
        <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={false} />
      </Card>

      <Modal
        title={editingId ? '编辑 API Key' : '新增 API Key'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="请输入名称" />
          </Form.Item>
          <Form.Item name="apiKey" label="API Key" rules={[{ required: true }]}>
            <Input placeholder="请输入 API Key" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="请输入描述" />
          </Form.Item>
          <Form.Item name="scopes" label="权限">
            <Select mode="tags" placeholder="输入权限后回车" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={[
              { label: '启用', value: 'ENABLED' },
              { label: '禁用', value: 'DISABLED' },
            ]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
