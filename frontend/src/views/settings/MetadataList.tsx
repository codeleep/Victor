import { useEffect, useState } from 'react'
import { Card, Table, Button, Tag, Space, Modal, Form, Input, Select, InputNumber, Switch, message, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { metadataApi } from '@/api'
import type { MetadataVO, MetadataRequest } from '@/types'
import './Settings.scss'

export default function MetadataList() {
  const [data, setData] = useState<MetadataVO[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [filterCategory, setFilterCategory] = useState<string>('')
  const [form] = Form.useForm()

  useEffect(() => {
    loadCategories()
  }, [])

  useEffect(() => {
    fetchData()
  }, [filterCategory])

  const loadCategories = async () => {
    try {
      const cats = await metadataApi.getCategories()
      setCategories(cats)
    } catch (error) {
      console.error('Failed to load categories:', error)
    }
  }

  const fetchData = async () => {
    setLoading(true)
    try {
      const res = await metadataApi.pageList({ category: filterCategory || undefined, page: 0, size: 10 })
      setData(res.records)
    } catch (error) {
      console.error('Failed to fetch metadata:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleAdd = () => {
    setEditingId(null)
    form.resetFields()
    setModalOpen(true)
  }

  const handleEdit = (row: MetadataVO) => {
    setEditingId(row.id)
    form.setFieldsValue({
      category: row.category,
      code: row.code,
      name: row.name,
      description: row.description,
      sortOrder: row.sortOrder,
      isActive: row.isActive,
    })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingId) {
        await metadataApi.update(editingId, values as MetadataRequest)
        message.success('更新成功')
      } else {
        await metadataApi.create(values as MetadataRequest)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchData()
      loadCategories()
    } catch (error) {
      console.error('Failed to save metadata:', error)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await metadataApi.delete(id)
      message.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('Failed to delete metadata:', error)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '分类', dataIndex: 'category', key: 'category', width: 150 },
    { title: '编码', dataIndex: 'code', key: 'code', width: 150 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '描述', dataIndex: 'description', key: 'description', render: (v: string) => v || '-' },
    { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80, render: (v: number) => v || 0 },
    {
      title: '状态',
      dataIndex: 'isActive',
      key: 'isActive',
      width: 80,
      render: (v: boolean) => v !== false ? <Tag color="green">启用</Tag> : <Tag>禁用</Tag>
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: unknown, record: MetadataVO) => (
        <Space>
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
          <h1>元数据管理</h1>
          <p>管理系统元数据配置</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增元数据</Button>
      </div>

      <div className="filter-bar">
        <Select
          value={filterCategory}
          onChange={setFilterCategory}
          placeholder="全部分类"
          allowClear
          style={{ width: 200 }}
          options={categories.map(c => ({ label: c, value: c }))}
        />
      </div>

      <Card className="table-card">
        <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }} />
      </Card>

      <Modal
        title={editingId ? '编辑元数据' : '新增元数据'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="category" label="分类" rules={[{ required: true }]}>
            <Select
              options={categories.map(c => ({ label: c, value: c }))}
              placeholder="选择或输入分类"
              showSearch
            />
          </Form.Item>
          <Form.Item name="code" label="编码" rules={[{ required: true }]}>
            <Input placeholder="请输入编码" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="请输入名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="请输入描述" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="isActive" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
