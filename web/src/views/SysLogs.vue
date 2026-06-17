<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">系统日志</h1>
        <div class="flex gap-2">
          <button @click="refresh" class="text-gray-400 text-sm">🔄</button>
          <button @click="exportLogs" class="text-blue-500 text-sm">导出</button>
          <span class="text-xs text-gray-400">{{ filtered.length }} 条</span>
        </div>
      </div>
    </div>
    <div class="p-4 space-y-3">
      <input v-model="search" type="text" placeholder="搜索日志内容..." class="w-full border rounded-lg px-3 py-2 text-sm" />

      <div v-if="logTypes.length > 1" class="flex gap-2 overflow-x-auto pb-1">
        <button @click="selectedType=''" :class="selectedType==='' ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600'"
          class="px-3 py-1 rounded-full text-xs whitespace-nowrap">全部</button>
        <button v-for="t in logTypes" :key="t" @click="selectedType=t"
          :class="selectedType===t ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600'"
          class="px-3 py-1 rounded-full text-xs whitespace-nowrap">{{ t }}</button>
      </div>

      <div v-if="!filtered.length && !loading" class="text-center text-gray-400 py-8">暂无日志</div>
      <div v-for="l in filtered" :key="l.id" class="bg-white rounded-xl p-3 shadow-sm">
        <div class="flex justify-between items-center mb-1">
          <span class="text-xs px-1.5 py-0.5 rounded font-medium"
            :class="typeClass(l.logType)">{{ l.logType }}</span>
          <span class="text-xs text-gray-400">{{ formatTime(l.createdTime) }}</span>
        </div>
        <div class="text-sm text-gray-600 break-all">{{ l.message }}</div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const logs = ref<any[]>([])
const loading = ref(true)
const search = ref('')
const selectedType = ref('')
let refreshTimer: ReturnType<typeof setInterval> | null = null

async function loadData() {
  try {
    const r = await api.get('/api/system/logs?limit=200')
    logs.value = r.data.logs || []
  } catch {}
  loading.value = false
}

async function refresh() {
  await loadData()
  ElMessage.success('已刷新')
}

onMounted(async () => {
  await loadData()
  refreshTimer = setInterval(loadData, 30_000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})

const logTypes = computed(() => {
  const types = new Set(logs.value.map(l => l.logType).filter(Boolean))
  return [...types].sort()
})

const filtered = computed(() => {
  let result = logs.value
  if (selectedType.value) {
    result = result.filter(l => l.logType === selectedType.value)
  }
  if (search.value.trim()) {
    const q = search.value.toLowerCase()
    result = result.filter(l =>
      (l.message || '').toLowerCase().includes(q) ||
      (l.logType || '').toLowerCase().includes(q)
    )
  }
  return result
})

function typeClass(type: string) {
  if (type === 'error') return 'bg-red-100 text-red-700'
  if (type === 'camera_error') return 'bg-orange-100 text-orange-700'
  if (type === 'daily_report') return 'bg-green-100 text-green-700'
  if (type === 'service_heartbeat') return 'bg-gray-100 text-gray-500'
  return 'bg-blue-100 text-blue-700'
}

function formatTime(ts: number) {
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString()
  return d.toLocaleDateString()
}

function exportLogs() {
  const data = filtered.value.map(l => `[${new Date(l.createdTime).toLocaleString()}] [${l.logType}] ${l.message}`).join('\n')
  const blob = new Blob([data], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `xiangqin_logs_${new Date().toISOString().slice(0,10)}.txt`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('日志已导出')
}
</script>
