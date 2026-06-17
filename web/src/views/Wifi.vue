<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">WiFi</h1>
        <button @click="scan" :disabled="scanning" class="text-blue-500 text-sm">{{ scanning ? '扫描中...' : '扫描' }}</button>
      </div>
    </div>
    <div class="p-4 space-y-4">
      <div v-if="security" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="flex items-center justify-between mb-3">
          <span class="font-medium">🔒 安全概览</span>
          <button @click="refreshSecurity" class="text-xs text-gray-400">刷新分析</button>
        </div>
        <div class="grid grid-cols-3 gap-3 text-center">
          <div class="bg-green-50 rounded-lg p-2">
            <div class="text-lg font-bold text-green-600">{{ security.safeCount }}</div>
            <div class="text-xs text-gray-500">安全</div>
          </div>
          <div class="bg-yellow-50 rounded-lg p-2">
            <div class="text-lg font-bold text-yellow-600">{{ security.mediumRiskCount }}</div>
            <div class="text-xs text-gray-500">中风险</div>
          </div>
          <div class="bg-red-50 rounded-lg p-2">
            <div class="text-lg font-bold text-red-600">{{ security.highRiskCount + security.criticalCount }}</div>
            <div class="text-xs text-gray-500">高风险</div>
          </div>
        </div>
        <div v-if="security.connectedInfo" class="mt-3 p-2 bg-blue-50 rounded-lg text-xs">
          <span class="font-medium">当前连接:</span> {{ security.connectedInfo.ssid }}
          <span class="text-gray-500 ml-1">({{ security.connectedInfo.securityType }})</span>
        </div>
        <div v-if="security.highRiskNetworks && security.highRiskNetworks.length" class="mt-3">
          <div class="text-xs text-red-500 font-medium mb-2">⚠️ 高风险网络</div>
          <div v-for="n in security.highRiskNetworks" :key="n.id" class="text-xs text-red-600 py-1">
            {{ n.ssid || '(隐藏)' }} - {{ n.securityType }}
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="flex items-center justify-between mb-3">
          <span class="font-medium">附近网络 ({{ networks.length }})</span>
        </div>
        <div v-if="!networks.length && !loading" class="text-center text-gray-400 py-4">暂无数据，点击扫描</div>

        <div v-if="loading" class="space-y-2">
          <div v-for="i in 5" :key="i" class="animate-pulse">
            <div class="flex justify-between items-center">
              <div class="h-4 bg-gray-200 rounded w-32"></div>
              <div class="h-3 bg-gray-100 rounded w-16"></div>
            </div>
            <div class="h-3 bg-gray-100 rounded w-48 mt-2"></div>
          </div>
        </div>
        <div v-for="w in networks" :key="w.id" class="py-2 border-b border-gray-50 last:border-0">
          <div class="flex justify-between items-center">
            <span class="font-medium text-sm">{{ w.ssid || '(隐藏)' }}</span>
            <div class="flex items-center gap-2">
              <span class="text-xs px-1.5 py-0.5 rounded" :class="signalClass(w.rssi)">{{ w.rssi }}dBm</span>
            </div>
          </div>
          <div class="text-xs text-gray-400 mt-1">
            {{ w.securityType }} · {{ w.frequency }}MHz
            <span v-if="isRisk(w)" class="text-red-500 ml-1">⚠️ {{ riskLabel(w) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const networks = ref<any[]>([])
const security = ref<any>(null)
const loading = ref(true)
const scanning = ref(false)

onMounted(async () => {
  const [nwRes, secRes] = await Promise.all([
    api.get('/api/wifi/networks'),
    api.get('/api/wifi/security').catch(() => ({ data: null }))
  ])
  networks.value = nwRes.data.networks || []
  security.value = secRes.data
  loading.value = false
})

async function refreshSecurity() {
  try {
    const res = await api.get('/api/wifi/security')
    security.value = res.data
  } catch {}
}

async function scan() {
  scanning.value = true
  try {
    await api.post('/api/wifi/scan')
    const [nwRes, secRes] = await Promise.all([
      api.get('/api/wifi/networks'),
      api.get('/api/wifi/security').catch(() => ({ data: null }))
    ])
    networks.value = nwRes.data.networks || []
    security.value = secRes.data
  } catch {}
  scanning.value = false
}

function signalClass(rssi: number) {
  if (rssi >= -50) return 'bg-green-100 text-green-700'
  if (rssi >= -70) return 'bg-yellow-100 text-yellow-700'
  return 'bg-red-100 text-red-700'
}

function isRisk(w: any) {
  const sec = (w.securityType || '').toLowerCase()
  return sec === 'open' || sec === 'wep'
}

function riskLabel(w: any) {
  const sec = (w.securityType || '').toLowerCase()
  if (sec === 'open') return '开放网络'
  if (sec === 'wep') return 'WEP不安全'
  return ''
}
</script>
