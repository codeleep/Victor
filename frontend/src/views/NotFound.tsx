import { useNavigate } from 'react-router-dom'
import { Button, Result } from 'antd'

export default function NotFound() {
  const navigate = useNavigate()

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
      <Result
        status="404"
        title="404"
        subTitle="抱歉，你访问的页面不存在"
        extra={<Button type="primary" onClick={() => navigate('/')}>返回首页</Button>}
      />
    </div>
  )
}
