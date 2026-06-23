import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, message } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { UserLoginRequest } from '@/types'
import './Auth.scss'

export default function Login() {
  const navigate = useNavigate()
  const { setUserInfo } = useUserStore()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: UserLoginRequest) => {
    setLoading(true)
    try {
      const user = await authApi.login(values)
      setUserInfo(user)
      message.success('登录成功')
      navigate('/')
    } catch (error) {
      console.error('Login failed:', error)
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
          <h1>欢迎回来</h1>
          <p>登录你的账号继续使用</p>
        </div>
        <Form
          name="login"
          initialValues={{ username: 'admin', password: 'admin123' }}
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
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
          <div className="auth-footer">
            还没有账号？<Link to="/register">立即注册</Link>
          </div>
        </Form>
        <div className="auth-version">v{__APP_VERSION__}</div>
      </div>
    </div>
  )
}
