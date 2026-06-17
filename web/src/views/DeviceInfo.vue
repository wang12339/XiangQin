<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">设备信息</h1>
        <button @click="refresh" class="text-gray-400 text-sm">🔄</button>
      </div>
    </div>
    <div class="p-4 space-y-3">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📱 设备</div>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span class="text-gray-500">品牌</span><span>{{ info.brand }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">型号</span><span>{{ info.modelName }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">设备名</span><span>{{ info.device }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">Android</span><span>{{ info.androidVersion }} (API {{ info.sdkVersion }})</span></div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🔋 电池</div>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between items-center"><span class="text-gray-500">电量</span>
            <div class="flex items-center gap-2">
              <div class="w-24 h-3 bg-gray-200 rounded-full overflow-hidden">
                <div class="h-full rounded-full transition-all" :class="batteryColor" :style="{width: (info.batteryLevel||0)+'%'}"></div>
              </div>
              <span class="font-medium">{{ info.batteryLevel }}%</span>
            </div>
          </div>
          <div class="flex justify-between"><span class="text-gray-500">充电状态</span>
            <span :class="info.isCharging ? 'text-green-600' : 'text-gray-600'">{{ info.isCharging ? '⚡ 充电中' : '未充电' }}</span>
          </div>
        </div>
        <div v-if="batteryHistory.length > 1" class="mt-3 pt-3 border-t border-gray-100">
          <div class="text-xs text-gray-400 mb-2">电量变化趋势</div>
          <div class="flex items-end gap-1" style="height: 40px;">
            <div v-for="(h, i) in batteryHistory" :key="i" class="flex-1 rounded-t transition-all"
              :class="h.level > 50 ? 'bg-green-400' : h.level > 20 ? 'bg-yellow-400' : 'bg-red-400'"
              :style="{ height: h.level + '%' }"></div>
          </div>
          <div class="flex justify-between text-[10px] text-gray-400 mt-1">
            <span>{{ batteryHistory[0]?.time }}</span>
            <span>{{ batteryHistory[batteryHistory.length-1]?.time }}</span>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📡 网络</div>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span class="text-gray-500">运营商</span><span>{{ info.simOperator || 'N/A' }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">手机号</span><span>{{ info.phoneNumber || 'N/A' }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">网络类型</span>
            <span :class="networkClass">{{ networkTypeLabel }}</span>
          </div>
          <div class="flex justify-between"><span class="text-gray-500">数据状态</span>
            <span :class="dataStateClass">{{ dataStateLabel }}</span>
          </div>
          <div class="flex justify-between"><span class="text-gray-500">漫游</span><span>{{ info.isNetworkRoaming ? '是' : '否' }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">连接质量</span>
            <span :class="signalClass">{{ signalLabel }}</span>
          </div>
        </div>
        <div class="mt-3 pt-3 border-t border-gray-100">
          <div class="flex items-center gap-2">
            <div class="w-3 h-3 rounded-full" :class="info.dataState === 2 ? 'bg-green-500 animate-pulse' : 'bg-red-500'"></div>
            <span class="text-xs" :class="info.dataState === 2 ? 'text-green-600' : 'text-red-500'">
              {{ info.dataState === 2 ? '网络连接正常' : '网络未连接' }}
            </span>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🔐 标识</div>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span class="text-gray-500">IMEI</span><span class="text-xs font-mono">{{ info.imei || 'N/A' }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">SIM 序列号</span><span class="text-xs font-mono">{{ info.simSerial || 'N/A' }}</span></div>
        </div>
      </div>

      <div v-if="serviceInfo" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">⚙️ 监控服务</div>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span class="text-gray-500">服务状态</span>
            <span :class="serviceInfo.serviceRunning ? 'text-green-600' : 'text-red-600'">{{ serviceInfo.serviceRunning ? '运行中' : '已停止' }}</span>
          </div>
          <div class="flex justify-between"><span class="text-gray-500">运行时间</span><span>{{ formatUptime }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">局域网</span><span>{{ serviceInfo.localIp }}:{{ serviceInfo.port }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">设备管理器</span>
            <span :class="serviceInfo.deviceAdminActive ? 'text-green-600' : 'text-gray-500'">{{ serviceInfo.deviceAdminActive ? '已激活' : '未激活' }}</span>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📊 快速统计</div>
        <div class="grid grid-cols-2 gap-3">
          <div class="bg-blue-50 rounded-lg p-3 text-center">
            <div class="text-lg font-bold text-blue-600">{{ stats.callsToday }}</div>
            <div class="text-xs text-gray-500">今日通话</div>
          </div>
          <div class="bg-green-50 rounded-lg p-3 text-center">
            <div class="text-lg font-bold text-green-600">{{ stats.smsToday }}</div>
            <div class="text-xs text-gray-500">今日短信</div>
          </div>
          <div class="bg-amber-50 rounded-lg p-3 text-center">
            <div class="text-lg font-bold text-amber-600">{{ stats.locationCount }}</div>
            <div class="text-xs text-gray-500">位置记录</div>
          </div>
          <div class="bg-red-50 rounded-lg p-3 text-center">
            <div class="text-lg font-bold text-red-600">{{ stats.alertToday }}</div>
            <div class="text-xs text-gray-500">今日告警</div>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🧹 存储清理</div>
        <p class="text-xs text-gray-400 mb-3">清理缓存和临时文件释放空间</p>
        <div class="grid grid-cols-2 gap-2">
          <button @click="cleanStorage('cache')" :disabled="cleaning"
            class="py-2 bg-gray-100 text-gray-700 rounded-lg text-sm font-medium active:bg-gray-200 disabled:opacity-50">清理缓存</button>
          <button @click="cleanStorage('screenshots')" :disabled="cleaning"
            class="py-2 bg-gray-100 text-gray-700 rounded-lg text-sm font-medium active:bg-gray-200 disabled:opacity-50">清理截屏</button>
        </div>
        <div v-if="cleanResult" class="mt-2 text-xs text-green-600">{{ cleanResult }}</div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🌐 网络速度</div>
        <button @click="speedTest" :disabled="testing"
          class="w-full py-3 bg-blue-500 text-white rounded-xl font-medium active:bg-blue-600 disabled:opacity-50">
          {{ testing ? '测试中...' : '开始测速' }}
        </button>
        <div v-if="speedResult" class="mt-3 space-y-2">
          <div class="flex justify-between text-sm">
            <span class="text-gray-500">下载速度</span>
            <span class="font-medium">{{ speedResult.download }}</span>
          </div>
          <div class="flex justify-between text-sm">
            <span class="text-gray-500">延迟</span>
            <span class="font-medium">{{ speedResult.latency }}</span>
          </div>
          <div class="text-xs text-gray-400 mt-2">测试时间: {{ speedResult.time }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const info = ref<any>({})
const serviceInfo = ref<any>(null)
const stats = ref({ callsToday: 0, smsToday: 0, locationCount: 0, alertToday: 0 })
const testing = ref(false)
const speedResult = ref<any>(null)
const batteryHistory = ref<{level: number; time: string; charging: boolean}[]>([])
const cleaning = ref(false)
const cleanResult = ref('')

async function cleanStorage(type: string) {
  cleaning.value = true
  cleanResult.value = ''
  try {
    const res = await api.post('/api/device/clean', { type })
    cleanResult.value = res.data.message
    ElMessage.success(res.data.message)
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '清理失败') }
  cleaning.value = false
}

function updateBatteryHistory() {
  if (info.value.batteryLevel !== undefined) {
    const now = new Date().toLocaleTimeString()
    batteryHistory.value.push({ level: info.value.batteryLevel, time: now, charging: info.value.isCharging })
    if (batteryHistory.value.length > 20) batteryHistory.value.shift()
    localStorage.setItem('xiangqin_battery_history', JSON.stringify(batteryHistory.value))
  }
}

async function speedTest() {
  testing.value = true
  speedResult.value = null
  try {
    const latencyStart = performance.now()
    await api.get('/api/health')
    const latency = Math.round(performance.now() - latencyStart)

    const sizes = [10240, 102400, 512000]
    const results: { size: number; speed: number }[] = []

    for (const size of sizes) {
      const start = performance.now()
      await api.get(`/api/speedtest?size=${size}`)
      const elapsed = (performance.now() - start) / 1000
      if (elapsed > 0) {
        results.push({ size, speed: size / elapsed })
      }
    }

    let bestSpeed = 0
    for (const r of results) {
      if (r.speed > bestSpeed) bestSpeed = r.speed
    }

    const fmtSpeed = (bps: number) => {
      if (bps < 1024) return bps.toFixed(0) + ' B/s'
      if (bps < 1048576) return (bps / 1024).toFixed(1) + ' KB/s'
      return (bps / 1048576).toFixed(2) + ' MB/s'
    }

    speedResult.value = {
      download: fmtSpeed(bestSpeed),
      latency: latency + 'ms',
      time: new Date().toLocaleTimeString()
    }
  } catch {
    speedResult.value = { download: '连接失败', latency: 'N/A', time: new Date().toLocaleTimeString() }
  }
  testing.value = false
}

async function refresh() {
  const [dRes, sRes, stRes] = await Promise.all([
    api.get('/api/device/info'),
    api.get('/api/settings').catch(() => ({ data: null })),
    api.get('/api/stats/summary').catch(() => ({ data: {} }))
  ])
  info.value = dRes.data
  serviceInfo.value = sRes.data
  stats.value = {
    callsToday: stRes.data.callCount || 0,
    smsToday: stRes.data.smsCount || 0,
    locationCount: stRes.data.locationCount || 0,
    alertToday: stRes.data.alertToday || 0
  }
}

onMounted(() => {
  try { batteryHistory.value = JSON.parse(localStorage.getItem('xiangqin_battery_history') || '[]') } catch {}
  refresh().then(() => updateBatteryHistory())
})

const batteryColor = computed(() => {
  const level = info.value.batteryLevel || 0
  if (level <= 10) return 'bg-red-500'
  if (level <= 20) return 'bg-orange-500'
  if (level <= 50) return 'bg-yellow-500'
  return 'bg-green-500'
})

const networkTypeLabel = computed(() => {
  const types: Record<number, string> = { 0: '未知', 1: 'GPRS', 2: 'EDGE', 3: 'UMTS', 4: 'CDMA', 5: 'EVDO_0', 6: 'EVDO_A', 7: '1xRTT', 8: 'HSDPA', 9: 'HSUPA', 10: 'HSPA', 13: 'LTE', 20: 'NR' }
  return types[info.value.networkType] || `类型${info.value.networkType}`
})

const dataStateLabel = computed(() => {
  const states: Record<number, string> = { 0: '断开', 1: '连接中', 2: '已连接', 3: '挂起中' }
  return states[info.value.dataState] || `状态${info.value.dataState}`
})

const formatUptime = computed(() => {
  if (!serviceInfo.value?.serviceRunning) return 'N/A'
  const ms = serviceInfo.value.serviceUptime || 0
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  return h > 0 ? `${h}小时${m}分钟` : `${m}分钟`
})

const networkClass = computed(() => {
  const type = info.value.networkType || 0
  if (type >= 13) return 'text-green-600 font-medium'
  if (type >= 8) return 'text-blue-600'
  return 'text-gray-500'
})

const dataStateClass = computed(() => {
  return info.value.dataState === 2 ? 'text-green-600' : 'text-red-500'
})

const signalLabel = computed(() => {
  const type = info.value.networkType || 0
  if (type >= 20) return '5G - 极佳'
  if (type >= 13) return '4G - 良好'
  if (type >= 8) return '3G - 一般'
  if (type >= 1) return '2G - 较差'
  return '未知'
})

const signalClass = computed(() => {
  const type = info.value.networkType || 0
  if (type >= 13) return 'text-green-600'
  if (type >= 8) return 'text-blue-600'
  if (type >= 1) return 'text-yellow-600'
  return 'text-gray-400'
})
</script>
