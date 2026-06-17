<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">通知记录</h1>
        <span class="text-xs text-gray-400">{{ filtered.length }} 条</span>
      </div>
    </div>
    <div class="p-4 space-y-3">
      <input v-model="search" type="text" placeholder="搜索通知内容..." class="w-full border rounded-lg px-3 py-2 text-sm" />

      <div v-if="appNames.length > 1" class="flex gap-2 overflow-x-auto pb-1">
        <button @click="selectedApp=''" :class="selectedApp==='' ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600'"
          class="px-3 py-1 rounded-full text-xs whitespace-nowrap">全部</button>
        <button v-for="app in appNames" :key="app" @click="selectedApp=app"
          :class="selectedApp===app ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600'"
          class="px-3 py-1 rounded-full text-xs whitespace-nowrap">{{ app }}</button>
      </div>

      <div v-if="!filtered.length && !loading" class="text-center text-gray-400 py-8">暂无通知</div>
      <div v-for="n in filtered" :key="n.id" @click="markRead(n)"
        class="bg-white rounded-xl p-3 shadow-sm transition"
        :class="n.read ? 'opacity-70' : 'border-l-3 border-l-blue-500'">
        <div class="flex justify-between items-start">
          <div class="flex items-center gap-2">
            <span v-if="!n.read" class="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0"></span>
            <span class="font-medium text-sm">{{ n.appName || n.packageName }}</span>
          </div>
          <span class="text-xs text-gray-400">{{ formatTime(n.postTime) }}</span>
        </div>
        <div v-if="n.title" class="text-sm font-medium mt-1">{{ n.title }}</div>
        <div v-if="n.text" class="text-xs text-gray-500 mt-0.5 line-clamp-2">{{ n.text }}</div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'

const list = ref<any[]>([])
const loading = ref(true)
const search = ref('')
const selectedApp = ref('')

onMounted(async () => {
  list.value = (await api.get('/api/notifications?limit=200')).data.notifications || []
  loading.value = false
})

const appNames = computed(() => {
  const names = new Set(list.value.map(n => n.appName || n.packageName).filter(Boolean))
  return [...names].sort()
})

const filtered = computed(() => {
  let result = list.value
  if (selectedApp.value) {
    result = result.filter(n => (n.appName || n.packageName) === selectedApp.value)
  }
  if (search.value.trim()) {
    const q = search.value.toLowerCase()
    result = result.filter(n =>
      (n.title || '').toLowerCase().includes(q) ||
      (n.text || '').toLowerCase().includes(q) ||
      (n.appName || '').toLowerCase().includes(q)
    )
  }
  return result
})

async function markRead(n: any) {
  if (n.read) return
  try {
    await api.post(`/api/notifications/read/${n.id}`)
    n.read = true
  } catch {}
}

function formatTime(ts: number) {
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString()
  return d.toLocaleDateString()
}
</script>
