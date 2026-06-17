<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">远程发短信</h1>
        <button @click="mode = mode === 'single' ? 'bulk' : 'single'" class="text-blue-500 text-sm">
          {{ mode === 'single' ? '群发模式' : '单发模式' }}
        </button>
      </div>
    </div>
    <div class="p-4 space-y-3">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="mb-3">
          <label class="text-sm text-gray-500 mb-1 block">收件人</label>
          <input v-if="mode === 'single'" v-model="form.phoneNumber" type="tel" placeholder="请输入手机号" class="w-full border rounded-xl px-4 py-3 text-sm" />
          <textarea v-else v-model="bulkNumbers" placeholder="每行一个手机号，或用逗号分隔" rows="3" class="w-full border rounded-xl px-4 py-3 text-sm resize-none"></textarea>
          <div v-if="mode === 'bulk' && bulkCount > 0" class="text-xs text-gray-400 mt-1">共 {{ bulkCount }} 个收件人</div>
        </div>

        <div v-if="recentContacts.length" class="mb-3">
          <label class="text-xs text-gray-400 mb-1 block">最近联系人</label>
          <div class="flex gap-2 overflow-x-auto pb-1">
            <button v-for="c in recentContacts" :key="c.phoneNumber" @click="addRecipient(c.phoneNumber)"
              class="px-3 py-1.5 bg-gray-100 rounded-full text-xs whitespace-nowrap flex-shrink-0 active:bg-gray-200">
              {{ c.callerName || c.phoneNumber }}
            </button>
          </div>
        </div>

        <div class="mb-3">
          <label class="text-sm text-gray-500 mb-1 block">短信内容</label>
          <textarea v-model="form.message" placeholder="请输入短信内容" rows="4" class="w-full border rounded-xl px-4 py-3 text-sm resize-none"></textarea>
          <div class="text-xs text-gray-400 mt-1 text-right">{{ form.message.length }} 字</div>
        </div>

        <button @click="sendSms" :disabled="!canSend || sending"
          class="w-full py-3 bg-blue-500 text-white rounded-xl font-medium active:bg-blue-600 disabled:opacity-50">
          {{ sending ? `发送中 (${sentCount}/${totalCount})...` : `发送短信${mode === 'bulk' ? ` (${bulkCount}人)` : ''}` }}
        </button>
      </div>

      <div v-if="lastResult" class="rounded-xl p-3 text-sm" :class="lastResult.ok ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'">
        {{ lastResult.msg }}
      </div>

      <div v-if="sendResults.length" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-2">发送结果</div>
        <div v-for="r in sendResults" :key="r.number" class="flex justify-between items-center py-1 text-sm">
          <span>{{ r.number }}</span>
          <span :class="r.ok ? 'text-green-500' : 'text-red-500'">{{ r.ok ? '✓' : '✗' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const mode = ref<'single' | 'bulk'>('single')
const form = ref({ phoneNumber: '', message: '' })
const bulkNumbers = ref('')
const sending = ref(false)
const lastResult = ref<{ok:boolean;msg:string}|null>(null)
const recentContacts = ref<any[]>([])
const sentCount = ref(0)
const totalCount = ref(0)
const sendResults = ref<{number: string; ok: boolean}[]>([])

const bulkCount = computed(() => {
  return bulkNumbers.value.split(/[\n,，]/).map(s => s.trim()).filter(s => s).length
})

const canSend = computed(() => {
  if (!form.value.message) return false
  if (mode.value === 'single') return !!form.value.phoneNumber
  return bulkCount.value > 0
})

onMounted(async () => {
  try {
    const res = await api.get('/api/calls')
    const calls = res.data || []
    const seen = new Set<string>()
    recentContacts.value = calls
      .filter((c: any) => { if (seen.has(c.phoneNumber)) return false; seen.add(c.phoneNumber); return true })
      .slice(0, 6)
  } catch {}
})

function addRecipient(number: string) {
  if (mode.value === 'single') {
    form.value.phoneNumber = number
  } else {
    if (bulkNumbers.value) bulkNumbers.value += '\n'
    bulkNumbers.value += number
  }
}

async function sendSms() {
  sending.value = true; lastResult.value = null; sendResults.value = []
  if (mode.value === 'single') {
    try {
      const r = await api.post('/api/device/sms', form.value)
      lastResult.value = { ok: true, msg: r.data.message }
      ElMessage.success('短信已发送')
      form.value = { phoneNumber: '', message: '' }
    } catch (e: any) {
      lastResult.value = { ok: false, msg: e.response?.data?.error || '发送失败' }
      ElMessage.error('发送失败')
    }
  } else {
    const numbers = bulkNumbers.value.split(/[\n,，]/).map(s => s.trim()).filter(s => s)
    totalCount.value = numbers.length; sentCount.value = 0
    for (const num of numbers) {
      try {
        await api.post('/api/device/sms', { phoneNumber: num, message: form.value.message })
        sendResults.value.push({ number: num, ok: true })
      } catch {
        sendResults.value.push({ number: num, ok: false })
      }
      sentCount.value++
    }
    const success = sendResults.value.filter(r => r.ok).length
    lastResult.value = { ok: success > 0, msg: `发送完成: ${success}/${numbers.length} 成功` }
  }
  sending.value = false
}
</script>
