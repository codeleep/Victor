import { useNavigate, useLocation } from 'react-router-dom'
import {
  HomeOutlined,
  BarChartOutlined,
  QuestionCircleOutlined,
  FileSearchOutlined,
  ProfileOutlined,
  SoundOutlined,
  DatabaseOutlined,
  KeyOutlined,
  RobotOutlined,
} from '@ant-design/icons'
import './AppSidebar.scss'

interface MenuItem {
  key: string
  icon: React.ReactNode
  label: string
  path: string
}

interface MenuSection {
  key: string
  label: string
  children: MenuItem[]
}

type MenuEntry = MenuItem | MenuSection

function isSection(entry: MenuEntry): entry is MenuSection {
  return 'children' in entry
}

const menuItems: MenuEntry[] = [
  { key: 'dashboard', icon: <HomeOutlined />, label: '仪表盘', path: '/' },
  {
    key: 'interview',
    label: '面试管理',
    children: [
      { key: 'interview-records', icon: <BarChartOutlined />, label: '面试记录', path: '/interview' }
    ]
  },
  {
    key: 'resource',
    label: '资源管理',
    children: [
      { key: 'questions', icon: <QuestionCircleOutlined />, label: '题库管理', path: '/resource/questions' },
      { key: 'resume', icon: <FileSearchOutlined />, label: '简历管理', path: '/resource/resume' },
      { key: 'experience', icon: <ProfileOutlined />, label: '经历管理', path: '/resource/experience' },
      { key: 'jobs', icon: <ProfileOutlined />, label: '岗位库', path: '/resource/jobs' },
    ]
  },
  {
    key: 'settings',
    label: '系统设置',
    children: [
      { key: 'ai-config', icon: <RobotOutlined />, label: 'AI 配置', path: '/settings/ai' },
      { key: 'voice-config', icon: <SoundOutlined />, label: '语音配置', path: '/settings/voice' },
      { key: 'metadata', icon: <DatabaseOutlined />, label: '元数据管理', path: '/settings/metadata' },
      { key: 'api-key', icon: <KeyOutlined />, label: 'API Key', path: '/settings/api-key' },
    ]
  }
]

export default function AppSidebar() {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <aside className="app-sidebar">
      <div className="sidebar-menu">
        {menuItems.map(entry => {
          if (isSection(entry)) {
            return (
              <div key={entry.key} className="menu-section">
                <div className="menu-section-label">{entry.label}</div>
                {entry.children.map(child => (
                  <div
                    key={child.key}
                    className={`menu-item ${location.pathname === child.path ? 'active' : ''}`}
                    onClick={() => navigate(child.path)}
                  >
                    <span className="menu-icon">{child.icon}</span>
                    <span className="menu-label">{child.label}</span>
                  </div>
                ))}
              </div>
            )
          }
          return (
            <div
              key={entry.key}
              className={`menu-item ${location.pathname === entry.path ? 'active' : ''}`}
              onClick={() => navigate(entry.path)}
            >
              <span className="menu-icon">{entry.icon}</span>
              <span className="menu-label">{entry.label}</span>
            </div>
          )
        })}
      </div>
      <div className="sidebar-version">v{__APP_VERSION__}</div>
    </aside>
  )
}
