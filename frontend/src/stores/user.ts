import { create } from 'zustand'
import type { UserVO } from '@/types'

interface UserState {
  token: string | null
  userInfo: UserVO | null
  isLoggedIn: boolean
  setToken: (token: string) => void
  clearToken: () => void
  setUserInfo: (info: UserVO) => void
  loadUserInfo: () => Promise<void>
  logout: () => void
}

export const useUserStore = create<UserState>((set, get) => ({
  token: localStorage.getItem('token'),
  userInfo: null,
  isLoggedIn: !!localStorage.getItem('token'),

  setToken: (token: string) => {
    localStorage.setItem('token', token)
    set({ token, isLoggedIn: true })
  },

  clearToken: () => {
    localStorage.removeItem('token')
    set({ token: null, userInfo: null, isLoggedIn: false })
  },

  setUserInfo: (info: UserVO) => {
    if (info.token) {
      localStorage.setItem('token', info.token)
    }
    set({ userInfo: info, token: info.token || localStorage.getItem('token'), isLoggedIn: true })
  },

  loadUserInfo: async () => {
    const token = get().token
    if (!token) return
    try {
      const { userApi } = await import('@/api')
      const user = await userApi.getCurrentUser()
      set({ userInfo: user })
    } catch (error) {
      console.error('Failed to load user info:', error)
      // Token might be invalid
      get().clearToken()
    }
  },

  logout: () => {
    localStorage.removeItem('token')
    set({ token: null, userInfo: null, isLoggedIn: false })
  }
}))
