<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">媒体文件</h1>
        <button @click="rescan" :disabled="scanning" class="text-blue-500 text-sm">{{ scanning ? '扫描中...' : '重新扫描' }}</button>
      </div>
      <div class="flex gap-2 mt-2 overflow-x-auto">
        <button v-for="tab in mediaTabs" :key="tab.key" @click="switchTab(tab.key)"
          class="px-3 py-1 rounded-full text-xs font-medium transition-colors whitespace-nowrap"
          :class="activeTab === tab.key ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-500'">
          {{ tab.icon }} {{ tab.label }}
        </button>
      </div>
    </div>

    <div class="p-4 space-y-4">
      <!-- Images -->
      <div v-if="activeTab === 'image'">
        <div class="text-xs text-gray-400 mb-2">共 {{ totalCount }} 张图片</div>
        <div class="grid grid-cols-3 md:grid-cols-6 gap-2">
          <div v-for="f in imageFiles" :key="f.id" @click="previewImage(f)"
            class="aspect-square bg-white rounded-lg overflow-hidden shadow-sm cursor-pointer active:opacity-80 flex items-center justify-center">
            <img :src="`/api/media/file?path=${encodeURIComponent(f.filePath)}`" class="w-full h-full object-cover" loading="lazy" onerror="this.style.display='none';this.parentElement.classList.add('hidden')" />
          </div>
        </div>
        <div v-if="typeLoading" class="text-center py-4 text-gray-400 text-sm">加载中...</div>
        <div v-else-if="hasMoreType" class="text-center py-4">
          <button @click="loadMoreType" class="text-blue-500 text-sm px-4 py-2 bg-blue-50 rounded-lg">加载更多</button>
        </div>
        <div v-else-if="imageFiles.length > 0" class="text-center py-4 text-gray-400 text-xs">已加载全部</div>
      </div>

      <!-- Videos -->
      <div v-if="activeTab === 'video'">
        <div class="text-xs text-gray-400 mb-2">共 {{ totalCount }} 个视频</div>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          <div v-for="f in videoFiles" :key="f.id"
            class="bg-white rounded-xl shadow-sm overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
            @click="playVideo(f)">
            <div class="aspect-video bg-gray-900 flex items-center justify-center relative group">
              <div class="w-14 h-14 bg-black/50 rounded-full flex items-center justify-center group-hover:bg-black/70 transition-colors">
                <span class="text-white text-2xl ml-1">▶</span>
              </div>
              <div class="absolute bottom-2 right-2 bg-black/60 text-white text-[10px] px-1.5 py-0.5 rounded">{{ fmtB(f.fileSize) }}</div>
            </div>
            <div class="px-3 py-2.5">
              <div class="font-medium text-sm truncate">{{ f.fileName }}</div>
              <div class="text-xs text-gray-400 mt-0.5">{{ fmtDate(f.dateAdded) }}</div>
            </div>
          </div>
        </div>
        <div v-if="typeLoading" class="text-center py-4 text-gray-400 text-sm">加载中...</div>
        <div v-else-if="hasMoreType" class="text-center py-4">
          <button @click="loadMoreType" class="text-blue-500 text-sm px-4 py-2 bg-blue-50 rounded-lg">加载更多</button>
        </div>
        <div v-else-if="videoFiles.length > 0" class="text-center py-4 text-gray-400 text-xs">已加载全部</div>
      </div>

      <!-- Audios -->
      <div v-if="activeTab === 'audio'">
        <div class="text-xs text-gray-400 mb-2">共 {{ totalCount }} 个音频</div>
        <div v-for="f in audioFiles" :key="f.id" class="bg-white rounded-xl p-3 shadow-sm mb-2">
          <div class="flex items-center gap-3 mb-2">
            <span class="text-2xl">🎵</span>
            <div class="flex-1 min-w-0">
              <div class="font-medium text-sm truncate">{{ f.fileName }}</div>
              <div class="text-xs text-gray-400">{{ fmtB(f.fileSize) }} · {{ fmtDate(f.dateAdded) }}</div>
            </div>
          </div>
          <audio :src="`/api/media/file?path=${encodeURIComponent(f.filePath)}`" controls class="w-full h-8" preload="none"></audio>
        </div>
        <div v-if="typeLoading" class="text-center py-4 text-gray-400 text-sm">加载中...</div>
        <div v-else-if="hasMoreType" class="text-center py-4">
          <button @click="loadMoreType" class="text-blue-500 text-sm px-4 py-2 bg-blue-50 rounded-lg">加载更多</button>
        </div>
        <div v-else-if="audioFiles.length > 0" class="text-center py-4 text-gray-400 text-xs">已加载全部</div>
      </div>

      <div v-if="!allLoaded && !scanning" class="text-center text-gray-400 py-8">暂无媒体文件</div>
    </div>

    <!-- Video player overlay -->
    <Teleport to="body">
      <div v-if="videoUrl" @click.self="stopVideo"
        class="fixed inset-0 bg-black/95 z-[100] flex flex-col items-center justify-center"
        @keydown.esc="stopVideo" tabindex="0" ref="playerRef">
        <div class="absolute top-0 left-0 right-0 flex items-center justify-between px-4 py-3 bg-gradient-to-b from-black/60 to-transparent">
          <span class="text-white text-sm truncate flex-1">{{ currentVideoName }}</span>
          <button @click="stopVideo" class="text-white/70 hover:text-white text-3xl leading-none ml-4 transition-colors">&times;</button>
        </div>
        <video ref="videoEl" :src="videoUrl" controls autoplay
          class="max-w-full max-h-full w-auto h-auto"
          @ended="stopVideo"></video>
      </div>
    </Teleport>

    <!-- Image preview overlay -->
    <Teleport to="body">
      <div v-if="previewUrl" @click="previewUrl=''"
        class="fixed inset-0 bg-black/95 z-[100] flex items-center justify-center p-4 cursor-pointer">
        <img :src="previewUrl" class="max-w-full max-h-full rounded-lg object-contain" @click.stop />
        <button @click="previewUrl=''" class="absolute top-4 right-4 text-white/70 hover:text-white text-3xl leading-none transition-colors">&times;</button>
      </div>
    </Teleport>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { api } from '../api/client'

