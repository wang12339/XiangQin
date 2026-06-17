<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10"><h1 class="text-lg font-bold">设置</h1></div>
    <div class="p-4 space-y-3">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="text-sm text-gray-400 mb-2">系统信息</div>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span class="text-gray-500">设备名</span><span>{{ info.hostname }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">局域网 IP</span><span>{{ info.localIp }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">端口</span><span>{{ info.port }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">服务状态</span><span :class="info.serviceRunning?'text-green-600':'text-red-600'">{{ info.serviceRunning?'运行中':'已停止' }}</span></div>
        </div>
        <div class="mt-3 text-center text-xs text-gray-400">
          <router-link to="/legal?type=privacy" class="text-blue-400">隐私政策</router-link> · <router-link to="/legal?type=agreement" class="text-blue-400">用户协议</router-link>
        </div>
      </div>

      <div v-if="deviceInfo.modelName" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="text-sm font-medium mb-2">设备详情</div>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span class="text-gray-500">品牌/型号</span><span>{{ deviceInfo.brand }} {{ deviceInfo.modelName }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">Android</span><span>{{ deviceInfo.androidVersion }} (SDK {{ deviceInfo.sdkVersion }})</span></div>
          <div class="flex justify-between"><span class="text-gray-500">运营商</span><span>{{ deviceInfo.simOperator || 'N/A' }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">电池</span>
            <span :class="deviceInfo.batteryLevel <= 20 ? 'text-red-500' : ''">{{ deviceInfo.batteryLevel }}% {{ deviceInfo.isCharging ? '⚡充电中' : '' }}</span>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="text-sm font-medium mb-3">音量控制</div>
        <div class="space-y-3">
          <div>
            <div class="flex justify-between text-xs text-gray-500 mb-1"><span>媒体音量</span><span>{{ settings.musicVolume }}/{{ settings.musicMax }}</span></div>
            <input type="range" :min="0" :max="settings.musicMax" v-model.number="settings.musicVolume" @change="setVolume('musicVolume', settings.musicVolume)" class="w-full" />
          </div>
          <div>
            <div class="flex justify-between text-xs text-gray-500 mb-1"><span>铃声音量</span><span>{{ settings.ringVolume }}/{{ settings.ringMax }}</span></div>
            <input type="range" :min="0" :max="settings.ringMax" v-model.number="settings.ringVolume" @change="setVolume('ringVolume', settings.ringVolume)" class="w-full" />
          </div>
          <div>
            <div class="flex justify-between text-xs text-gray-500 mb-1"><span>闹钟音量</span><span>{{ settings.alarmVolume }}/{{ settings.alarmMax }}</span></div>
            <input type="range" :min="0" :max="settings.alarmMax" v-model.number="settings.alarmVolume" @change="setVolume('alarmVolume', settings.alarmVolume)" class="w-full" />
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="text-sm font-medium mb-2">设备账户</div>
        <div v-if="accounts.length" class="space-y-2">
          <div v-for="a in accounts" :key="a.name" class="flex items-center gap-2 text-sm">
            <span class="w-2 h-2 rounded-full bg-blue-400"></span>
            <span class="flex-1 truncate">{{ a.name }}</span>
            <span class="text-xs text-gray-400">{{ a.type }}</span>
          </div>
        </div>
        <div v-else class="text-sm text-gray-400">无账户信息</div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="text-sm font-medium mb-3">外观</div>
        <div class="flex items-center justify-between">
          <span class="text-sm text-gray-600">暗色模式</span>
          <label class="relative inline-flex items-center cursor-pointer">
            <input type="checkbox" v-model="darkMode" @change="toggleDark" class="sr-only peer" />
            <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-500"></div>
          </label>
        </div>
        <div class="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
          <span class="text-sm text-gray-600">语言</span>
          <select v-model="lang" @change="changeLang" class="border rounded px-2 py-1 text-sm">
            <option value="zh">中文</option>
            <option value="en">English</option>
          </select>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="text-sm font-medium mb-3">修改密码</div>
        <input v-model="pw.oldPassword" type="password" placeholder="旧密码" class="w-full border rounded-lg px-3 py-2 text-sm mb-2" />
        <input v-model="pw.newPassword" type="password" placeholder="新密码" class="w-full border rounded-lg px-3 py-2 text-sm mb-3" />
        <button @click="changePw" class="w-full py-2.5 bg-blue-500 text-white rounded-xl font-medium active:bg-blue-600">修改密码</button>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const info = ref<any>({})
const deviceInfo = ref<any>({})
const settings = ref<any>({ musicVolume: 0, musicMax: 15, ringVolume: 0, ringMax: 15, alarmVolume: 0, alarmMax: 7 })
const accounts = ref<any[]>([])
const pw = ref({ oldPassword: '', newPassword: '' })
const darkMode = ref(localStorage.getItem('theme') === 'dark')
const lang = ref(localStorage.getItem('lang') || 'zh')

function toggleDark() {
  document.documentElement.classList.toggle('dark', darkMode.value)
  localStorage.setItem('theme', darkMode.value ? 'dark' : 'light')
}

function changeLang() {
  localStorage.setItem('lang', lang.value)
  document.documentElement.lang = lang.value
  ElMessage.success(lang.value === 'zh' ? '已切换为中文' : 'Switched to English')
}

onMounted(async () => {
  if (darkMode.value) document.documentElement.classList.add('dark')
  const [i, s, a, d] = await Promise.all([
    api.get('/api/settings'),
    api.get('/api/system/settings'),
    api.get('/api/accounts'),
    api.get('/api/device/info').catch(() => ({ data: {} }))
  ])
  info.value = i.data; settings.value = s.data; accounts.value = a.data.accounts || []
  deviceInfo.value = d.data
})

async function setVolume(key: string, value: number) {
  try { await api.post('/api/system/settings', { [key]: value }); ElMessage.success('已更新') }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '失败') }
}

async function changePw() {
  try { await api.post('/api/settings/password', pw.value); ElMessage.success('密码已修改'); pw.value = { oldPassword: '', newPassword: '' } }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '失败') }
}
</script>
