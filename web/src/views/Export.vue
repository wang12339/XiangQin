<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center gap-2">
        <button @click="$router.back()" class="text-blue-500 text-sm">← 返回</button>
        <h1 class="text-lg font-bold">数据导出</h1>
      </div>
    </div>
    <div class="p-4 space-y-3">
      <div v-for="item in exports" :key="item.key" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="flex items-center justify-between mb-2">
          <div>
            <div class="font-medium text-sm">{{ item.icon }} {{ item.label }}</div>
            <div class="text-xs text-gray-400">{{ item.desc }}</div>
          </div>
          <span v-if="item.count !== undefined" class="text-xs text-gray-400">{{ item.count }} 条</span>
        </div>
        <div class="flex gap-2">
          <button @click="exportData(item.key, 'csv')" :disabled="exporting === item.key"
            class="flex-1 py-2 bg-blue-50 text-blue-600 rounded-lg text-sm font-medium active:bg-blue-100 disabled:opacity-50">
            {{ exporting === item.key ? '导出中...' : 'CSV' }}
          </button>
          <button @click="exportData(item.key, 'json')" :disabled="exporting === item.key"
            class="flex-1 py-2 bg-gray-50 text-gray-600 rounded-lg text-sm font-medium active:bg-gray-100 disabled:opacity-50">
            {{ exporting === item.key ? '导出中...' : 'JSON' }}
          </button>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium text-sm mb-3">📊 导出说明</div>
        <div class="space-y-2 text-xs text-gray-500">
          <div>• CSV 格式可在 Excel 中打开</div>
          <div>• JSON 格式适合程序处理</div>
          <div>• 导出的数据为最近 30 天的记录</div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const exporting = ref('')

const exports = ref([
  { key: 'calls', icon: '📞', label: '通话记录', desc: '导出所有通话记录', count: 0 },
  { key: 'sms', icon: '💬', label: '短信记录', desc: '导出所有短信记录', count: 0 },
  { key: 'usage', icon: '📱', label: '应用使用', desc: '导出应用使用统计', count: 0 },
])

onMounted(async () => {
  try {
    const [calls, sms, usage] = await Promise.all([
      api.get('/api/calls').catch(() => ({ data: [] })),
      api.get('/api/sms').catch(() => ({ data: [] })),
      api.get('/api/usage').catch(() => ({ data: [] })),
    ])
    exports.value[0].count = (calls.data || []).length
    exports.value[1].count = (sms.data || []).length
    exports.value[2].count = (usage.data || []).length
  } catch {}
})

async function exportData(key: string, format: string) {
  exporting.value = key
  try {
    const res = await api.get(`/api/export/${key}/${format}`, { responseType: 'blob' })
    const blob = new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `xiangqin_${key}_${new Date().toISOString().slice(0,10)}.${format}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`${key} 导出成功`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '导出失败')
  }
  exporting.value = ''
}
</script>
