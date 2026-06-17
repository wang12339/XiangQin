<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">日历事件</h1>
        <button @click="showAdd = !showAdd" class="text-blue-500 text-sm">+ 添加</button>
      </div>
    </div>
    <div class="p-4 space-y-3">
      <div v-if="showAdd" class="bg-white rounded-xl p-4 shadow-sm">
        <input v-model="newEvent.title" placeholder="事件标题" class="w-full border rounded-lg px-3 py-2 text-sm mb-2" />
        <input v-model="newEvent.date" type="date" class="w-full border rounded-lg px-3 py-2 text-sm mb-2" />
        <input v-model="newEvent.time" type="time" class="w-full border rounded-lg px-3 py-2 text-sm mb-2" />
        <select v-model="newEvent.reminder" class="w-full border rounded-lg px-3 py-2 text-sm mb-2">
          <option value="0">无提醒</option>
          <option value="5">5 分钟前</option>
          <option value="15">15 分钟前</option>
          <option value="30">30 分钟前</option>
          <option value="60">1 小时前</option>
          <option value="1440">1 天前</option>
        </select>
        <button @click="addEvent" :disabled="!newEvent.title"
          class="w-full py-2.5 bg-blue-500 text-white rounded-xl text-sm font-medium active:bg-blue-600 disabled:opacity-50">
          添加事件
        </button>
      </div>

      <input v-model="search" type="text" placeholder="搜索事件..." class="w-full border rounded-lg px-3 py-2 text-sm" />

      <div v-if="!filtered.length && !loading" class="text-center text-gray-400 py-8">暂无日历事件</div>
      <div v-for="e in filtered" :key="e.id" class="bg-white rounded-xl p-3 shadow-sm">
        <div class="flex justify-between items-start">
          <div class="font-medium text-sm">{{ e.eventTitle }}</div>
          <span v-if="isUpcoming(e.startTime)" class="text-xs px-1.5 py-0.5 rounded bg-green-100 text-green-600">即将到来</span>
        </div>
        <div class="text-xs text-gray-400 mt-1">
          {{ e.eventLocation ? e.eventLocation + ' · ' : '' }}{{ formatDate(e.startTime) }}
        </div>
        <div v-if="getDaysUntil(e.startTime) <= 3 && getDaysUntil(e.startTime) >= 0" class="text-xs text-amber-500 mt-1">
          ⏰ {{ getDaysUntil(e.startTime) === 0 ? '今天' : getDaysUntil(e.startTime) + ' 天后' }}
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const list = ref<any[]>([])
const loading = ref(true)
const showAdd = ref(false)
const search = ref('')
const newEvent = ref({ title: '', date: '', time: '', reminder: '0' })

onMounted(async () => {
  list.value = (await api.get('/api/calendar/events?days=30')).data.events || []
  loading.value = false
})

const filtered = computed(() => {
  if (!search.value.trim()) return list.value
  const q = search.value.toLowerCase()
  return list.value.filter(e =>
    (e.eventTitle || '').toLowerCase().includes(q) ||
    (e.eventLocation || '').toLowerCase().includes(q)
  )
})

function isUpcoming(ts: number) {
  const diff = ts - Date.now()
  return diff > 0 && diff < 7 * 24 * 60 * 60 * 1000
}

function getDaysUntil(ts: number) {
  return Math.ceil((ts - Date.now()) / (24 * 60 * 60 * 1000))
}

function formatDate(ts: number) {
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return '今天 ' + d.toLocaleTimeString()
  if (d.toDateString() === new Date(now.getTime() + 86400000).toDateString()) return '明天 ' + d.toLocaleTimeString()
  return d.toLocaleString()
}

async function addEvent() {
  try {
    const dt = newEvent.value.date && newEvent.value.time ? new Date(`${newEvent.value.date}T${newEvent.value.time}`) : new Date()
    await api.post('/api/calendar/add', { title: newEvent.value.title, startTime: dt.getTime() })
    ElMessage.success('事件已添加')
    showAdd.value = false
    newEvent.value = { title: '', date: '', time: '', reminder: '0' }
    list.value = (await api.get('/api/calendar/events?days=30')).data.events || []
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '添加失败') }
}
</script>
