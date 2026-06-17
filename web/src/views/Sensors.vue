<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">传感器</h1>
        <button @click="refresh" class="text-gray-400 text-sm px-3 py-1 bg-gray-50 rounded-lg">{{ loading ? '刷新中...' : '刷新' }}</button>
      </div>
    </div>

    <div class="p-4 space-y-4">
      <!-- 步数大卡片 -->
      <div v-if="getVal('step_counter') !== null" class="bg-gradient-to-br from-blue-500 to-blue-600 rounded-2xl p-5 text-white shadow-lg">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-sm opacity-80 mb-1">今日步数</div>
            <div class="text-4xl font-bold">{{ formatStep(getVal('step_counter') || 0) }}</div>
          </div>
          <div class="text-5xl opacity-80">🚶</div>
        </div>
        <div class="mt-3 h-2 bg-white/20 rounded-full overflow-hidden">
          <div class="h-full bg-white rounded-full transition-all" :style="{ width: Math.min((getVal('step_counter') || 0) / 10000 * 100, 100) + '%' }"></div>
        </div>
        <div class="text-xs opacity-60 mt-1">目标 10,000 步</div>
      </div>

      <!-- 环境信息网格 -->
      <div v-if="envCards.length" class="grid grid-cols-2 gap-3">
        <div v-for="card in envCards" :key="card.key"
          class="bg-white rounded-xl p-4 shadow-sm relative overflow-hidden">
          <div class="text-2xl mb-2">{{ card.icon }}</div>
          <div class="text-xl font-bold" :class="card.color">{{ card.display }}</div>
          <div class="text-xs text-gray-400 mt-0.5">{{ card.label }}</div>
          <div class="absolute top-0 right-0 w-16 h-16 rounded-bl-full" :class="card.bg"></div>
        </div>
      </div>

      <!-- 运动状态 -->
      <div v-if="motionSensors.length" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium text-sm mb-3">🏃 运动状态</div>
        <div class="grid grid-cols-3 gap-3">
          <div v-for="s in motionSensors" :key="s.sensorType" class="text-center">
            <div class="relative w-16 h-16 mx-auto mb-2">
              <svg class="w-16 h-16 -rotate-90" viewBox="0 0 36 36">
                <circle cx="18" cy="18" r="15" fill="none" stroke="#f3f4f6" stroke-width="3" />
                <circle cx="18" cy="18" r="15" fill="none" :stroke="s.color" stroke-width="3"
                  :stroke-dasharray="`${s.pct} 100`" stroke-linecap="round" class="transition-all" />
              </svg>
              <div class="absolute inset-0 flex items-center justify-center text-xs font-mono font-bold" :class="s.color">
                {{ s.displayVal }}
              </div>
            </div>
            <div class="text-xs text-gray-500">{{ s.label }}</div>
          </div>
        </div>
      </div>

      <!-- 设备朝向 -->
      <div v-if="orientation" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium text-sm mb-3">🧭 设备朝向</div>
        <div class="flex items-center justify-center">
          <div class="relative w-32 h-32">
            <svg viewBox="0 0 100 100" class="w-full h-full">
              <circle cx="50" cy="50" r="45" fill="none" stroke="#e5e7eb" stroke-width="2" />
              <text x="50" y="12" text-anchor="middle" class="text-[8px] fill-gray-400">N</text>
              <text x="90" y="53" text-anchor="middle" class="text-[8px] fill-gray-400">E</text>
              <text x="50" y="95" text-anchor="middle" class="text-[8px] fill-gray-400">S</text>
              <text x="10" y="53" text-anchor="middle" class="text-[8px] fill-gray-400">W</text>
              <line x1="50" y1="50" :x2="50 + 30 * Math.sin(orientation.azimuth * Math.PI / 180)" :y2="50 - 30 * Math.cos(orientation.azimuth * Math.PI / 180)" stroke="#3b82f6" stroke-width="2.5" stroke-linecap="round" />
              <circle cx="50" cy="50" r="3" fill="#3b82f6" />
            </svg>
          </div>
        </div>
        <div class="text-center mt-2">
          <span class="text-2xl font-bold text-blue-600">{{ orientation.direction }}</span>
          <span class="text-sm text-gray-400 ml-2">{{ orientation.azimuth.toFixed(0) }}°</span>
        </div>
      </div>

      <!-- 磁力计 -->
      <div v-if="getVal('magnetic_field') !== null" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium text-sm mb-2">🧲 磁场强度</div>
        <div class="flex items-center gap-3">
          <div class="flex-1 h-3 bg-gray-100 rounded-full overflow-hidden">
            <div class="h-full rounded-full bg-purple-500 transition-all" :style="{ width: Math.min((getVal('magnetic_field') || 0) / 100 * 100, 100) + '%' }"></div>
          </div>
          <span class="font-mono text-sm font-bold text-purple-600 whitespace-nowrap">{{ (getVal('magnetic_field') || 0).toFixed(1) }} μT</span>
        </div>
        <div class="text-xs text-gray-400 mt-1">{{ magneticDesc }}</div>
      </div>

      <!-- 光线 -->
      <div v-if="getVal('light') !== null" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium text-sm mb-2">💡 环境光线</div>
        <div class="flex items-center gap-3">
          <span class="text-3xl">{{ lightIcon }}</span>
          <div class="flex-1">
            <div class="text-xl font-bold text-yellow-500">{{ (getVal('light') || 0).toFixed(0) }} lux</div>
            <div class="text-xs text-gray-400">{{ lightDesc }}</div>
          </div>
        </div>
      </div>

      <!-- 其他原始数据 -->
      <div v-if="otherSensors.length" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium text-sm mb-3">📊 其他传感器</div>
        <div class="space-y-2">
          <div v-for="s in otherSensors" :key="s.sensorType" class="flex justify-between items-center py-1.5 border-b border-gray-50 last:border-0">
            <span class="text-sm text-gray-600">{{ sensorNameMap[s.sensorType] || s.sensorType }}</span>
            <span class="font-mono text-sm font-medium text-gray-800">{{ formatRaw(s.value) }}</span>
          </div>
        </div>
      </div>

      <div v-if="!list.length && !loading" class="text-center text-gray-400 py-8">暂无传感器数据</div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'

