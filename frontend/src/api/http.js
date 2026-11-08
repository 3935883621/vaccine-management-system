import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

/**
 * 统一 axios 实例：
 * - 请求拦截：自动附加 JWT（Authorization: Bearer）
 * - 响应拦截：code!==200 时弹出错误提示；401 清除登录态并跳转登录页
 */
const http = axios.create({ timeout: 15000 })

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const res = resp.data
    if (res && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    const res = error.response?.data
    const msg = res?.message || error.message || '网络异常'
    if (status === 401) {
      const auth = useAuthStore()
      auth.logout()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
        return Promise.reject(error)
      }
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default http
