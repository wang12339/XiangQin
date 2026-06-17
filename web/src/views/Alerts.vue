<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10 flex justify-between items-center">
      <h1 class="text-lg font-bold">告警</h1>
      <div class="flex gap-3">
        <button @click="$router.push('/alertsettings')" class="text-blue-500 text-sm">设置</button>
        <button @click="ackAll" class="text-blue-500 text-sm">全部确认</button>
      </div>
    </div>

    <div class="p-4 space-y-4">
      <div v-if="summary" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="grid grid-cols-3 gap-3 text-center">
          <div class="bg-red-50 rounded-lg p-2">
            <div class="text-lg font-bold text-red-600">{{ summary.critical }}</div>
            <div class="text-xs text-gray-500">严重</div>
          </div>
          <div class="bg-yellow-50 rounded-lg p-2">
            <div class="text-lg font-bold text-yellow-600">{{ summary.warning }}</div>
            <div class="text-xs text-gray-500">警告</div>
          </div>
          <div class="bg-blue-50 rounded-lg p-2">
            <div class="text-lg font-bold text-blue-600">{{ summary.info }}</div>
            <div class="text-xs text-gray-500">提示</div>
          </div>
        </div>
      </div>

      <div v-if="alerts.length" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">近7日趋势</div>
        <div class="flex items-end gap-1" style="height: 80px;">
          <div v-for="(d, i) in dailyAlerts" :key="i" class="flex-1 flex flex-col items-center gap-0.5">
            <div class="w-full bg-red-400 rounded-t" :style="{ height: barHeight(d.count, maxAlerts) + 'px' }"></div>
            <div class="text-[10px] text-gray-400">{{ d.date }}</div>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">告警记录 ({{ alerts.length }})</div>
        <div v-if="!alerts.length && !loading" class="text-center text-gray-400 py-8">暂无告警</div>
        <div v-for="a in alerts" :key="a.id" class="bg-gray-50 rounded-lg p-3 mb-2">
          <div class="flex justify-between items-start">
            <span class="font-medium text-sm">{{ a.title }}</span>
            <span class="text-xs px-1.5 py-0.5 rounded" :class="severityClass(a.severity)">{{ a.severity }}</span>
          </div>
          <div class="text-sm text-gray-600 mt-1">{{ a.message }}</div>
          <div class="flex justify-between items-center mt-2">
            <span class="text-xs text-gray-400">{{ new Date(a.triggeredTime).toLocaleString() }}</span>
            <button v-if="!a.acknowledged" @click="ack(a.id)" class="text-xs text-blue-500">确认</button>
            <span v-else class="text-xs text-green-500">已确认</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'

const alerts = ref<any[]>([])
const loading = ref(true)
const summary = ref<any>(null)

const dailyAlerts = computed(() => {
  const map = new Map<string, number>()
  alerts.value.forEach(a => {
    const d = new Date(a.triggeredTime).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
    map.set(d, (map.get(d) || 0) + 1)
  })
  return Array.from(map.entries()).map(([date, count]) => ({ date, count })).slice(-7)
})

const maxAlerts = computed(() => Math.max(...dailyAlerts.value.map(d => d.count), 1))

function barHeight(val: number, max: number) {
  return Math.max((val / max) * 70, 4)
}

onMounted(async () => {
  try {
    const res = await api.get('/api/alerts?days=7')
    alerts.value = res.data.alerts || []
    const crit = alerts.value.filter(a => a.severity === 'critical').length
    const warn = alerts.value.filter(a => a.severity === 'warning').length
    const info = alerts.value.filter(a => a.severity === 'info').length
    summary.value = { critical: crit, warning: warn, info: info }
  } catch {}
  loading.value = false
})

async function ack(id: number) {
  await api.post(`/api/alerts/acknowledge/${id}`)
  alerts.value = alerts.value.map(a => a.id === id ? { ...a, acknowledged: true } : a)
}

async function ackAll() {
  await api.post('/api/alerts/acknowledge-all')
  alerts.value = alerts.value.map(a => ({ ...a, acknowledged: true }))
}

function severityClass(s: string) {
  if (s === 'critical') return 'bg-red-100 text-red-700'
  if (s === 'warning') return 'bg-amber-100 text-amber-700'
  return 'bg-blue-100 text-blue-700'
}
</script>
