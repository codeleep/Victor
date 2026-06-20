import { useEffect, useState } from 'react'
import { Card, Table, Button, Tag, Space, Modal, Form, Input, Select, Popover, Dropdown, message, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import MarkdownEditor from '@/components/MarkdownEditor'
import MarkdownView from '@/components/MarkdownView'
import { questionApi } from '@/api'
import { useMetadataStore } from '@/stores/metadata'
import type { QuestionVO, QuestionRequest, QuestionQueryRequest } from '@/types'
import './ResourceList.scss'

export default function QuestionList() {
  const metadataStore = useMetadataStore()
  const [data, setData] = useState<QuestionVO[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewContent, setPreviewContent] = useState('')
  const [previewTitle, setPreviewTitle] = useState('')
  const [form] = Form.useForm()
  const [searchParams, setSearchParams] = useState<QuestionQueryRequest>({
    page: 0,
    size: 10,
  })
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })

  useEffect(() => {
    fetchData()
  }, [searchParams])

  const fetchData = async () => {
    setLoading(true)
    try {
      const res = await questionApi.list(searchParams)
      setData(res.records)
      setPagination(prev => ({ ...prev, total: res.total }))
    } catch (error) {
      console.error('Failed to fetch questions:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleAdd = () => {
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({ type: 'TECHNICAL', difficulty: 'MEDIUM', tags: [] })
    setModalOpen(true)
  }

  const handleEdit = (row: QuestionVO) => {
    setEditingId(row.id)
    form.setFieldsValue({
      title: row.title,
      description: row.description,
      type: row.type,
      difficulty: row.difficulty,
      tags: row.tags,
      referenceAnswer: row.referenceAnswer,
    })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingId) {
        await questionApi.update(editingId, values as QuestionRequest)
        message.success('更新成功')
      } else {
        await questionApi.create(values as QuestionRequest)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchData()
    } catch (error) {
      console.error('Failed to save question:', error)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await questionApi.delete(id)
      message.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('Failed to delete question:', error)
    }
  }

  const handleApprove = async (id: number) => {
    try {
      await questionApi.approve(id)
      message.success('审核通过')
      fetchData()
    } catch (error) {
      console.error('Failed to approve question:', error)
    }
  }

  const handleReject = async (id: number) => {
    try {
      await questionApi.reject(id)
      message.success('已拒绝')
      fetchData()
    } catch (error) {
      console.error('Failed to reject question:', error)
    }
  }

  const handlePreview = (content: string, title: string) => {
    setPreviewContent(content)
    setPreviewTitle(title)
    setPreviewOpen(true)
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '题目',
      dataIndex: 'title',
      key: 'title',
      render: (text: string, record: QuestionVO) => (
        <Space>
          <span>{text}</span>
          {record.type && (
            <Tag color={record.type === 'TECHNICAL' ? 'blue' : record.type === 'BEHAVIORAL' ? 'green' : 'default'}>
              {metadataStore.getNameByCode('QUESTION_TYPE', record.type)}
            </Tag>
          )}
        </Space>
      )
    },
    {
      title: '难度',
      dataIndex: 'difficulty',
      key: 'difficulty',
      width: 100,
      render: (d: string) => (
        <Tag color={d === 'EASY' ? 'success' : d === 'HARD' ? 'error' : 'warning'}>
          {metadataStore.getNameByCode('DIFFICULTY', d)}
        </Tag>
      )
    },
    {
      title: '标签',
      dataIndex: 'tags',
      key: 'tags',
      width: 200,
      render: (tags: string[]) => (
        <Space size={[0, 4]} wrap>
          {tags?.slice(0, 3).map(tag => <Tag key={tag}>{tag}</Tag>)}
          {(tags?.length || 0) > 3 && (
            <Popover content={<Space size={[0, 4]} wrap>{tags!.slice(3).map(tag => <Tag key={tag}>{tag}</Tag>)}</Space>}>
              <Tag style={{ cursor: 'pointer' }}>+{tags!.length - 3}</Tag>
            </Popover>
          )}
        </Space>
      )
    },
    {
      title: '描述',
      width: 80,
      render: (_: unknown, record: QuestionVO) => (
        record.description ? (
          <Button type="link" size="small" onClick={() => handlePreview(record.description!, '题目描述')}>查看</Button>
        ) : <span style={{ color: '#8E8E8C' }}>-</span>
      )
    },
    {
      title: '参考答案',
      width: 100,
      render: (_: unknown, record: QuestionVO) => (
        record.referenceAnswer ? (
          <Button type="link" size="small" onClick={() => handlePreview(record.referenceAnswer!, '参考答案')}>查看</Button>
        ) : <span style={{ color: '#8E8E8C' }}>-</span>
      )
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 100,
      render: (s: string) => {
        const map: Record<string, string> = { SYSTEM: '系统', USER: '用户创建', OPEN_API: 'API导入' }
        return <Tag>{map[s] || s || '-'}</Tag>
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
      render: (_: unknown, record: QuestionVO) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          {record.ingestStatus === 'PENDING_REVIEW' && (
            <Dropdown menu={{
              items: [
                { key: 'approve', label: '通过', onClick: () => handleApprove(record.id) },
                { key: 'reject', label: '拒绝', danger: true, onClick: () => handleReject(record.id) },
              ]
            }}>
              <Button type="link">审核</Button>
            </Dropdown>
          )}
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div className="resource-list-page">
      <div className="page-header">
        <div className="header-left">
          <h1>题库管理</h1>
          <p>管理面试题库，支持多种题型分类</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增题目</Button>
      </div>

      <div className="filter-bar">
        <Select
          value={searchParams.type}
          onChange={v => setSearchParams(prev => ({ ...prev, type: v }))}
          placeholder="题目类型"
          allowClear
          style={{ width: 140 }}
          options={metadataStore.getOptions('QUESTION_TYPE')}
        />
        <Select
          value={searchParams.difficulty}
          onChange={v => setSearchParams(prev => ({ ...prev, difficulty: v }))}
          placeholder="难度"
          allowClear
          style={{ width: 120 }}
          options={metadataStore.getOptions('DIFFICULTY')}
        />
      </div>

      <Card className="table-card">
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            onChange: (page, pageSize) => {
              setSearchParams(prev => ({ ...prev, page: page - 1, size: pageSize }))
              setPagination(prev => ({ ...prev, current: page, pageSize }))
            }
          }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑题目' : '新增题目'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={700}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="题目" rules={[{ required: true, message: '请输入题目标题' }]}>
            <Input placeholder="请输入题目标题" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={4} placeholder="请输入题目描述" />
          </Form.Item>
          <Space size="large">
            <Form.Item name="type" label="类型" rules={[{ required: true }]}>
              <Select style={{ width: 150 }} options={metadataStore.getOptions('QUESTION_TYPE')} />
            </Form.Item>
            <Form.Item name="difficulty" label="难度" rules={[{ required: true }]}>
              <Select style={{ width: 120 }} options={metadataStore.getOptions('DIFFICULTY')} />
            </Form.Item>
          </Space>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="输入标签后回车" />
          </Form.Item>
          <Form.Item name="referenceAnswer" label="参考答案">
            <MarkdownEditor height={300} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={previewTitle}
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width="80%"
        style={{ top: '10vh' }}
        styles={{ body: { height: '75vh', overflow: 'auto' } }}
      >
        <MarkdownView source={previewContent} />
      </Modal>
    </div>
  )
}