const PAGE_SIZE = 100

const activeTab = ref('image')
const imageFiles = ref<any[]>([])
const videoFiles = ref<any[]>([])
const audioFiles = ref<any[]>([])
const scanning = ref(false)
const typeLoading = ref(false)
const hasMoreType = ref(true)
const totalCount = ref(0)

const videoUrl = ref('')
const currentVideoName = ref('')
const previewUrl = ref('')
const playerRef = ref<HTMLElement>()
const videoEl = ref<HTMLVideoElement>()

const allLoaded = computed(() => imageFiles.value.length + videoFiles.value.length + audioFiles.value.length > 0)

const mediaTabs = computed(() => [
  { key: 'image', icon: '🖼️', label: `图片 (${imageFiles.value.length})` },
  { key: 'video', icon: '🎬', label: `视频 (${videoFiles.value.length})` },
  { key: 'audio', icon: '🎵', label: `音频 (${audioFiles.value.length})` },
])

async function switchTab(key: string) {
  if (activeTab.value === key) return
  activeTab.value = key
  await loadTypeData(true)
}

async function loadTypeData(reset = false) {
  if (typeLoading.value) return
  typeLoading.value = true
  try {
    if (reset) {
      hasMoreType.value = true
      if (activeTab.value === 'image') imageFiles.value = []
      if (activeTab.value === 'video') videoFiles.value = []
      if (activeTab.value === 'audio') audioFiles.value = []
    }
    const current = activeTab.value === 'image' ? imageFiles.value : activeTab.value === 'video' ? videoFiles.value : audioFiles.value
    const res = await api.get('/api/media', { params: { type: activeTab.value, limit: PAGE_SIZE, offset: current.length } })
    const newFiles = res.data.files || []
    totalCount.value = res.data.counts?.[activeTab.value === 'audio' ? 'audio' : activeTab.value + 's'] || 0
    if (newFiles.length < PAGE_SIZE) hasMoreType.value = false
    if (activeTab.value === 'image') imageFiles.value = [...imageFiles.value, ...newFiles]
    else if (activeTab.value === 'video') videoFiles.value = [...videoFiles.value, ...newFiles]
    else audioFiles.value = [...audioFiles.value, ...newFiles]
  } catch {}
  typeLoading.value = false
}

function loadMoreType() {
  loadTypeData(false)
}

async function rescan() {
  scanning.value = true
  try {
    await api.post('/api/media/cleanup')
    imageFiles.value = []
    videoFiles.value = []
    audioFiles.value = []
    await loadTypeData(true)
  } catch {}
  scanning.value = false
}

onMounted(() => loadTypeData(true))

watch(videoUrl, (v) => {
  if (v) nextTick(() => playerRef.value?.focus())
})

function previewImage(f: any) {
  previewUrl.value = `/api/media/file?path=${encodeURIComponent(f.filePath)}`
}

function playVideo(f: any) {
  currentVideoName.value = f.fileName
  videoUrl.value = `/api/media/file?path=${encodeURIComponent(f.filePath)}`
}

function stopVideo() {
  if (videoEl.value) { videoEl.value.pause(); videoEl.value.src = '' }
  videoUrl.value = ''
  currentVideoName.value = ''
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (videoUrl.value) { stopVideo(); return }
    if (previewUrl.value) { previewUrl.value = ''; return }
  }
}

function fmtB(b: number) {
  if (b < 1024) return b + 'B'
  if (b < 1048576) return (b / 1024).toFixed(1) + 'KB'
  if (b < 1073741824) return (b / 1048576).toFixed(1) + 'MB'
  return (b / 1073741824).toFixed(1) + 'GB'
}

function fmtDate(ts: number) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>
