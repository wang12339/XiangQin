<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-bold">联系人</h1>
        <div class="flex gap-2">
          <button @click="refresh" class="text-gray-400 text-sm">🔄</button>
          <button @click="showAdd = !showAdd" class="text-blue-500 text-sm">+ 添加</button>
        </div>
      </div>
    </div>
    <div class="p-4">
      <div v-if="showAdd" class="bg-white rounded-xl p-4 shadow-sm mb-3">
        <input v-model="newContact.name" placeholder="姓名" class="w-full border rounded-lg px-3 py-2 text-sm mb-2" />
        <input v-model="newContact.phone" type="tel" placeholder="手机号" class="w-full border rounded-lg px-3 py-2 text-sm mb-2" />
        <button @click="addContact" :disabled="!newContact.name || !newContact.phone"
          class="w-full py-2.5 bg-blue-500 text-white rounded-xl text-sm font-medium active:bg-blue-600 disabled:opacity-50">
          添加联系人
        </button>
      </div>

      <div v-if="loading" class="space-y-2">
        <div v-for="i in 5" :key="i" class="bg-white rounded-xl p-3 shadow-sm animate-pulse">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full bg-gray-200"></div>
            <div class="flex-1 space-y-2">
              <div class="h-4 bg-gray-200 rounded w-24"></div>
              <div class="h-3 bg-gray-100 rounded w-32"></div>
            </div>
          </div>
        </div>
      </div>

      <input v-model="query" @input="onSearch" placeholder="搜索联系人..." class="w-full border rounded-xl px-4 py-3 text-sm mb-3 bg-white" />
      <div class="text-xs text-gray-400 mb-2">共 {{ filtered.length }} 个联系人</div>

      <div v-for="c in filtered" :key="c.id" class="bg-white rounded-xl p-3 shadow-sm mb-2 flex items-center gap-3">
        <div class="w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm text-white flex-shrink-0"
          :style="{ background: avatarColor(c.name) }">
          {{ (c.name || '?')[0] }}
        </div>
        <div class="flex-1 min-w-0">
          <div class="font-medium text-sm">{{ c.name }}</div>
          <div class="text-xs text-gray-400">{{ formatPhone(c.phone) }}</div>
        </div>
        <div class="flex gap-1">
          <a v-if="c.phone" :href="`tel:${c.phone}`" class="text-green-500 text-xs px-2 py-1 rounded bg-green-50">拨打</a>
          <button @click="deleteContact(c.id)" class="text-red-400 text-xs px-2 py-1">删除</button>
        </div>
      </div>
      <div v-if="!filtered.length && !loading" class="text-center text-gray-400 py-8">暂无联系人</div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const contacts = ref<any[]>([])
const query = ref('')
const loading = ref(true)
const showAdd = ref(false)
const newContact = ref({ name: '', phone: '' })

onMounted(loadData)

async function loadData() {
  loading.value = true
  const r = await api.get('/api/contacts')
  contacts.value = r.data.contacts || []
  loading.value = false
}

async function refresh() {
  await loadData()
  ElMessage.success('已刷新')
}

const filtered = computed(() => {
  if (!query.value.trim()) return contacts.value
  const q = query.value.toLowerCase()
  return contacts.value.filter(c =>
    (c.name || '').toLowerCase().includes(q) ||
    (c.phone || '').includes(q)
  )
})

async function onSearch() {
  if (!query.value.trim()) { await loadData(); return }
  const r = await api.get(`/api/contacts?q=${query.value}`)
  contacts.value = r.data.contacts || []
}

async function addContact() {
  try {
    await api.post('/api/contacts/add', newContact.value)
    ElMessage.success('联系人已添加'); showAdd.value = false; newContact.value = { name: '', phone: '' }
    await loadData()
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '添加失败') }
}

async function deleteContact(id: string) {
  try {
    await api.post('/api/contacts/delete', { contactId: id })
    ElMessage.success('已删除'); contacts.value = contacts.value.filter(c => c.id !== id)
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '删除失败') }
}

function avatarColor(name: string) {
  const colors = ['#3b82f6', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#f97316']
  const hash = (name || '').split('').reduce((h, c) => ((h << 5) - h + c.charCodeAt(0)) | 0, 0)
  return colors[Math.abs(hash) % colors.length]
}

function formatPhone(phone: string) {
  if (!phone) return '无电话'
  return phone.replace(/(\d{3})(\d{4})(\d{4})/, '$1 $2 $3')
}
</script>
