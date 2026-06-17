<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10"><h1 class="text-lg font-bold">远程通话</h1></div>
    <div class="p-4 space-y-3">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="mb-3">
          <label class="text-sm text-gray-500 mb-1 block">拨打电话</label>
          <input v-model="phoneNumber" type="tel" placeholder="请输入手机号" class="w-full border rounded-xl px-4 py-3 text-sm" />
        </div>

        <div v-if="recentContacts.length" class="mb-3">
          <label class="text-xs text-gray-400 mb-1 block">最近联系人</label>
          <div class="flex gap-2 overflow-x-auto pb-1">
            <button v-for="c in recentContacts" :key="c.phoneNumber" @click="phoneNumber = c.phoneNumber"
              class="px-3 py-1.5 bg-gray-100 rounded-full text-xs whitespace-nowrap flex-shrink-0 active:bg-gray-200">
              {{ c.callerName || c.phoneNumber }}
            </button>
          </div>
        </div>

        <div class="flex gap-2">
          <button @click="makeCall" :disabled="!phoneNumber || calling"
            class="flex-1 py-3 bg-green-500 text-white rounded-xl font-medium active:bg-green-600 disabled:opacity-50">
            {{ calling ? '拨号中...' : '📞 拨号' }}
          </button>
          <button @click="hangup"
            class="flex-1 py-3 bg-red-500 text-white rounded-xl font-medium active:bg-red-600">
            ✖ 挂断
          </button>
        </div>
      </div>

      <div v-if="lastResult" class="rounded-xl p-3 text-sm" :class="lastResult.ok ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'">
        {{ lastResult.msg }}
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const phoneNumber = ref('')
const calling = ref(false)
const lastResult = ref<{ok:boolean;msg:string}|null>(null)
const recentContacts = ref<any[]>([])

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

async function makeCall() {
  calling.value = true; lastResult.value = null
  try {
    const r = await api.post('/api/device/call', { phoneNumber: phoneNumber.value })
    lastResult.value = { ok: true, msg: r.data.message }; ElMessage.success('正在拨号')
  } catch (e: any) { lastResult.value = { ok: false, msg: e.response?.data?.error || '拨号失败' }; ElMessage.error('拨号失败') }
  calling.value = false
}

async function hangup() {
  try {
    await api.post('/api/device/hangup')
    lastResult.value = { ok: true, msg: '电话已挂断' }; ElMessage.success('已挂断')
  } catch (e: any) { lastResult.value = { ok: false, msg: e.response?.data?.error || '挂断失败' }; ElMessage.error('挂断失败') }
}
</script>
