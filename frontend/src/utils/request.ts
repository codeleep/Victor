import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { message } from 'antd'
import type { Result } from '@/types'

const instance: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor
instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor
instance.interceptors.response.use(
  (response: AxiosResponse<Result<unknown>>) => {
    // 非 JSON 响应(文件下载 blob / 纯文本 text 等)不走统一 Result 拆包,直接放行
    const responseType = response.config.responseType
    if (responseType === 'blob' || responseType === 'text' || responseType === 'arraybuffer' || responseType === 'document') {
      return response
    }
    const { data } = response
    if (data.code === 0 || data.code === 200) {
      return response
    } else {
      message.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
  },
  (error) => {
    if (error.response) {
      const { status } = error.response
      if (status === 401) {
        localStorage.removeItem('token')
        window.location.href = '/login'
        message.error('登录已过期，请重新登录')
      } else if (status === 403) {
        message.error('没有权限访问')
      } else if (status === 404) {
        message.error('请求的资源不存在')
      } else if (status === 500) {
        message.error('服务器内部错误')
      } else {
        message.error(error.response.data?.message || '请求失败')
      }
    } else {
      message.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

const request = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config).then((res) => {
      const responseType = config?.responseType
      // blob/text 等原始响应直接返回 body,不再拆 Result.data
      if (responseType === 'blob' || responseType === 'text' || responseType === 'arraybuffer' || responseType === 'document') {
        return res.data as T
      }
      return res.data.data as T
    })
  },

  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config).then((res) => res.data.data as T)
  },

  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config).then((res) => res.data.data as T)
  },

  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config).then((res) => res.data.data as T)
  },

  upload<T>(url: string, file: File, data?: Record<string, unknown>): Promise<T> {
    const formData = new FormData()
    formData.append('file', file)
    if (data) {
      Object.entries(data).forEach(([key, value]) => {
        formData.append(key, String(value))
      })
    }
    return instance.post(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }).then((res) => res.data.data as T)
  }
}

export { instance }
export default request
