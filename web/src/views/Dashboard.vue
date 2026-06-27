<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10 flex justify-between items-center">
      <h1 class="text-lg font-bold">总览</h1>
      <span class="text-xs px-2 py-1 rounded-full" :class="wsConnected ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'">
        {{ wsConnected ? '实时' : '离线' }}
      </span>
    </div>

    <div class="p-4 space-y-4">
      <div v-if="loading" class="space-y-3">
        <div class="grid grid-cols-2 gap-3">
          <div v-for="i in 4" :key="i" class="bg-white rounded-xl p-4 shadow-sm animate-pulse">
            <div class="h-8 bg-gray-200 rounded w-16 mx-auto mb-2"></div>
            <div class="h-3 bg-gray-100 rounded w-12 mx-auto"></div>
          </div>
        </div>
      </div>

      <template v-else>
        <div class="grid grid-cols-2 gap-3">
          <div v-for="s in stats" :key="s.label" class="bg-white rounded-xl p-4 shadow-sm text-center">
            <div class="text-2xl font-bold" :style="{color: s.color}">{{ s.value }}</div>
            <div class="text-xs text-gray-400 mt-1">{{ s.label }}</div>
          </div>
        </div>

        <div class="bg-white rounded-xl p-3 shadow-sm">
          <div class="grid grid-cols-4 gap-2">
            <button v-for="q in quickActions" :key="q.path" @click="$router.push(q.path)"
              class="flex flex-col items-center py-2 rounded-lg active:bg-gray-100 transition">
              <span class="text-xl">{{ q.icon }}</span>
              <span class="text-[10px] text-gray-500 mt-1">{{ q.label }}</span>
            </button>
          </div>
        </div>

        <div v-if="dailyData.length" class="bg-white rounded-xl p-4 shadow-sm">
          <div class="font-medium mb-3">近7日趋势</div>
          <div class="flex items-end gap-1" style="height: 120px;">
            <div v-for="(d, i) in dailyData" :key="i" class="flex-1 flex flex-col items-center gap-0.5">
              <div class="w-full flex gap-0.5 items-end" :style="{ height: '100px' }">
                <div class="flex-1 rounded-t" :style="{ height: barHeight(d.calls, maxVal) + 'px', background: '#3b82f6' }"></div>
                <div class="flex-1 rounded-t" :style="{ height: barHeight(d.sms, maxVal) + 'px', background: '#22c55e' }"></div>
              </div>
              <div class="text-[10px] text-gray-400">{{ d.date }}</div>
            </div>
          </div>
          <div class="flex justify-center gap-4 mt-2 text-xs text-gray-500">
            <span class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-blue-500 inline-block"></span>通话</span>
            <span class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-green-500 inline-block"></span>短信</span>
          </div>
        </div>

        <div v-if="battery" class="bg-white rounded-xl p-4 shadow-sm">
          <div class="flex items-center justify-between mb-2">
            <span class="font-medium">🔋 电池状态</span>
            <span class="text-xs" :class="battery.isCharging ? 'text-green-500' : 'text-gray-400'">
              {{ battery.isCharging ? '⚡ 充电中' : '未充电' }}
            </span>
          </div>
          <div class="flex items-center gap-3">
            <div class="flex-1 h-4 bg-gray-100 rounded-full overflow-hidden">
              <div class="h-full rounded-full transition-all" :class="batteryColor" :style="{ width: battery.level + '%' }"></div>
            </div>
            <span class="text-sm font-bold" :class="batteryColor.replace('bg-', 'text-')">{{ battery.level }}%</span>
          </div>
        </div>

        <div class="bg-white rounded-xl p-4 shadow-sm">
          <div class="flex items-center justify-between mb-3">
            <span class="font-medium">最新通话</span>
            <button @click="$router.push('/calls')" class="text-blue-500 text-sm">查看全部</button>
          </div>
          <div v-if="!data.latestCalls?.length" class="text-gray-400 text-sm py-4 text-center">暂无数据</div>
          <div v-for="c in data.latestCalls?.slice(0,3)" :key="c.id" class="flex items-center justify-between py-2 border-b border-gray-50 last:border-0">
            <div>
              <div class="font-medium text-sm">{{ c.callerName || c.phoneNumber }}</div>
              <div class="text-xs text-gray-400">{{ new Date(c.callTime).toLocaleTimeString() }}</div>
            </div>
            <span class="text-xs px-2 py-0.5 rounded" :class="c.callType===3?'bg-red-50 text-red-600':'bg-gray-50 text-gray-600'">
              {{ ['','来电','去电','未接'][c.callType] }}
            </span>
          </div>
        </div>

        <div class="bg-white rounded-xl p-4 shadow-sm">
          <div class="flex items-center justify-between mb-3">
            <span class="font-medium">最新短信</span>
            <button @click="$router.push('/sms')" class="text-blue-500 text-sm">查看全部</button>
          </div>
          <div v-if="!data.latestSms?.length" class="text-gray-400 text-sm py-4 text-center">暂无短信</div>
          <div v-for="s in data.latestSms?.slice(0,3)" :key="s.id" class="py-2 border-b border-gray-50 last:border-0">
            <div class="flex justify-between">
              <span class="font-medium text-sm">{{ s.senderName || s.phoneNumber }}</span>
              <span class="text-xs text-gray-400">{{ new Date(s.receivedTime).toLocaleTimeString() }}</span>
            </div>
            <div class="text-sm text-gray-600 truncate mt-0.5">{{ s.body }}</div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, createWs } from '../api/client'
