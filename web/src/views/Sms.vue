<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <h1 class="text-lg font-bold">短信记录</h1>
    </div>
    <div class="p-4 space-y-2">
      <div v-if="!list.length && !loading" class="text-center text-gray-400 py-8">暂无短信</div>
      <div v-for="s in list" :key="s.id" class="bg-white rounded-xl p-3 shadow-sm">
        <div class="flex justify-between">
          <span class="font-medium text-sm">{{ s.senderName || s.phoneNumber }}</span>
          <span class="text-xs text-gray-400">{{ s.smsType===1?'收':'发' }} · {{ new Date(s.receivedTime).toLocaleString() }}</span>
        </div>
        <div class="text-sm text-gray-600 mt-1">{{ s.body }}</div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
const list = ref<any[]>([]); const loading = ref(true)
onMounted(async () => { list.value = (await api.get('/api/sms')).data; loading.value = false })
</script>
