<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white border-b sticky top-0 z-10">
      <div class="flex px-4 py-3">
        <h1 class="text-lg font-bold flex-1">通讯</h1>
        <button v-if="tab==='通话' && calls.length" @click="deleteAllCalls" class="text-xs text-red-500">清空通话</button>
      </div>
      <div class="flex border-b">
        <button v-for="t in ['通话','短信']" :key="t" @click="tab=t"
          class="flex-1 py-2 text-sm font-medium transition-colors"
          :class="tab===t ? 'text-blue-600 border-b-2 border-blue-600' : 'text-gray-500'">{{ t }}</button>
      </div>
      <div class="px-4 py-2 flex gap-2">
        <input v-model="search" type="text" placeholder="搜索号码/姓名/内容..." class="flex-1 border rounded-lg px-3 py-1.5 text-sm" />
        <select v-model="dateRange" class="border rounded-lg px-2 py-1.5 text-sm text-gray-600">
          <option value="9999">全部</option>
          <option value="1">1天</option>
          <option value="7">7天</option>
          <option value="30">30天</option>
          <option value="90">90天</option>
        </select>
      </div>
    </div>
    <div class="p-4">
      <div v-if="loading" class="space-y-2">
        <div v-for="i in 5" :key="i" class="bg-white rounded-xl p-3 shadow-sm animate-pulse">
          <div class="flex items-center justify-between">
            <div class="h-4 bg-gray-200 rounded w-24"></div>
            <div class="h-3 bg-gray-100 rounded w-16"></div>
          </div>
          <div class="h-3 bg-gray-100 rounded w-32 mt-2"></div>
        </div>
      </div>
      <div v-else-if="tab==='通话'">
        <div class="text-xs text-gray-400 mb-2">{{ filteredCalls.length }} 条通话记录</div>
        <div v-if="!filteredCalls.length && !loading" class="text-center text-gray-400 py-8">暂无通话记录</div>
        <div v-for="c in filteredCalls" :key="c.id" class="bg-white rounded-xl p-3 mb-2 shadow-sm">
          <div class="flex items-center justify-between">
            <div class="font-medium text-sm">{{ c.callerName || c.phoneNumber }}</div>
            <div class="flex items-center gap-2">
              <span class="text-xs px-2 py-0.5 rounded" :class="c.callType===3?'bg-red-50 text-red-600':'bg-gray-50 text-gray-600'">
                {{ ['','来电','去电','未接'][c.callType] }}
              </span>
              <button @click="deleteCall(c.id)" class="text-red-400 text-xs">删除</button>
            </div>
          </div>
          <div class="flex justify-between text-xs text-gray-400 mt-1">
            <span>{{ c.phoneNumber }}</span>
            <span>{{ Math.floor(c.durationSeconds/60) }}分{{ c.durationSeconds%60 }}秒 · {{ new Date(c.callTime).toLocaleString() }}</span>
          </div>
        </div>
      </div>
      <div v-if="tab==='短信'">
        <div class="text-xs text-gray-400 mb-2">{{ filteredSms.length }} 条短信</div>
        <div v-if="!filteredSms.length && !loading" class="text-center text-gray-400 py-8">暂无短信</div>
        <div v-for="s in filteredSms" :key="s.id" class="bg-white rounded-xl p-3 mb-2 shadow-sm">
          <div class="flex justify-between items-start">
            <span class="font-medium text-sm">{{ s.senderName || s.phoneNumber }}</span>
            <div class="flex items-center gap-2">
              <span class="text-xs text-gray-400">{{ s.smsType===1?'收':'发' }} · {{ new Date(s.receivedTime).toLocaleString() }}</span>
              <button @click="deleteSms(s.id)" class="text-red-400 text-xs">删除</button>
            </div>
          </div>
          <div class="text-sm text-gray-600 mt-1">{{ s.body }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const tab = ref('通话')
const search = ref('')
const dateRange = ref('9999')
const calls = ref<any[]>([])
const smsList = ref<any[]>([])
const loading = ref(true)

function dateFilter(list: any[], dateField: string) {
  const days = parseInt(dateRange.value)
  const cutoff = Date.now() - days * 86400000
  return list.filter((item: any) => {
    const t = item[dateField] || item.callTime || item.receivedTime || 0
    return t >= cutoff
  })
}

function searchFilter(list: any[], fields: string[]) {
  if (!search.value.trim()) return list
  const q = search.value.toLowerCase()
  return list.filter((item: any) => fields.some(f => String(item[f] || '').toLowerCase().includes(q)))
}

const filteredCalls = computed(() => {
  let result = dateFilter(calls.value, 'callTime')
  result = searchFilter(result, ['phoneNumber', 'callerName'])
  return result
})

const filteredSms = computed(() => {
  let result = dateFilter(smsList.value, 'receivedTime')
  result = searchFilter(result, ['phoneNumber', 'senderName', 'body'])
  return result
})

onMounted(async () => {
  const [c, s] = await Promise.all([api.get('/api/calls'), api.get('/api/sms')])
  calls.value = c.data; smsList.value = s.data; loading.value = false
})

async function deleteCall(id: number) {
  try { await api.post('/api/calls/delete', { callId: String(id) }); calls.value = calls.value.filter(c => c.id !== id); ElMessage.success('已删除') }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '删除失败') }
}
async function deleteAllCalls() {
  try { await api.post('/api/calls/delete', {}); calls.value = []; ElMessage.success('已清空') }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '清空失败') }
}
async function deleteSms(id: number) {
  try { await api.post('/api/sms/delete', { smsId: String(id) }); smsList.value = smsList.value.filter(s => s.id !== id); ElMessage.success('已删除') }
  catch (e: any) { ElMessage.error(e.response?.data?.error || '删除失败') }
}
</script>
