<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <h1 class="text-lg font-bold">流量统计</h1>
    </div>
    <div class="p-4 space-y-4">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="grid grid-cols-2 gap-4">
          <div class="text-center">
            <div class="text-2xl font-bold text-blue-500">{{ fmtB(totalRx) }}</div>
            <div class="text-xs text-gray-400 mt-1">总下载</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-orange-500">{{ fmtB(totalTx) }}</div>
            <div class="text-xs text-gray-400 mt-1">总上传</div>
          </div>
        </div>
      </div>

      <div v-if="topApps.length" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">流量 Top 10</div>
        <div class="space-y-2">
          <div v-for="(t, i) in topApps" :key="t.id" class="flex items-center gap-2">
            <span class="text-xs text-gray-400 w-4 text-right">{{ i + 1 }}</span>
            <div class="flex-1 min-w-0">
              <div class="flex justify-between items-center mb-1">
                <span class="text-xs font-medium truncate">{{ t.appName || t.packageName }}</span>
                <span class="text-xs text-gray-500">{{ fmtB(t.rxBytes + t.txBytes) }}</span>
              </div>
              <div class="flex w-full h-2 rounded-full overflow-hidden bg-gray-100">
                <div class="bg-blue-400 h-full" :style="{ width: rxWidth(t.rxBytes) + '%' }"></div>
                <div class="bg-orange-400 h-full" :style="{ width: txWidth(t.txBytes) + '%' }"></div>
              </div>
            </div>
          </div>
        </div>
        <div class="flex justify-center gap-4 mt-3 text-xs text-gray-500">
          <span class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-blue-400 inline-block"></span>下载</span>
          <span class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-orange-400 inline-block"></span>上传</span>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">全部应用 ({{ list.length }})</div>
        <div v-if="!list.length && !loading" class="text-center text-gray-400 py-4">暂无数据</div>
        <div v-for="t in list" :key="t.id" class="flex justify-between items-center py-2 border-b border-gray-50 last:border-0">
          <span class="font-medium text-sm truncate flex-1">{{ t.appName || t.packageName }}</span>
          <div class="text-right ml-2">
            <div class="text-sm text-blue-600">↓{{ fmtB(t.rxBytes) }}</div>
            <div class="text-xs text-orange-500">↑{{ fmtB(t.txBytes) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'

const list = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  list.value = (await api.get('/api/traffic')).data.traffic || []
  loading.value = false
})

const totalRx = computed(() => list.value.reduce((s, t) => s + (t.rxBytes || 0), 0))
const totalTx = computed(() => list.value.reduce((s, t) => s + (t.txBytes || 0), 0))
const topApps = computed(() => list.value.slice(0, 10))
const maxBytes = computed(() => Math.max(...list.value.map(t => (t.rxBytes || 0) + (t.txBytes || 0)), 1))

function rxWidth(rx: number) { return ((rx / maxBytes.value) * 100).toFixed(1) }
function txWidth(tx: number) { return ((tx / maxBytes.value) * 100).toFixed(1) }

function fmtB(b: number) {
  if (b < 1024) return b + 'B'
  if (b < 1048576) return (b / 1024).toFixed(1) + 'KB'
  if (b < 1073741824) return (b / 1048576).toFixed(1) + 'MB'
  return (b / 1073741824).toFixed(2) + 'GB'
}
</script>
