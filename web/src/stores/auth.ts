import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const password = ref(localStorage.getItem('password') || (localStorage.getItem('token') ? localStorage.getItem('token') : 'xiangqin123'))

  const isAuthenticated = ref(!!token.value || !!password.value)

  async function login(pwd: string) {
    const res = await api.post('/api/login', { username: 'admin', password: pwd })
    if (res.data.token) {
      token.value = res.data.token
      password.value = pwd
      localStorage.setItem('token', token.value)
      localStorage.setItem('password', pwd)
      isAuthenticated.value = true
      return true
    }
    return false
  }

  function logout() {
    token.value = ''
    password.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('password')
    isAuthenticated.value = false
  }

  // 认证失败时自动使用密码重新认证
  async function reauth() {
    if (password.value) {
      return await login(password.value)
    }
    return false
  }

  return { token, password, isAuthenticated, login, logout, reauth }
})
