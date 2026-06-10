import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, message } from 'antd'
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { UserRegisterRequest } from '@/types'
import './Auth.scss'

export default function Register() {
  const navigate = useNavigate()
  const { setUserInfo } = useUserStore()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: UserRegisterRequest) => {
    setLoading(true)
    try {
      const user = await authApi.register(values)
      setUserInfo(user)
      message.success('注册成功')
      navigate('/')
    } catch (error) {
      console.error('Register failed:', error)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-header">
          <div className="auth-logo">
            <span className="logo-icon">V</span>
            <span className="logo-text">Victor AI</span>
          </div>
          <h1>创建账号</h1>
          <p>注册一个新账号开始使用</p>
        </div>
        <Form
          name="register"
          onFinish={onFinish}
          autoComplete="off"
          size="large"
          className="auth-form"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item
            name="email"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '请输入有效的邮箱地址' }
            ]}
          >
            <Input prefix={<MailOutlined />} placeholder="邮箱" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少6个字符' }
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item
            name="nickname"
          >
            <Input prefix={<UserOutlined />} placeholder="昵称（可选）" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              注册
            </Button>
          </Form.Item>
          <div className="auth-footer">
            已有账号？<Link to="/login">立即登录</Link>
          </div>
        </Form>
      </div>
    </div>
  )
}
