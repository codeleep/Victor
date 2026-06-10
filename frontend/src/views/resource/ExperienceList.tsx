import { useEffect, useState } from 'react'
import { Card, Table, Button, Tag, Space, Modal, Form, Input, Select, Popover, Dropdown, message, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import { experienceApi } from '@/api'
import type { ExperienceVO, ExperienceRequest } from '@/types'
import './ResourceList.scss'

export default function ExperienceList() {
  const [data, setData] = useState<ExperienceVO[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewContent, setPreviewContent] = useState('')
  const [filterType, setFilterType] = useState<string>('')
  const [form] = Form.useForm()

  useEffect(() => {
    fetchData()
  }, [filterType])

  const fetchData = async () => {
    setLoading(true)
    try {
      const res = await experienceApi.list(filterType || undefined)
      setData(res)
    } catch (error) {
      console.error('Failed to fetch experiences:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleAdd = () => {
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({ type: 'PROJECT' })
    setModalOpen(true)
  }

  const handleEdit = (row: ExperienceVO) => {
    setEditingId(row.id)
    form.setFieldsValue({
      type: row.type,
      title: row.title,
      startDate: row.startDate,
      endDate: row.endDate,
      description: row.description,
      skills: row.skills,
    })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingId) {
        await experienceApi.update(editingId, values as ExperienceRequest)
        message.success('更新成功')
      } else {
        await experienceApi.create(values as ExperienceRequest)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchData()
    } catch (error) {
      console.error('Failed to save experience:', error)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await experienceApi.delete(id)
      message.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('Failed to delete experience:', error)
    }
  }

  const handleApprove = async (id: number) => {
    try {
      await experienceApi.approve(id)
      message.success('审核通过')
      fetchData()
    } catch (error) {
      console.error('Failed to approve experience:', error)
    }
  }

  const handleReject = async (id: number) => {
    try {
      await experienceApi.reject(id)
      message.success('已拒绝')
      fetchData()
    } catch (error) {
      console.error('Failed to reject experience:', error)
    }
  }

  const getTypeName = (type: string) => {
    const map: Record<string, string> = { PROJECT: '项目经历', WORK: '工作经历', EDUCATION: '教育经历', OTHER: '其他' }
    return map[type] || type
  }

  const getTypeColor = (type: string) => {
    const map: Record<string, string> = { PROJECT: 'blue', WORK: 'green', EDUCATION: 'orange', OTHER: 'default' }
    return map[type] || 'default'
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type: string) => <Tag color={getTypeColor(type)}>{getTypeName(type)}</Tag>
    },
    { title: '标题', dataIndex: 'title', key: 'title' },
    { title: '开始日期', dataIndex: 'startDate', key: 'startDate', width: 120, render: (v: string) => v || '-' },
    { title: '结束日期', dataIndex: 'endDate', key: 'endDate', width: 120, render: (v: string) => v || '-' },
    {
      title: '描述',
      width: 100,
      render: (_: unknown, record: ExperienceVO) => (
        record.description ? (
          <Button type="link" size="small" onClick={() => { setPreviewContent(record.description!); setPreviewOpen(true) }}>查看</Button>
        ) : <span style={{ color: '#8E8E8C' }}>-</span>
      )
    },
    {
      title: '技能',
      dataIndex: 'skills',
      key: 'skills',
      render: (skills: string[]) => (
        <Space size={[0, 4]} wrap>
          {skills?.slice(0, 3).map(s => <Tag key={s}>{s}</Tag>)}
          {(skills?.length || 0) > 3 && (
            <Popover content={<Space size={[0, 4]} wrap>{skills!.slice(3).map(s => <Tag key={s}>{s}</Tag>)}</Space>}>
              <Tag style={{ cursor: 'pointer' }}>+{skills!.length - 3}</Tag>
            </Popover>
          )}
        </Space>
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
      width: 200,
      render: (_: unknown, record: ExperienceVO) => (
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
          <h1>经历管理</h1>
          <p>管理你的项目经历、工作经历和教育经历</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增经历</Button>
      </div>

      <div className="filter-bar">
        <Select
          value={filterType}
          onChange={setFilterType}
          placeholder="全部类型"
          allowClear
          style={{ width: 150 }}
          options={[
            { label: '项目经历', value: 'PROJECT' },
            { label: '工作经历', value: 'WORK' },
            { label: '教育经历', value: 'EDUCATION' },
            { label: '其他', value: 'OTHER' },
          ]}
        />
      </div>

      <Card className="table-card">
        <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }} />
      </Card>

      <Modal
        title={editingId ? '编辑经历' : '新增经历'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={[
              { label: '项目经历', value: 'PROJECT' },
              { label: '工作经历', value: 'WORK' },
              { label: '教育经历', value: 'EDUCATION' },
              { label: '其他', value: 'OTHER' },
            ]} />
          </Form.Item>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}>
            <Input placeholder="请输入标题" />
          </Form.Item>
          <Space size="large">
            <Form.Item name="startDate" label="开始日期">
              <Input placeholder="如: 2023-01" />
            </Form.Item>
            <Form.Item name="endDate" label="结束日期">
              <Input placeholder="如: 2024-06 或 至今" />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="描述">
            <MDEditor height={200} />
          </Form.Item>
          <Form.Item name="skills" label="技能">
            <Select mode="tags" placeholder="输入技能标签后回车" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="查看描述" open={previewOpen} onCancel={() => setPreviewOpen(false)} footer={null} width={700}>
        <MDEditor.Markdown source={previewContent} />
      </Modal>
    </div>
  )
}
