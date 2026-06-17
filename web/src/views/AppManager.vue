<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center gap-2">
        <button @click="$router.back()" class="text-blue-500 text-sm">← 返回</button>
        <h1 class="text-lg font-bold flex-1">应用管理</h1>
        <span class="text-xs text-gray-400">{{ filtered.length }} 个</span>
      </div>
    </div>
    <div class="p-4 space-y-3">
      <input v-model="search" type="text" placeholder="搜索应用..." class="w-full border rounded-lg px-3 py-2 text-sm" />

      <div class="flex gap-2 mb-2">
        <button @click="showSystem = !showSystem" :class="showSystem ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600'"
          class="px-3 py-1 rounded-full text-xs">系统应用</button>
        <button @click="sortBy = sortBy === 'name' ? 'time' : 'name'"
          class="px-3 py-1 rounded-full text-xs bg-gray-100 text-gray-600">
          {{ sortBy === 'name' ? '按名称' : '按安装时间' }}
        </button>
      </div>

      <div v-if="!filtered.length && !loading" class="text-center text-gray-400 py-8">暂无应用</div>
      <div v-for="app in filtered" :key="app.packageName" class="bg-white rounded-xl p-3 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-gray-100 flex items-center justify-center text-lg">
            {{ app.isSystem ? '⚙️' : '📱' }}
          </div>
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm truncate">{{ app.appName }}</div>
            <div class="text-xs text-gray-400 truncate">{{ app.packageName }}</div>
            <div class="text-xs text-gray-400">v{{ app.versionName }}</div>
          </div>
          <div class="flex gap-1">
            <button v-if="!app.isSystem" @click="setLimit(app)"
              class="text-blue-400 text-xs px-2 py-1 rounded bg-blue-50">限制</button>
            <button v-if="!app.isSystem" @click="uninstall(app)"
              class="text-red-400 text-xs px-2 py-1 rounded bg-red-50">卸载</button>
          </div>
        </div>
        <div v-if="limits[app.packageName]" class="mt-2 p-2 bg-amber-50 rounded-lg flex items-center justify-between">
          <span class="text-xs text-amber-700">⏰ 每日限制: {{ limits[app.packageName] }} 分钟</span>
          <button @click="removeLimit(app.packageName)" class="text-xs text-red-500">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const apps = ref<any[]>([])
const loading = ref(true)
const search = ref('')
const showSystem = ref(false)
const sortBy = ref('name')
const limits = ref<Record<string, number>>({})

onMounted(async () => {
  try {
    const [appsRes, limitsRes] = await Promise.all([
      api.get('/api/apps'),
      api.get('/api/apps/limits').catch(() => ({ data: { limits: {} } }))
    ])
    apps.value = appsRes.data.apps || []
    limits.value = limitsRes.data.limits || {}
  } catch {}
  loading.value = false
})

const filtered = computed(() => {
  let result = apps.value
  if (!showSystem.value) {
    result = result.filter(a => !a.isSystem)
  }
  if (search.value.trim()) {
    const q = search.value.toLowerCase()
    result = result.filter(a =>
      (a.appName || '').toLowerCase().includes(q) ||
      (a.packageName || '').toLowerCase().includes(q)
    )
  }
  if (sortBy.value === 'time') {
    result = [...result].sort((a, b) => (b.installTime || 0) - (a.installTime || 0))
  }
  return result
})

function setLimit(app: any) {
  const current = limits.value[app.packageName] || 60
  const input = prompt(`设置 ${app.appName} 每日使用限制（分钟）:`, String(current))
  if (input === null) return
  const minutes = parseInt(input)
  if (isNaN(minutes) || minutes < 0) { ElMessage.error('请输入有效数字'); return }
  api.post('/api/apps/limit', { packageName: app.packageName, minutes })
    .then(() => {
      if (minutes > 0) limits.value[app.packageName] = minutes
      else delete limits.value[app.packageName]
      ElMessage.success('限制已更新')
    })
    .catch((e: any) => ElMessage.error(e.response?.data?.error || '更新失败'))
}

function removeLimit(packageName: string) {
  api.post('/api/apps/limit', { packageName, minutes: 0 })
    .then(() => { delete limits.value[packageName]; ElMessage.success('限制已取消') })
    .catch((e: any) => ElMessage.error(e.response?.data?.error || '取消失败'))
}

async function uninstall(app: any) {
  if (!confirm(`确定要卸载 ${app.appName} 吗？`)) return
  try {
    await api.post('/api/apps/uninstall', { packageName: app.packageName })
    ElMessage.success('已启动卸载')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '卸载失败')
  }
}
</script>