const list = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  await refresh()
})

async function refresh() {
  loading.value = true
  list.value = (await api.get('/api/sensors?limit=200')).data.sensors || []
  loading.value = false
}

function getVal(type: string): number | null {
  const s = list.value.find(s => s.sensorType === type)
  return s ? s.value : null
}

const envCards = computed(() => {
  const cards: any[] = []
  const temp = getVal('ambient_temperature')
  if (temp !== null) cards.push({ key: 'temp', icon: '🌡️', label: '温度', display: temp.toFixed(1) + '°C', color: 'text-red-500', bg: 'bg-red-50' })
  const humidity = getVal('humidity')
  if (humidity !== null) cards.push({ key: 'hum', icon: '💧', label: '湿度', display: humidity.toFixed(0) + '%', color: 'text-blue-500', bg: 'bg-blue-50' })
  const pressure = getVal('pressure')
  if (pressure !== null) cards.push({ key: 'press', icon: '🔽', label: '气压', display: (pressure / 100).toFixed(1) + ' hPa', color: 'text-green-500', bg: 'bg-green-50' })
  const proximity = getVal('proximity')
  if (proximity !== null) cards.push({ key: 'prox', icon: '📏', label: '距离', display: proximity.toFixed(0) + ' cm', color: 'text-indigo-500', bg: 'bg-indigo-50' })
  return cards
})

const motionSensors = computed(() => {
  const items: any[] = []
  const acc = getVal('acceleration') || getVal('accelerometer')
  if (acc !== null) {
    const pct = Math.min(acc / 20 * 100, 100)
    items.push({ sensorType: 'acceleration', label: '加速度', displayVal: acc.toFixed(1), pct, color: '#3b82f6' })
  }
  const gyro = getVal('gyroscope')
  if (gyro !== null) {
    const pct = Math.min(gyro / 10 * 100, 100)
    items.push({ sensorType: 'gyroscope', label: '陀螺仪', displayVal: gyro.toFixed(1), pct, color: '#8b5cf6' })
  }
  const linAcc = getVal('linear_acceleration')
  if (linAcc !== null) {
    const pct = Math.min(linAcc / 20 * 100, 100)
    items.push({ sensorType: 'linear_acceleration', label: '线性加速度', displayVal: linAcc.toFixed(1), pct, color: '#06b6d4' })
  }
  return items
})

const orientation = computed(() => {
  const val = getVal('orientation') || getVal('rotation_vector')
  if (val === null) return null
  const azimuth = getVal('orientation') !== null ? val : ((val * 180) / Math.PI)
  const normalizedAz = ((azimuth % 360) + 360) % 360
  let direction = '北'
  if (normalizedAz >= 22.5 && normalizedAz < 67.5) direction = '东北'
  else if (normalizedAz >= 67.5 && normalizedAz < 112.5) direction = '东'
  else if (normalizedAz >= 112.5 && normalizedAz < 157.5) direction = '东南'
  else if (normalizedAz >= 157.5 && normalizedAz < 202.5) direction = '南'
  else if (normalizedAz >= 202.5 && normalizedAz < 247.5) direction = '西南'
  else if (normalizedAz >= 247.5 && normalizedAz < 292.5) direction = '西'
  else if (normalizedAz >= 292.5 && normalizedAz < 337.5) direction = '西北'
  return { azimuth: normalizedAz, direction }
})

const magneticDesc = computed(() => {
  const val = getVal('magnetic_field') || 0
  if (val < 25) return '弱磁场 - 正常环境'
  if (val < 65) return '中等磁场'
  return '强磁场 - 可能靠近电子设备'
})

const lightDesc = computed(() => {
  const val = getVal('light') || 0
  if (val < 10) return '非常暗'
  if (val < 50) return '室内暗光'
  if (val < 300) return '室内正常光线'
  if (val < 1000) return '多云户外'
  return '阳光直射'
})

const lightIcon = computed(() => {
  const val = getVal('light') || 0
  if (val < 10) return '🌑'
  if (val < 50) return '🌘'
  if (val < 300) return '🌗'
  if (val < 1000) return '🌖'
  return '🌕'
})

const otherSensors = computed(() => {
  const known = new Set(['step_counter', 'ambient_temperature', 'humidity', 'pressure', 'light', 'proximity', 'magnetic_field', 'acceleration', 'accelerometer', 'gyroscope', 'linear_acceleration', 'orientation', 'rotation_vector', 'step_detector'])
  return list.value.filter(s => !known.has(s.sensorType))
})

const sensorNameMap: Record<string, string> = {
  'game_rotation_vector': '游戏旋转向量',
  'significant_motion': '大幅运动',
  'heartrate': '心率',
  'pose_6dof': '6DoF姿态',
  'geomagnetic_rotation_vector': '地磁旋转向量',
  'absolute_humidity': '绝对湿度',
  'ambient_temperature': '环境温度',
  'relative_humidity': '相对湿度',
}

function formatStep(val: number) {
  return Math.round(val).toLocaleString()
}

function formatRaw(val: number) {
  return val.toFixed(3)
}
</script>
