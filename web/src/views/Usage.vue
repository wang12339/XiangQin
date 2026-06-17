<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10"><h1 class="text-lg font-bold">应用使用</h1></div>
    <div class="p-4 space-y-4">
      <div v-if="topApps.length" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">使用时间分布</div>
        <div class="space-y-2">
          <div v-for="(u, i) in topApps" :key="u.id" class="flex items-center gap-2">
            <span class="text-xs text-gray-400 w-4 text-right">{{ i + 1 }}</span>
            <div class="flex-1 min-w-0">
              <div class="flex justify-between items-center mb-1">
                <span class="text-xs font-medium truncate">{{ u.appName || u.packageName }}</span>
                <span class="text-xs font-bold text-blue-600">{{ fmtMs(u.totalTimeForeground) }}</span>
              </div>
              <div class="w-full bg-gray-100 rounded-full h-2">
                <div class="bg-blue-500 rounded-full h-2 transition-all" :style="{ width: barWidth(u.totalTimeForeground) + '%' }"></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="flex items-center justify-between mb-3">
          <span class="font-medium">全部应用 ({{ filtered.length }})</span>
          <select v-model="sortBy" class="text-xs border rounded px-2 py-1 text-gray-600">
            <option value="time">按时间</option>
            <option value="name">按名称</option>
            <option value="launches">按次数</option>
          </select>
        </div>
        <input v-model="search" type="text" placeholder="搜索应用..." class="w-full border rounded-lg px-3 py-2 text-sm mb-3" />
        <div v-if="!filtered.length && !loading" class="text-center text-gray-400 py-4">暂无数据</div>
        <div v-for="u in filtered" :key="u.id" class="flex justify-between items-center py-2 border-b border-gray-50 last:border-0">
          <div class="min-w-0 flex-1">
            <div class="font-medium text-sm truncate">{{ u.appName || u.packageName }}</div>
            <div class="text-xs text-gray-400">{{ fmtMs(u.totalTimeForeground) }} · {{ u.launchCount || 0 }}次</div>
          </div>
          <span class="text-sm font-bold text-blue-600 ml-2">{{ fmtMs(u.totalTimeForeground) }}</span>
        </div>
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
const sortBy = ref('time')

onMounted(async () => {
  list.value = (await api.get('/api/usage')).data || []
  loading.value = false
})

const filtered = computed(() => {
  let result = [...list.value]
  if (search.value.trim()) {
    const q = search.value.toLowerCase()
    result = result.filter(u =>
      (u.appName || '').toLowerCase().includes(q) ||
      (u.packageName || '').toLowerCase().includes(q)
    )
  }
  if (sortBy.value === 'time') result.sort((a, b) => (b.totalTimeForeground || 0) - (a.totalTimeForeground || 0))
  else if (sortBy.value === 'name') result.sort((a, b) => (a.appName || a.packageName || '').localeCompare(b.appName || b.packageName || ''))
  else if (sortBy.value === 'launches') result.sort((a, b) => (b.launchCount || 0) - (a.launchCount || 0))
  return result
})

const topApps = computed(() => {
  const sorted = [...list.value].sort((a, b) => (b.totalTimeForeground || 0) - (a.totalTimeForeground || 0))
  return sorted.slice(0, 10)
})

const maxTime = computed(() => {
  const times = list.value.map(u => u.totalTimeForeground || 0)
  return Math.max(...times, 1)
})

function barWidth(ms: number) {
  return Math.max((ms / maxTime.value) * 100, 1)
}

function fmtMs(ms: number) {
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  return h > 0 ? `${h}h${m}m` : `${m}m`
}
</script>
