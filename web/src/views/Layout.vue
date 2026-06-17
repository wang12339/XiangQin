<template>
  <div class="min-h-screen bg-gray-50">
    <div v-if="isOffline" class="bg-yellow-500 text-white text-xs text-center py-1 fixed top-0 left-0 right-0 z-50">
      ⚠️ 网络连接已断开，部分功能可能不可用
    </div>

    <div v-if="globalLoading || routeLoading" class="fixed top-0 left-0 right-0 z-50">
      <div class="h-0.5 bg-gray-200">
        <div class="h-full bg-gradient-to-r from-blue-500 to-cyan-400 animate-loading-bar"></div>
      </div>
    </div>

    <!-- Desktop sidebar -->
    <aside class="hidden md:flex fixed left-0 top-0 bottom-0 w-56 bg-white border-r border-gray-200 z-40 flex-col">
      <div class="px-4 py-4 border-b border-gray-100">
        <div class="text-lg font-bold text-gray-800">乡亲</div>
        <div class="text-xs text-gray-400 mt-0.5">家庭安全监控</div>
      </div>
      <nav class="flex-1 overflow-y-auto py-2">
        <div v-for="group in sidebarGroups" :key="group.label" class="mb-1">
          <div class="px-4 py-1.5 text-[10px] font-semibold text-gray-400 uppercase tracking-wider">{{ group.label }}</div>
          <button v-for="item in group.items" :key="item.path" @click="$router.push(item.path)"
            class="w-full flex items-center gap-3 px-4 py-2 text-sm transition-colors"
            :class="$route.path === item.path ? 'bg-blue-50 text-blue-600 font-medium' : 'text-gray-600 hover:bg-gray-50'">
            <span class="text-base w-5 text-center">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
            <span v-if="item.badge" class="ml-auto w-5 h-5 bg-red-500 text-white text-[10px] rounded-full flex items-center justify-center">{{ item.badge > 99 ? '99+' : item.badge }}</span>
          </button>
        </div>
      </nav>
      <div class="px-4 py-3 border-t border-gray-100 text-xs text-gray-400">v0.1.0</div>
    </aside>

    <!-- Mobile bottom nav -->
    <nav class="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 z-50 md:hidden safe-bottom">
      <div class="flex">
        <button v-for="tab in tabs" :key="tab.path" @click="$router.push(tab.path)"
          class="flex-1 flex flex-col items-center py-2 text-xs transition-colors relative"
          :class="$route.path === tab.path ? 'text-blue-600' : 'text-gray-500'">
          <span class="text-xl mb-0.5 relative">
            {{ tab.icon }}
            <span v-if="tab.badge" class="absolute -top-1 -right-2 w-4 h-4 bg-red-500 text-white text-[10px] rounded-full flex items-center justify-center">{{ tab.badge > 99 ? '99+' : tab.badge }}</span>
          </span>
          <span>{{ tab.label }}</span>
        </button>
      </div>
    </nav>

    <!-- Main content area -->
    <div class="md:ml-56">
      <router-view v-slot="{ Component }">
        <keep-alive :max="5">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </keep-alive>
      </router-view>
    </div>

    <button v-show="showBackTop" @click="scrollToTop"
      class="fixed right-4 bottom-20 z-40 w-10 h-10 bg-white rounded-full shadow-lg flex items-center justify-center text-gray-500 active:bg-gray-100 transition-all md:bottom-4">
      ↑
    </button>

    <div class="h-14 md:hidden"></div>
    <div v-if="isOffline" class="h-5"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, isLoading } from '../api/client'

const route = useRoute()
const router = useRouter()
const showBackTop = ref(false)
const alertCount = ref(0)
const isOffline = ref(!navigator.onLine)
const globalLoading = isLoading
const routeLoading = ref(false)

watch(() => route.fullPath, () => {
  routeLoading.value = true
  setTimeout(() => { routeLoading.value = false }, 300)
})

const tabPaths = ['/', '/calls', '/location', '/remote', '/more']

const tabs = ref([
  { path: '/', icon: '📊', label: '总览', badge: 0 },
  { path: '/calls', icon: '📞', label: '通讯', badge: 0 },
  { path: '/location', icon: '📍', label: '位置', badge: 0 },
  { path: '/remote', icon: '📸', label: '远程', badge: 0 },
  { path: '/more', icon: '☰', label: '更多', badge: 0 },
])

