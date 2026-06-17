<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10"><h1 class="text-lg font-bold">远程控制</h1></div>
    <div class="p-4 space-y-3">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🔔 查找手机</div>
        <p class="text-sm text-gray-500 mb-3">让手机持续震动+闪灯，帮助找到设备</p>
        <button @click="findPhone"
          :class="finding ? 'w-full py-3 bg-gray-500 text-white rounded-xl font-medium active:bg-gray-600' : 'w-full py-3 bg-red-500 text-white rounded-xl font-medium active:bg-red-600'">
          {{ finding ? '⏹ 停止查找' : '🔔 查找手机' }}
        </button>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📳 远程震动</div>
        <p class="text-sm text-gray-500 mb-3">让手机震动指定时长</p>
        <div class="flex gap-2">
          <button @click="vibrate(500)" class="flex-1 py-3 bg-blue-500 text-white rounded-xl text-sm active:bg-blue-600">500ms</button>
          <button @click="vibrate(1000)" class="flex-1 py-3 bg-blue-500 text-white rounded-xl text-sm active:bg-blue-600">1秒</button>
          <button @click="vibrate(3000)" class="flex-1 py-3 bg-blue-500 text-white rounded-xl text-sm active:bg-blue-600">3秒</button>
          <button @click="vibrate(5000)" class="flex-1 py-3 bg-blue-500 text-white rounded-xl text-sm active:bg-blue-600">5秒</button>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🔦 手电筒</div>
        <p class="text-sm text-gray-500 mb-3">开关设备手电筒</p>
        <div class="flex gap-2">
          <button @click="flashlight(true)" class="flex-1 py-3 bg-yellow-500 text-white rounded-xl text-sm active:bg-yellow-600">开启</button>
          <button @click="flashlight(false)" class="flex-1 py-3 bg-gray-500 text-white rounded-xl text-sm active:bg-gray-600">关闭</button>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">⏰ 设置闹钟</div>
        <div class="flex gap-2 mb-3">
          <input v-model="alarmHour" type="number" min="0" max="23" placeholder="时" class="w-20 border rounded-xl px-3 py-3 text-sm text-center" />
          <span class="text-xl self-center">:</span>
          <input v-model="alarmMinute" type="number" min="0" max="59" placeholder="分" class="w-20 border rounded-xl px-3 py-3 text-sm text-center" />
        </div>
        <button @click="setAlarm" class="w-full py-3 bg-indigo-500 text-white rounded-xl text-sm active:bg-indigo-600">设置闹钟</button>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">⚡ 快捷操作</div>
        <div class="grid grid-cols-2 gap-2">
          <button @click="vibrate(100); flashOn()" class="py-3 bg-purple-500 text-white rounded-xl text-sm active:bg-purple-600">闪烁提醒</button>
          <button @click="vibrate(200); vibrate(400); vibrate(600)" class="py-3 bg-orange-500 text-white rounded-xl text-sm active:bg-orange-600">节奏震动</button>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🔊 音量控制</div>
        <div class="space-y-3">
          <div>
            <div class="flex justify-between text-xs text-gray-500 mb-1"><span>媒体音量</span><span>{{ volumes.music }}/{{ volumes.musicMax }}</span></div>
            <input type="range" :min="0" :max="volumes.musicMax" v-model.number="volumes.music" @change="setVolume('music', volumes.music)" class="w-full" />
          </div>
          <div>
            <div class="flex justify-between text-xs text-gray-500 mb-1"><span>铃声音量</span><span>{{ volumes.ring }}/{{ volumes.ringMax }}</span></div>
            <input type="range" :min="0" :max="volumes.ringMax" v-model.number="volumes.ring" @change="setVolume('ring', volumes.ring)" class="w-full" />
          </div>
          <div>
            <div class="flex justify-between text-xs text-gray-500 mb-1"><span>闹钟音量</span><span>{{ volumes.alarm }}/{{ volumes.alarmMax }}</span></div>
            <input type="range" :min="0" :max="volumes.alarmMax" v-model.number="volumes.alarm" @change="setVolume('alarm', volumes.alarm)" class="w-full" />
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">⚠️ 设备电源</div>
        <p class="text-xs text-gray-400 mb-3">需要 Root 权限</p>
        <div class="flex gap-2">
          <button @click="reboot" class="flex-1 py-3 bg-amber-500 text-white rounded-xl text-sm font-medium active:bg-amber-600">🔄 重启</button>
          <button @click="shutdown" class="flex-1 py-3 bg-red-500 text-white rounded-xl text-sm font-medium active:bg-red-600">⏻ 关机</button>
        </div>
      </div>

      <div v-if="result" class="rounded-xl p-3 text-sm" :class="result.ok ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'">{{ result.msg }}</div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted, onUnmounted, onDeactivated } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const alarmHour = ref(8)
const alarmMinute = ref(0)
const result = ref<{ok:boolean;msg:string}|null>(null)
const finding = ref(false)
const volumes = ref({ music: 0, musicMax: 15, ring: 0, ringMax: 15, alarm: 0, alarmMax: 7 })
let findInterval: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  try {
    const res = await api.get('/api/system/settings')
    volumes.value = res.data
  } catch {}
})

async function setVolume(type: string, value: number) {
  try {
    const key = type === 'music' ? 'musicVolume' : type === 'ring' ? 'ringVolume' : 'alarmVolume'
    await api.post('/api/system/settings', { [key]: value })
  } catch {}
}

async function vibrate(ms: number) {
  try { await api.post('/api/device/vibrate', { duration: ms }) }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '失败') }
}

async function flashlight(on: boolean) {
  try { await api.post('/api/device/flashlight', { on }) }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '失败') }
}

async function flashOn() {
  await flashlight(true)
  setTimeout(() => flashlight(false), 500)
}

async function findPhone() {
  if (finding.value) {
    await stopFinding()
    return
  }

  finding.value = true
  ElMessage.info('正在查找手机...')

  let count = 0
  const doFind = async () => {
    try {
      await api.post('/api/device/vibrate', { duration: 1000 })
      count++
      if (count % 2 === 0) {
        await api.post('/api/device/flashlight', { on: true })
        setTimeout(() => api.post('/api/device/flashlight', { on: false }), 800)
      }
    } catch {}
  }
  await doFind()
  findInterval = setInterval(doFind, 2000)
}

async function stopFinding() {
  if (findInterval) { clearInterval(findInterval); findInterval = null }
  if (finding.value) {
    finding.value = false
    try { await flashlight(false) } catch {}
    try { await api.post('/api/device/vibrate', { duration: 0 }) } catch {}
    ElMessage.success('已停止查找')
  }
}

async function setAlarm() {
  try {
    const r = await api.post('/api/device/alarm', { hour: alarmHour.value, minute: alarmMinute.value, message: '乡亲提醒' })
    result.value = { ok: true, msg: r.data.message }
    ElMessage.success(r.data.message)
  } catch (e: any) {
    result.value = { ok: false, msg: e.response?.data?.error || '失败' }
    ElMessage.error('失败')
  }
}

async function reboot() {
  if (!confirm('确定要重启设备吗？')) return
  try { await api.post('/api/device/reboot'); ElMessage.success('设备即将重启') }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '重启失败') }
}

async function shutdown() {
  if (!confirm('确定要关机吗？')) return
  try { await api.post('/api/device/shutdown'); ElMessage.success('设备即将关机') }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '关机失败') }
}

onUnmounted(() => { stopFinding() })
onDeactivated(() => { stopFinding() })
</script>
