<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center gap-2">
        <button v-if="currentPath !== '/sdcard'" @click="goUp" class="text-blue-500 text-sm">← 返回</button>
        <h1 class="text-lg font-bold flex-1 truncate">文件管理</h1>
      </div>
    </div>
    <div class="p-4">
      <div class="flex items-center gap-1 text-xs text-gray-400 mb-3 overflow-x-auto whitespace-nowrap">
        <span v-for="(crumb, i) in breadcrumbs" :key="i" class="flex items-center gap-1">
          <button @click="navigateTo(crumb.path)" class="text-blue-500 hover:underline">{{ crumb.name }}</button>
          <span v-if="i < breadcrumbs.length - 1">/</span>
        </span>
      </div>
      <div v-if="!files.length && !loading" class="text-center text-gray-400 py-8">空目录</div>
      <div v-for="f in files" :key="f.path" @click="openFile(f)"
        class="bg-white rounded-xl p-3 shadow-sm mb-2 flex items-center gap-3 active:bg-gray-100 cursor-pointer">
        <span class="text-2xl">{{ f.isDirectory ? '📁' : getFileIcon(f.name) }}</span>
        <div class="flex-1 min-w-0">
          <div class="font-medium text-sm truncate">{{ f.name }}</div>
          <div class="text-xs text-gray-400">{{ f.isDirectory ? '文件夹' : fmtSize(f.size) }}</div>
        </div>
        <button v-if="!f.isDirectory" @click.stop="downloadFile(f)" class="text-blue-500 text-xs px-2 py-1 rounded bg-blue-50">下载</button>
      </div>
    </div>

    <div v-if="previewUrl" @click="previewUrl=''" class="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4 cursor-pointer">
      <img :src="previewUrl" class="max-w-full max-h-full rounded-lg shadow-lg" @click.stop />
      <button @click="previewUrl=''" class="absolute top-4 right-4 text-white text-2xl">✕</button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'

const files = ref<any[]>([])
const loading = ref(true)
const currentPath = ref('/sdcard')
const previewUrl = ref('')

const breadcrumbs = computed(() => {
  const parts = currentPath.value.split('/').filter(Boolean)
  return parts.map((name, i) => ({
    name,
    path: '/' + parts.slice(0, i + 1).join('/')
  }))
})

onMounted(() => loadDir('/sdcard'))

async function loadDir(path: string) {
  loading.value = true; currentPath.value = path
  try {
    const r = await api.get(`/api/files/list?path=${encodeURIComponent(path)}`)
    files.value = r.data.files || []
  } catch { files.value = [] }
  loading.value = false
}

function openFile(f: any) {
  if (f.isDirectory) {
    loadDir(f.path)
  } else if (isImage(f.name)) {
    previewUrl.value = `/api/media/file?path=${encodeURIComponent(f.path)}`
  } else {
    window.open(`/api/files/${encodeURIComponent(f.path)}`, '_blank')
  }
}

function downloadFile(f: any) {
  window.open(`/api/files/${encodeURIComponent(f.path)}`, '_blank')
}

function navigateTo(path: string) {
  loadDir(path)
}

function goUp() {
  const parts = currentPath.value.split('/')
  parts.pop()
  loadDir(parts.join('/') || '/sdcard')
}

function isImage(name: string) {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return ['jpg','jpeg','png','gif','webp','bmp'].includes(ext)
}

function fmtSize(s: number) {
  if (s < 1024) return s + ' B'
  if (s < 1048576) return (s/1024).toFixed(1) + ' KB'
  if (s < 1073741824) return (s/1048576).toFixed(1) + ' MB'
  return (s/1073741824).toFixed(1) + ' GB'
}

function getFileIcon(name: string) {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (['jpg','jpeg','png','gif','webp'].includes(ext)) return '🖼️'
  if (['mp4','mkv','avi','mov'].includes(ext)) return '🎬'
  if (['mp3','aac','wav','flac'].includes(ext)) return '🎵'
  if (['pdf'].includes(ext)) return '📄'
  if (['zip','rar','7z'].includes(ext)) return '📦'
  if (['apk'].includes(ext)) return '📱'
  if (['txt','md','json','xml','csv'].includes(ext)) return '📝'
  return '📄'
}
</script>
