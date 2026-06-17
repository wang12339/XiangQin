<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">告警设置</h1>
        <button @click="save" :disabled="saving" class="text-blue-500 text-sm disabled:opacity-50">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </div>
    <div class="p-4 space-y-4">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">告警开关</div>
        <div class="space-y-3">
          <div v-for="item in alertTypes" :key="item.key" class="flex items-center justify-between">
            <div>
              <div class="text-sm font-medium">{{ item.icon }} {{ item.label }}</div>
              <div class="text-xs text-gray-400">{{ item.desc }}</div>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" v-model="settings.enabled[item.key]" class="sr-only peer" />
              <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-500"></div>
            </label>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🏠 家的位置</div>
        <div class="text-sm text-gray-500 mb-2">设置家的位置和范围，用于检测深夜离家</div>
        <div v-if="settings.home" class="bg-gray-50 rounded-lg p-3 mb-3">
          <div class="font-mono text-sm">{{ settings.home.latitude.toFixed(5) }}, {{ settings.home.longitude.toFixed(5) }}</div>
          <div class="text-xs text-gray-400 mt-1">半径 {{ settings.home.radiusMeters }}m · {{ settings.home.address || '未设置地址' }}</div>
        </div>
        <div class="space-y-2">
          <input v-model.number="homeForm.latitude" type="number" step="0.00001" placeholder="纬度" class="w-full border rounded-lg px-3 py-2 text-sm" />
          <input v-model.number="homeForm.longitude" type="number" step="0.00001" placeholder="经度" class="w-full border rounded-lg px-3 py-2 text-sm" />
          <div class="flex gap-2">
            <input v-model.number="homeForm.radiusMeters" type="number" step="50" placeholder="半径(米)" class="flex-1 border rounded-lg px-3 py-2 text-sm" />
            <input v-model="homeForm.address" type="text" placeholder="地址(可选)" class="flex-1 border rounded-lg px-3 py-2 text-sm" />
          </div>
          <div class="flex gap-2">
            <button @click="useCurrentLocation" class="flex-1 py-2 bg-gray-100 text-gray-700 rounded-lg text-sm">📍 获取当前位置</button>
            <button @click="saveHome" class="flex-1 py-2 bg-blue-500 text-white rounded-lg text-sm">保存位置</button>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">飞书推送</div>
        <div class="text-sm text-gray-500 mb-2">配置飞书 Webhook URL，告警将推送到飞书</div>
        <input v-model="settings.feishuWebhook" type="url" placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/..." class="w-full border rounded-lg px-3 py-2 text-sm mb-2" />
        <button @click="testPush" :disabled="testing" class="w-full py-2 bg-green-500 text-white rounded-lg text-sm disabled:opacity-50">
          {{ testing ? '发送中...' : '发送测试消息' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const saving = ref(false)
const testing = ref(false)
const settings = reactive<{ enabled: Record<string, boolean>; home: any; feishuWebhook: string }>({
  enabled: {},
  home: null,
  feishuWebhook: ''
})
const homeForm = reactive({ latitude: 0, longitude: 0, radiusMeters: 200, address: '' })

const alertTypes = [
  { key: 'late_night_leave', icon: '🌙', label: '深夜离家', desc: '22:00-06:00 离开家范围' },
  { key: 'low_battery', icon: '🔋', label: '低电量', desc: '电量低于 20% 且未充电' },
  { key: 'no_heartbeat', icon: '💤', label: '心跳丢失', desc: '超过 6 小时无心跳' },
  { key: 'off_hour_call', icon: '📞', label: '凌晨通话', desc: '00:00-06:00 有通话' },
  { key: 'device_boot', icon: '🔄', label: '设备重启', desc: '手机重启完成' },
  { key: 'sim_change', icon: '📱', label: 'SIM卡变化', desc: 'SIM卡被拔出或更换' },
  { key: 'app_install', icon: '📦', label: '应用安装/卸载', desc: '检测到新应用安装或卸载' },
  { key: 'wifi_change', icon: '📶', label: 'WiFi切换', desc: '连接到新的WiFi网络' },
  { key: 'cell_change', icon: '📡', label: '基站变化', desc: '检测到基站切换' },
]

onMounted(async () => {
  try {
    const res = await api.get('/api/alerts/settings')
    const d = res.data
    settings.enabled = d.enabled || {}
    settings.home = d.home
    settings.feishuWebhook = d.feishuWebhook || ''
    if (d.home) {
      homeForm.latitude = d.home.latitude
      homeForm.longitude = d.home.longitude
      homeForm.radiusMeters = d.home.radiusMeters || 200
      homeForm.address = d.home.address || ''
    }
  } catch { ElMessage.error('加载设置失败') }
})

async function save() {
  saving.value = true
  try {
    await api.post('/api/alerts/settings', {
      enabled: settings.enabled,
      feishuWebhook: settings.feishuWebhook
    })
    ElMessage.success('设置已保存')
  } catch { ElMessage.error('保存失败') }
  saving.value = false
}

function saveHome() {
  settings.home = { ...homeForm }
  save()
}

function useCurrentLocation() {
  if (!navigator.geolocation) { ElMessage.error('浏览器不支持定位'); return }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      homeForm.latitude = pos.coords.latitude
      homeForm.longitude = pos.coords.longitude
      ElMessage.success('已获取当前位置')
    },
    () => { ElMessage.error('定位失败') }
  )
}

async function testPush() {
  testing.value = true
  try {
    await api.post('/api/alerts/test-push')
    ElMessage.success('测试消息已发送，请检查飞书')
  } catch { ElMessage.error('发送失败，请检查 Webhook URL') }
  testing.value = false
}
</script>
