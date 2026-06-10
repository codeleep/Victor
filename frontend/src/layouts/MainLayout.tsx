import { useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import AppHeader from '@/components/layout/AppHeader'
import AppSidebar from '@/components/layout/AppSidebar'
import { useUserStore } from '@/stores/user'
import './MainLayout.scss'

export default function MainLayout() {
  const { userInfo, loadUserInfo } = useUserStore()

  useEffect(() => {
    if (!userInfo) {
      loadUserInfo()
    }
  }, [userInfo, loadUserInfo])

  return (
    <div className="main-layout">
      <AppHeader />
      <div className="layout-body">
        <AppSidebar />
        <main className="layout-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
