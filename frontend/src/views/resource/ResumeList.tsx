import { useEffect, useState } from 'react'
import { Card, Table, Button, Tag, Space, Modal, Form, Input, Upload, Dropdown, message, Popconfirm } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import { resumeApi } from '@/api'
import type { ResumeVO } from '@/types'
import './ResourceList.scss'

export default function ResumeList() {
  const [data, setData] = useState<ResumeVO[]>([])
  const [loading, setLoading] = useState(false)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [contentOpen, setContentOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingContent, setEditingContent] = useState('')
  const [form] = Form.useForm()
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  useEffect(() => {
    fetchData()
  }, [])

  const fetchData = async () => {
    setLoading(true)
    try {
      const res = await resumeApi.list()
      setData(res)
    } catch (error) {
      console.error('Failed to fetch resumes:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleUpload = async () => {
    try {
      const values = await form.validateFields()
      if (!selectedFile) {
        message.warning('请选择要上传的文件')
        return
      }
      await resumeApi.upload(selectedFile, values.name)
      message.success('上传成功')
      setUploadOpen(false)
      form.resetFields()
      setSelectedFile(null)
      fetchData()
    } catch (error) {
      console.error('Failed to upload resume:', error)
    }
  }

  const handleParse = async (id: number) => {
    try {
      await resumeApi.parse(id)
      message.success('解析任务已提交')
      fetchData()
    } catch (error) {
      console.error('Failed to parse resume:', error)
    }
  }

  const handleViewContent = async (row: ResumeVO) => {
    try {
      const detail = await resumeApi.getById(row.id)
      setEditingId(row.id)
      setEditingContent(detail.rawText || '')
      setContentOpen(true)
    } catch (error) {
      console.error('Failed to fetch resume detail:', error)
    }
  }

  const handleSaveContent = async () => {
    if (!editingId) return
    try {
      await resumeApi.update(editingId, { rawText: editingContent })
      message.success('保存成功')
      setContentOpen(false)
      fetchData()
    } catch (error) {
      console.error('Failed to save resume content:', error)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await resumeApi.delete(id)
      message.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('Failed to delete resume:', error)
    }
  }

  const handleApprove = async (id: number) => {
    try {
      await resumeApi.approve(id)
      message.success('审核通过')
      fetchData()
    } catch (error) {
      console.error('Failed to approve resume:', error)
    }
  }

  const handleReject = async (id: number) => {
    try {
      await resumeApi.reject(id)
      message.success('已拒绝')
      fetchData()
    } catch (error) {
      console.error('Failed to reject resume:', error)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '简历名称', dataIndex: 'name', key: 'name' },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', render: (v: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string) => {
        const map: Record<string, { color: string; text: string }> = {
          PENDING: { color: 'default', text: '待解析' },
          PARSED: { color: 'warning', text: '已解析' },
          EMBEDDED: { color: 'success', text: '已向量化' }
        }
        const info = map[status] || { color: 'default', text: status }
        return <Tag color={info.color}>{info.text}</Tag>
      }
    },
    {
      title: '解析内容',
      width: 100,
      render: (_: unknown, record: ResumeVO) => (
        (record.status === 'PARSED' || record.status === 'EMBEDDED') ? (
          <Button type="link" size="small" onClick={() => handleViewContent(record)}>查看/编辑</Button>
        ) : <span style={{ color: '#8E8E8C' }}>-</span>
      )
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString('zh-CN')
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_: unknown, record: ResumeVO) => (
        <Space>
          <Button type="link" onClick={() => handleParse(record.id)} disabled={record.status !== 'PENDING'}>解析</Button>
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
            <Button type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div className="resource-list-page">
      <div className="page-header">
        <div className="header-left">
          <h1>简历管理</h1>
          <p>上传和管理你的简历</p>
        </div>
        <Button type="primary" icon={<UploadOutlined />} onClick={() => { form.resetFields(); setSelectedFile(null); setUploadOpen(true) }}>
          上传简历
        </Button>
      </div>

      <Card className="table-card">
        <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }} />
      </Card>

      <Modal title="上传简历" open={uploadOpen} onOk={handleUpload} onCancel={() => setUploadOpen(false)} width={500}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="简历名称" rules={[{ required: true, message: '请输入简历名称' }]}>
            <Input placeholder="请输入简历名称" />
          </Form.Item>
          <Form.Item label="简历文件">
            <Upload
              beforeUpload={file => { setSelectedFile(file); return false }}
              maxCount={1}
              accept=".pdf,.doc,.docx"
              onRemove={() => setSelectedFile(null)}
            >
              <Button icon={<UploadOutlined />}>选择文件</Button>
            </Upload>
            <div style={{ color: '#8E8E8C', fontSize: 12, marginTop: 4 }}>支持 PDF、Word 格式，大小不超过 10MB</div>
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="简历解析内容" open={contentOpen} onOk={handleSaveContent} onCancel={() => setContentOpen(false)} width={800}>
        <MDEditor value={editingContent} onChange={(v) => setEditingContent(v || '')} height={500} />
      </Modal>
    </div>
  )
}
