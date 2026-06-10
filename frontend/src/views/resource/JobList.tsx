import { useEffect, useState } from 'react'
import { Card, Table, Button, Tag, Space, Modal, Form, Input, InputNumber, Select, Popover, Dropdown, message, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import { jobApi } from '@/api'
import type { JobVO, JobRequest } from '@/types'
import './ResourceList.scss'

export default function JobList() {
  const [data, setData] = useState<JobVO[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewContent, setPreviewContent] = useState('')
  const [form] = Form.useForm()
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })

  useEffect(() => {
    fetchData()
  }, [pagination.current, pagination.pageSize])

  const fetchData = async () => {
    setLoading(true)
    try {
      const res = await jobApi.list({ page: pagination.current - 1, size: pagination.pageSize })
      setData(res.records)
      setPagination(prev => ({ ...prev, total: res.total }))
    } catch (error) {
      console.error('Failed to fetch jobs:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleAdd = () => {
    setEditingId(null)
    form.resetFields()
    setModalOpen(true)
  }

  const handleEdit = (row: JobVO) => {
    setEditingId(row.id)
    form.setFieldsValue({
      name: row.name,
      description: row.description,
      requiredSkills: row.requiredSkills,
      experienceYears: row.experienceYears,
      education: row.education,
      salaryRange: row.salaryRange,
      domains: row.domains,
    })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingId) {
        await jobApi.update(editingId, values as JobRequest)
        message.success('更新成功')
      } else {
        await jobApi.create(values as JobRequest)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchData()
    } catch (error) {
      console.error('Failed to save job:', error)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await jobApi.delete(id)
      message.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('Failed to delete job:', error)
    }
  }

  const handleApprove = async (id: number) => {
    try {
      await jobApi.approve(id)
      message.success('审核通过')
      fetchData()
    } catch (error) {
      console.error('Failed to approve job:', error)
    }
  }

  const handleReject = async (id: number) => {
    try {
      await jobApi.reject(id)
      message.success('已拒绝')
      fetchData()
    } catch (error) {
      console.error('Failed to reject job:', error)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '岗位名称', dataIndex: 'name', key: 'name' },
    { title: '经验要求', dataIndex: 'experienceYears', key: 'experienceYears', width: 100, render: (v: number) => v ? `${v}年` : '-' },
    { title: '学历要求', dataIndex: 'education', key: 'education', width: 100, render: (v: string) => v || '-' },
    { title: '薪资范围', dataIndex: 'salaryRange', key: 'salaryRange', width: 120, render: (v: string) => v || '-' },
    {
      title: '技能要求',
      dataIndex: 'requiredSkills',
      key: 'requiredSkills',
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
      title: '描述',
      width: 100,
      render: (_: unknown, record: JobVO) => (
        record.description ? (
          <Button type="link" size="small" onClick={() => { setPreviewContent(record.description!); setPreviewOpen(true) }}>查看</Button>
        ) : <span style={{ color: '#8E8E8C' }}>-</span>
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
      render: (_: unknown, record: JobVO) => (
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
          <h1>岗位库</h1>
          <p>管理目标岗位信息</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增岗位</Button>
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
              setPagination(prev => ({ ...prev, current: page, pageSize }))
            }
          }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑岗位' : '新增岗位'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={700}
      >
        <Form form={form} layout="vertical">
          <Space size="large" style={{ width: '100%' }}>
            <Form.Item name="name" label="岗位名称" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input placeholder="请输入岗位名称" />
            </Form.Item>
            <Form.Item name="salaryRange" label="薪资范围" style={{ flex: 1 }}>
              <Input placeholder="如: 20k-40k" />
            </Form.Item>
          </Space>
          <Space size="large" style={{ width: '100%' }}>
            <Form.Item name="experienceYears" label="经验年限">
              <InputNumber min={0} max={30} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="education" label="学历要求" style={{ flex: 1 }}>
              <Select
                options={[
                  { label: '不限', value: '' },
                  { label: '大专', value: '大专' },
                  { label: '本科', value: '本科' },
                  { label: '硕士', value: '硕士' },
                  { label: '博士', value: '博士' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="岗位描述">
            <MDEditor height={200} />
          </Form.Item>
          <Form.Item name="requiredSkills" label="技能要求">
            <Select mode="tags" placeholder="输入后回车添加" />
          </Form.Item>
          <Form.Item name="domains" label="领域">
            <Select mode="tags" placeholder="输入后回车添加" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="查看描述" open={previewOpen} onCancel={() => setPreviewOpen(false)} footer={null} width={700}>
        <MDEditor.Markdown source={previewContent} />
      </Modal>
    </div>
  )
}
