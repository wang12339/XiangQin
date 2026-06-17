<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center gap-2">
        <button @click="$router.back()" class="text-blue-500 text-sm">← 返回</button>
        <h1 class="text-lg font-bold">关于</h1>
      </div>
    </div>
    <div class="p-4 space-y-4">
      <div class="bg-white rounded-xl p-6 shadow-sm text-center">
        <div class="text-5xl mb-3">🏠</div>
        <h2 class="text-xl font-bold">乡亲</h2>
        <p class="text-sm text-gray-500 mt-1">家庭守护 · 安心相伴</p>
        <p class="text-xs text-gray-400 mt-2">版本 {{ version }}</p>
        <div v-if="buildInfo" class="text-xs text-gray-300 mt-1">{{ buildInfo }}</div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">功能特性</div>
        <div class="space-y-2 text-sm text-gray-600">
          <div class="flex items-start gap-2"><span>📞</span><span>通话和短信监控</span></div>
          <div class="flex items-start gap-2"><span>📍</span><span>实时位置追踪 + 地图可视化</span></div>
          <div class="flex items-start gap-2"><span>🚨</span><span>9种智能告警（深夜离家、低电量等）</span></div>
          <div class="flex items-start gap-2"><span>📸</span><span>远程拍照/截屏/录音</span></div>
          <div class="flex items-start gap-2"><span>🔧</span><span>远程控制（震动/手电筒/闹钟/查找手机）</span></div>
          <div class="flex items-start gap-2"><span>🔔</span><span>通知捕获和记录</span></div>
          <div class="flex items-start gap-2"><span>📊</span><span>应用使用和流量统计 + 图表</span></div>
          <div class="flex items-start gap-2"><span>📤</span><span>飞书 Webhook 告警推送</span></div>
          <div class="flex items-start gap-2"><span>🌙</span><span>暗色模式支持</span></div>
          <div class="flex items-start gap-2"><span>📤</span><span>数据导出（CSV/JSON）</span></div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">技术栈</div>
        <div class="space-y-1 text-sm text-gray-600">
          <div>Kotlin + Jetpack Compose + SplashScreen</div>
          <div>Room + SQLCipher 加密数据库</div>
          <div>Ktor 服务端（局域网 Web 管理面板）</div>
          <div>Vue 3 + Element Plus + Tailwind CSS v4</div>
          <div>WebSocket 实时推送 + Leaflet 地图</div>
        </div>
      </div>

      <div class="text-center text-xs text-gray-400 py-4">
          <router-link to="/legal?type=privacy" class="text-blue-400">隐私政策</router-link>
        <span class="mx-2">·</span>
          <router-link to="/legal?type=agreement" class="text-blue-400">用户协议</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const version = ref('0.1.0')
const buildInfo = ref('')

onMounted(async () => {
  try {
    const res = await api.get('/api/device/info')
    if (res.data.androidVersion) {
      buildInfo.value = `Android ${res.data.androidVersion} · ${res.data.brand} ${res.data.modelName}`
    }
  } catch {}
})
</script>
