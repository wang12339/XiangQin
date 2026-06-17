import axios from 'axios'
import { ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'
import router from '../router'

const requestCount = ref(0)
const isLoading = ref(false)

const api = axios.create({
  baseURL: '',
  timeout: 30000
})

api.interceptors.request.use(
  (config) => {
    requestCount.value++
    isLoading.value = true
    
    const auth = useAuthStore()
    const pwd = auth.password || 'xiangqin123'
    const credentials = btoa(`admin:${pwd}`)
    config.headers.Authorization = `Basic ${credentials}`
    
    return config
  },
  (err) => Promise.reject(err)
)

api.interceptors.response.use(
  (res) => {
    requestCount.value--
    if (requestCount.value <= 0) { requestCount.value = 0; isLoading.value = false }
    return res
  },
  async (err) => {
    requestCount.value--
    if (requestCount.value <= 0) { requestCount.value = 0; isLoading.value = false }

    const auth = useAuthStore()
    
    if (err.response?.status === 401 && auth.password) {
      try {
        const success = await auth.reauth()
        if (success) {
          return api.request(err.config)
        }
      } catch (e) {
      }
      auth.logout()
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else if (err.response?.status >= 500) {
      ElMessage.error('服务器错误，请稍后重试')
    } else if (!err.response) {
      ElMessage.error('网络连接失败')
    }
    return Promise.reject(err)
  }
)

export { isLoading }
export { api }
export default api

export function createWs() {
  const auth = useAuthStore()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const ws = new WebSocket(`${protocol}//${location.host}/ws`)
  const credentials = btoa(`admin:${auth.password}`)
  ws.onopen = () => ws.send(`auth:${credentials}`)
  return ws
}
