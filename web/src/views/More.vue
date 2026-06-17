<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <h1 class="text-lg font-bold">更多</h1>
    </div>
    <div class="p-4 space-y-3">
      <div v-if="favorites.length" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="text-xs text-gray-400 font-medium mb-2">⭐ 常用</div>
        <div class="grid grid-cols-4 gap-2">
          <button v-for="fav in favorites" :key="fav.path" @click="$router.push(fav.path)"
            class="flex flex-col items-center py-2 rounded-lg active:bg-gray-100 transition">
            <span class="text-xl">{{ fav.icon }}</span>
            <span class="text-[10px] text-gray-500 mt-1 truncate w-full text-center">{{ fav.label }}</span>
          </button>
        </div>
      </div>

      <div class="text-xs text-gray-400 font-medium mb-1">监控功能</div>
      <div v-for="item in monitorItems" :key="item.path" @click="$router.push(item.path)"
        class="bg-white rounded-xl p-4 flex items-center gap-3 active:bg-gray-100 transition cursor-pointer shadow-sm">
        <span class="text-2xl">{{ item.icon }}</span>
        <div class="flex-1">
          <div class="font-medium">{{ item.label }}</div>
          <div class="text-xs text-gray-400">{{ item.desc }}</div>
        </div>
        <button @click.stop="toggleFavorite(item)" class="text-xs px-2 py-1 rounded"
          :class="isFavorite(item.path) ? 'text-yellow-500 bg-yellow-50' : 'text-gray-300'">
          {{ isFavorite(item.path) ? '★' : '☆' }}
        </button>
      </div>

      <div class="text-xs text-gray-400 font-medium mt-4 mb-1">远程操作</div>
      <div v-for="item in remoteItems" :key="item.path" @click="$router.push(item.path)"
        class="bg-white rounded-xl p-4 flex items-center gap-3 active:bg-gray-100 transition cursor-pointer shadow-sm">
        <span class="text-2xl">{{ item.icon }}</span>
        <div class="flex-1">
          <div class="font-medium">{{ item.label }}</div>
          <div class="text-xs text-gray-400">{{ item.desc }}</div>
        </div>
        <button @click.stop="toggleFavorite(item)" class="text-xs px-2 py-1 rounded"
          :class="isFavorite(item.path) ? 'text-yellow-500 bg-yellow-50' : 'text-gray-300'">
          {{ isFavorite(item.path) ? '★' : '☆' }}
        </button>
      </div>

      <div class="text-xs text-gray-400 font-medium mt-4 mb-1">系统</div>
      <div v-for="item in systemItems" :key="item.path" @click="$router.push(item.path)"
        class="bg-white rounded-xl p-4 flex items-center gap-3 active:bg-gray-100 transition cursor-pointer shadow-sm">
        <span class="text-2xl">{{ item.icon }}</span>
        <div class="flex-1">
          <div class="font-medium">{{ item.label }}</div>
          <div class="text-xs text-gray-400">{{ item.desc }}</div>
        </div>
        <button @click.stop="toggleFavorite(item)" class="text-xs px-2 py-1 rounded"
          :class="isFavorite(item.path) ? 'text-yellow-500 bg-yellow-50' : 'text-gray-300'">
          {{ isFavorite(item.path) ? '★' : '☆' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const favorites = ref<any[]>([])

const allItems = [
  { path: '/sms', icon: '💬', label: '短信记录', desc: '查看收发短信' },
  { path: '/contacts', icon: '👥', label: '联系人', desc: '设备联系人列表' },
  { path: '/usage', icon: '📱', label: '应用使用', desc: '应用使用时长统计' },
  { path: '/traffic', icon: '🌐', label: '流量统计', desc: '网络流量使用情况' },
  { path: '/wifi', icon: '📶', label: 'WiFi 扫描', desc: '附近 WiFi 热点' },
  { path: '/sensors', icon: '💪', label: '传感器', desc: '计步器、心率等' },
  { path: '/calendar', icon: '📅', label: '日历事件', desc: '同步日历' },
  { path: '/media', icon: '🖼️', label: '媒体文件', desc: '图片视频音频' },
  { path: '/remote', icon: '📸', label: '拍照/截屏/录音', desc: '远程拍照、截屏、录音' },
  { path: '/remotecontrol', icon: '🔧', label: '震动/手电筒/闹钟', desc: '远程设备控制' },
  { path: '/remotesms', icon: '📤', label: '远程发短信', desc: '远程发送短信' },
  { path: '/remotecall', icon: '📞', label: '远程拨号', desc: '远程拨打电话' },
  { path: '/alerts', icon: '🚨', label: '告警记录', desc: '查看和确认告警' },
  { path: '/alertsettings', icon: '⚙️', label: '告警设置', desc: '配置告警开关和推送' },
  { path: '/deviceinfo', icon: '📱', label: '设备信息', desc: 'SIM卡、运营商、电池' },
  { path: '/stats', icon: '📊', label: '数据统计', desc: '综合数据分析' },
  { path: '/apps', icon: '📦', label: '应用管理', desc: '查看/卸载应用' },
  { path: '/files', icon: '📂', label: '文件管理', desc: '浏览和下载设备文件' },
  { path: '/notifications', icon: '🔔', label: '通知记录', desc: '捕获的通知内容' },
  { path: '/syslogs', icon: '📋', label: '系统日志', desc: '心跳、错误等日志' },
  { path: '/export', icon: '📤', label: '数据导出', desc: '导出通话/短信/使用记录' },
  { path: '/feedback', icon: '💬', label: '反馈与支持', desc: '报告问题或提出建议' },
  { path: '/settings', icon: '⚙️', label: '设置', desc: '密码、法律文件' },
]

const monitorItems = allItems.filter(i => ['/sms', '/contacts', '/usage', '/traffic', '/wifi', '/sensors', '/calendar', '/media'].includes(i.path))
const remoteItems = allItems.filter(i => ['/remote', '/remotecontrol', '/remotesms', '/remotecall', '/alerts', '/alertsettings'].includes(i.path))
const systemItems = allItems.filter(i => ['/deviceinfo', '/stats', '/apps', '/files', '/notifications', '/syslogs', '/export', '/feedback', '/settings'].includes(i.path))

onMounted(() => {
  try { favorites.value = JSON.parse(localStorage.getItem('xiangqin_favorites') || '[]') } catch {}
})

function isFavorite(path: string) {
  return favorites.value.some(f => f.path === path)
}

function toggleFavorite(item: any) {
  if (isFavorite(item.path)) {
    favorites.value = favorites.value.filter(f => f.path !== item.path)
  } else {
    favorites.value.push(item)
  }
  localStorage.setItem('xiangqin_favorites', JSON.stringify(favorites.value))
}
</script>
