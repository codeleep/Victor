import { useNavigate, useLocation } from 'react-router-dom'
import { Button, Dropdown } from 'antd'
import { UserOutlined, LogoutOutlined } from '@ant-design/icons'
import { useUserStore } from '@/stores/user'
import './AppHeader.scss'

export default function AppHeader() {
  const navigate = useNavigate()
  const location = useLocation()
  const { userInfo, logout } = useUserStore()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const menuItems = [
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout }
  ]

  return (
    <header className="app-header">
      <div className="header-left">
        <div className="logo" onClick={() => navigate('/')}>
          <span className="logo-icon">V</span>
          <span className="logo-text">Victor AI</span>
        </div>
        <nav className="nav-links">
          <a
            className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}
            onClick={() => navigate('/')}
          >
            仪表盘
          </a>
          <a
            className={`nav-link ${location.pathname.startsWith('/interview') ? 'active' : ''}`}
            onClick={() => navigate('/interview')}
          >
            面试
          </a>
          <a
            className={`nav-link ${location.pathname.startsWith('/resource') ? 'active' : ''}`}
            onClick={() => navigate('/resource/questions')}
          >
            资源
          </a>
        </nav>
      </div>
      <div className="header-right">
        <Button type="primary" size="small" onClick={() => navigate('/interview/config')}>
          开始面试
        </Button>
        <Dropdown menu={{ items: menuItems }} placement="bottomRight">
          <div className="user-avatar">
            <UserOutlined />
            <span className="user-name">{userInfo?.nickname || userInfo?.username || '用户'}</span>
          </div>
        </Dropdown>
      </div>
    </header>
  )
}