const sidebarGroups = ref([
  {
    label: '监控',
    items: [
      { path: '/', icon: '📊', label: '总览', badge: 0 },
      { path: '/calls', icon: '📞', label: '通话记录' },
      { path: '/sms', icon: '💬', label: '短信' },
      { path: '/contacts', icon: '👥', label: '联系人' },
      { path: '/notifications', icon: '🔔', label: '通知' },
      { path: '/usage', icon: '📱', label: '应用使用' },
      { path: '/traffic', icon: '📡', label: '流量统计' },
    ]
  },
  {
    label: '位置',
    items: [
      { path: '/location', icon: '📍', label: '实时位置' },
      { path: '/bluetooth', icon: '📶', label: '蓝牙' },
      { path: '/wifi', icon: '🌐', label: 'WiFi' },
      { path: '/sensors', icon: '🌡️', label: '传感器' },
    ]
  },
  {
    label: '远程',
    items: [
      { path: '/remote', icon: '📸', label: '远程操作' },
      { path: '/remotecontrol', icon: '🎮', label: '远程控制' },
      { path: '/remotesms', icon: '📨', label: '远程短信' },
      { path: '/remotecall', icon: '📞', label: '远程拨号' },
      { path: '/media', icon: '🖼️', label: '媒体文件' },
      { path: '/files', icon: '📁', label: '文件管理' },
    ]
  },
  {
    label: '系统',
    items: [
      { path: '/alerts', icon: '⚠️', label: '告警', badge: 0 },
      { path: '/alertsettings', icon: '⚙️', label: '告警设置' },
      { path: '/apps', icon: '📦', label: '应用管理' },
      { path: '/calendar', icon: '📅', label: '日历' },
      { path: '/deviceinfo', icon: 'ℹ️', label: '设备信息' },
      { path: '/syslogs', icon: '📋', label: '系统日志' },
      { path: '/stats', icon: '📈', label: '数据统计' },
      { path: '/export', icon: '💾', label: '数据导出' },
      { path: '/settings', icon: '⚙️', label: '设置' },
    ]
  },
])

async function checkAlerts() {
  try {
    const res = await api.get('/api/alerts?days=1')
    const unacked = res.data.unacknowledged || 0
    alertCount.value = unacked
    tabs.value[0].badge = unacked
    sidebarGroups.value[3].items[0].badge = unacked
    isOffline.value = false
  } catch {
    isOffline.value = true
  }
}

function onScroll() {
  showBackTop.value = window.scrollY > 300
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onOnline() { isOffline.value = false }
function onOffline() { isOffline.value = true }

let touchStartX = 0
let touchStartY = 0

function onTouchStart(e: TouchEvent) {
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
}

function onTouchEnd(e: TouchEvent) {
  const dx = e.changedTouches[0].clientX - touchStartX
  const dy = e.changedTouches[0].clientY - touchStartY
  if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 80) {
    const idx = tabPaths.indexOf(route.path)
    if (idx === -1) return
    if (dx < 0 && idx < tabPaths.length - 1) router.push(tabPaths[idx + 1])
    else if (dx > 0 && idx > 0) router.push(tabPaths[idx - 1])
  }
}

let alertTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  window.addEventListener('scroll', onScroll)
  window.addEventListener('online', onOnline)
  window.addEventListener('offline', onOffline)
  document.addEventListener('touchstart', onTouchStart)
  document.addEventListener('touchend', onTouchEnd)
  checkAlerts()
  alertTimer = setInterval(checkAlerts, 60_000)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('online', onOnline)
  window.removeEventListener('offline', onOffline)
  document.removeEventListener('touchstart', onTouchStart)
  document.removeEventListener('touchend', onTouchEnd)
  if (alertTimer) clearInterval(alertTimer)
})
</script>

<style scoped>
.safe-bottom { padding-bottom: env(safe-area-inset-bottom); }
.fade-enter-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.fade-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.fade-enter-from { opacity: 0; transform: translateX(10px); }
.fade-leave-to { opacity: 0; transform: translateX(-10px); }
@keyframes loading-bar {
  0% { transform: translateX(-100%); }
  50% { transform: translateX(0%); }
  100% { transform: translateX(100%); }
}
.animate-loading-bar { animation: loading-bar 1.5s ease-in-out infinite; }
</style>
