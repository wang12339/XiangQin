<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">蓝牙设备</h1>
        <div class="flex gap-2">
          <button @click="toggleAutoScan" :class="autoScan ? 'bg-green-100 text-green-600' : 'text-gray-400'"
            class="text-sm px-2 py-1 rounded">{{ autoScan ? '自动扫描中' : '自动扫描' }}</button>
          <button @click="scan" :disabled="scanning" class="text-blue-500 text-sm">{{ scanning ? '扫描中...' : '扫描' }}</button>
        </div>
      </div>
    </div>
    <div class="p-4 space-y-4">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="grid grid-cols-3 gap-3 text-center">
          <div>
            <div class="text-lg font-bold text-blue-600">{{ devices.length }}</div>
            <div class="text-xs text-gray-400">总设备</div>
          </div>
          <div>
            <div class="text-lg font-bold text-green-600">{{ pairedCount }}</div>
            <div class="text-xs text-gray-400">已配对</div>
          </div>
          <div>
            <div class="text-lg font-bold text-gray-500">{{ devices.length - pairedCount }}</div>
            <div class="text-xs text-gray-400">未配对</div>
          </div>
        </div>
      </div>

      <div v-if="!devices.length && !loading" class="text-center text-gray-400 py-8">暂无蓝牙设备，点击扫描</div>

      <div v-for="d in devices" :key="d.deviceAddress" class="bg-white rounded-xl p-3 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center text-lg"
            :class="d.bondState === 12 ? 'bg-green-100' : 'bg-gray-100'">
            {{ deviceIcon(d) }}
          </div>
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm truncate">{{ d.deviceName || '未知设备' }}</div>
            <div class="text-xs text-gray-400 font-mono">{{ d.deviceAddress }}</div>
          </div>
          <div class="text-right">
            <span v-if="d.rssi" class="text-xs px-1.5 py-0.5 rounded" :class="signalClass(d.rssi)">{{ d.rssi }}dBm</span>
            <div class="text-xs text-gray-400 mt-1">{{ bondLabel(d.bondState) }}</div>
          </div>
        </div>
        <div v-if="d.rssi" class="mt-2">
          <div class="flex items-center gap-1">
            <div v-for="i in 5" :key="i" class="w-2 h-2 rounded-full"
              :class="i <= signalBars(d.rssi) ? 'bg-blue-500' : 'bg-gray-200'"></div>
            <span class="text-[10px] text-gray-400 ml-1">{{ signalText(d.rssi) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const devices = ref<any[]>([])
const loading = ref(true)
const scanning = ref(false)
const autoScan = ref(false)
let autoScanTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  try {
    const res = await api.get('/api/bluetooth/devices')
    devices.value = res.data.devices || []
  } catch {}
  loading.value = false
})

const pairedCount = computed(() => devices.value.filter(d => d.bondState === 12).length)

function toggleAutoScan() {
  autoScan.value = !autoScan.value
  if (autoScan.value) {
    scan()
    autoScanTimer = setInterval(scan, 30000)
  } else if (autoScanTimer) {
    clearInterval(autoScanTimer)
    autoScanTimer = null
  }
}

onUnmounted(() => {
  if (autoScanTimer) clearInterval(autoScanTimer)
})

async function scan() {
  scanning.value = true
  try {
    await api.post('/api/bluetooth/scan')
    const res = await api.get('/api/bluetooth/devices')
    devices.value = res.data.devices || []
    if (!autoScan.value) ElMessage.success(`发现 ${devices.value.length} 个设备`)
  } catch { if (!autoScan.value) ElMessage.error('扫描失败') }
  scanning.value = false
}

function deviceIcon(d: any) {
  const name = (d.deviceName || '').toLowerCase()
  if (name.includes('airpod') || name.includes('buds') || name.includes('headphone') || name.includes('headset')) return '🎧'
  if (name.includes('speaker') || name.includes('sound')) return '🔊'
  if (name.includes('watch')) return '⌚'
  if (name.includes('keyboard')) return '⌨️'
  if (name.includes('mouse')) return '🖱️'
  if (name.includes('tv') || name.includes('display')) return '📺'
  return '📡'
}

function signalClass(rssi: number) {
  if (rssi >= -50) return 'bg-green-100 text-green-700'
  if (rssi >= -70) return 'bg-yellow-100 text-yellow-700'
  return 'bg-red-100 text-red-700'
}

function signalBars(rssi: number) {
  if (rssi >= -50) return 5
  if (rssi >= -60) return 4
  if (rssi >= -70) return 3
  if (rssi >= -80) return 2
  return 1
}

function signalText(rssi: number) {
  if (rssi >= -50) return '极强'
  if (rssi >= -60) return '强'
  if (rssi >= -70) return '中等'
  if (rssi >= -80) return '弱'
  return '极弱'
}

function bondLabel(state: number) {
  if (state === 12) return '已配对'
  if (state === 11) return '配对中'
  return '未配对'
}
</script>
