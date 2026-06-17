<template>
  <div class="min-h-screen bg-gradient-to-b from-blue-500 to-blue-700 flex items-center justify-center p-6">
    <div class="w-full max-w-sm">
      <div class="text-center mb-8 animate-fade-in">
        <div class="text-5xl mb-3 animate-bounce">🏠</div>
        <h1 class="text-2xl font-bold text-white">乡亲</h1>
        <p class="text-blue-100 text-sm mt-1">家庭守护 · 安心相伴</p>
      </div>
      <div class="bg-white rounded-2xl p-6 shadow-xl animate-slide-up">
        <div class="space-y-4">
          <div>
            <label class="text-sm text-gray-500 mb-1 block">用户名</label>
            <input v-model="form.username" class="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition" />
          </div>
          <div>
            <label class="text-sm text-gray-500 mb-1 block">密码</label>
            <div class="relative">
              <input v-model="form.password" :type="showPw ? 'text' : 'password'"
                class="w-full border border-gray-200 rounded-xl px-4 py-3 pr-12 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition"
                @keyup.enter="handleLogin" />
              <button @click="showPw = !showPw" class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">
                {{ showPw ? '隐藏' : '显示' }}
              </button>
            </div>
          </div>
          <button @click="handleLogin" :disabled="loading || !form.password"
            class="w-full py-3 bg-blue-500 text-white rounded-xl font-medium text-base active:bg-blue-600 disabled:opacity-50 transition relative overflow-hidden">
            <span :class="loading ? 'opacity-0' : ''">登录</span>
            <div v-if="loading" class="absolute inset-0 flex items-center justify-center">
              <div class="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
            </div>
          </button>
        </div>
        <div class="mt-4 text-center">
          <p class="text-xs text-gray-400">密码可在手机 App 面板中查看</p>
        </div>
      </div>
      <div class="text-center mt-6 animate-fade-in">
        <button @click="$router.push('/about')" class="text-blue-200 text-xs hover:text-white transition">关于乡亲</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const showPw = ref(false)
const form = reactive({ username: 'admin', password: '' })

async function handleLogin() {
  if (!form.password || loading.value) return
  loading.value = true
  try { await auth.login(form.password); router.push('/') }
  catch { ElMessage.error('登录失败，请检查密码') }
  finally { loading.value = false }
}
</script>

<style scoped>
@keyframes fade-in {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in { animation: fade-in 0.5s ease-out; }
.animate-slide-up { animation: slide-up 0.5s ease-out 0.2s both; }
</style>