import { getCached, setCache } from '../utils/cache'

const data = ref<any>({})
const stats = ref<any[]>([])
const dailyData = ref<any[]>([])
const battery = ref<any>(null)
const wsConnected = ref(false)
const loading = ref(true)
let ws: WebSocket | null = null
let refreshTimer: ReturnType<typeof setInterval> | null = null

const quickActions = [
  { path: '/remote', icon: '📸', label: '截屏' },
  { path: '/remotecall', icon: '📞', label: '拨号' },
  { path: '/remotesms', icon: '💬', label: '短信' },
  { path: '/alerts', icon: '🚨', label: '告警' },
]

const maxVal = computed(() => {
  const vals = dailyData.value.map(d => Math.max(d.calls, d.sms))
  return Math.max(...vals, 1)
})

const batteryColor = computed(() => {
  const level = battery.value?.level || 0
  if (level <= 10) return 'bg-red-500'
  if (level <= 20) return 'bg-orange-500'
  if (level <= 50) return 'bg-yellow-500'
  return 'bg-green-500'
})

function barHeight(val: number, max: number) {
  return Math.max((val / max) * 100, 2)
}

async function loadData() {
  const cachedSummary = getCached<any>('dashboard_summary')
  const cachedDaily = getCached<any>('dashboard_daily')
  if (cachedSummary && cachedDaily) {
    data.value = cachedSummary
    dailyData.value = cachedDaily.days || []
    stats.value = [
      { label: '今日通话', value: cachedSummary.callCount, color: '#3b82f6' },
      { label: '今日短信', value: cachedSummary.smsCount, color: '#22c55e' },
      { label: '今日告警', value: cachedSummary.alertToday, color: '#ef4444' },
      { label: '位置点', value: cachedSummary.locationCount, color: '#f59e0b' },
    ]
  }
  try {
    const [summaryRes, dailyRes, deviceRes] = await Promise.all([
      api.get('/api/stats/summary'),
      api.get('/api/stats/daily?days=7'),
      api.get('/api/device/info').catch(() => ({ data: {} }))
    ])
    data.value = summaryRes.data
    wsConnected.value = true
    stats.value = [
      { label: '今日通话', value: summaryRes.data.callCount, color: '#3b82f6' },
      { label: '今日短信', value: summaryRes.data.smsCount, color: '#22c55e' },
      { label: '今日告警', value: summaryRes.data.alertToday, color: '#ef4444' },
      { label: '位置点', value: summaryRes.data.locationCount, color: '#f59e0b' },
    ]
    dailyData.value = dailyRes.data.days || []
    battery.value = { level: deviceRes.data.batteryLevel || 0, isCharging: deviceRes.data.isCharging || false }
    setCache('dashboard_summary', summaryRes.data)
    setCache('dashboard_daily', dailyRes.data)
  } catch { wsConnected.value = false }
  loading.value = false
}

function handleWsMessage(event: MessageEvent) {
  try {
    const msg = JSON.parse(event.data)
    if (msg.type === 'call_updated' || msg.type === 'sms_updated' || msg.type === 'alert_new') {
      loadData()
    }
  } catch {}
}

onMounted(async () => {
  await loadData()
  let reconnectDelay = 1000
  const maxDelay = 30000
  try {
    ws = createWs()
    ws.onmessage = handleWsMessage
    ws.onopen = () => { wsConnected.value = true; reconnectDelay = 1000 }
    ws.onclose = () => {
      wsConnected.value = false
      setTimeout(() => {
        if (!ws || ws.readyState === WebSocket.CLOSED) {
          try { ws = createWs(); ws.onmessage = handleWsMessage; ws.onopen = () => { wsConnected.value = true; reconnectDelay = 1000 } } catch {}
        }
      }, reconnectDelay)
      reconnectDelay = Math.min(reconnectDelay * 2, maxDelay)
    }
    ws.onerror = () => {}
  } catch {}
  refreshTimer = setInterval(loadData, 30_000)
})

onUnmounted(() => {
  ws?.close()
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>
