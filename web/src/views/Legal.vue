<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Header -->
    <div class="bg-white border-b sticky top-0 z-10">
      <div class="max-w-2xl mx-auto px-4 py-3 flex items-center gap-3">
        <button @click="$router.back()" class="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 transition-colors text-gray-500">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/></svg>
        </button>
        <h1 class="text-base font-semibold text-gray-800">{{ title }}</h1>
      </div>
    </div>

    <!-- Content -->
    <div class="max-w-2xl mx-auto px-4 py-6">
      <div v-if="loading" class="space-y-4">
        <div class="h-5 bg-gray-200 rounded w-1/3 animate-pulse"></div>
        <div class="h-3 bg-gray-100 rounded w-full animate-pulse"></div>
        <div class="h-3 bg-gray-100 rounded w-5/6 animate-pulse"></div>
        <div class="h-3 bg-gray-100 rounded w-4/6 animate-pulse"></div>
      </div>

      <div v-else-if="content" class="legal-content">
        <div class="text-xs text-gray-400 text-center mb-6">最后更新：{{ lastUpdate }}</div>

        <div v-for="(section, idx) in sections" :key="idx" class="mb-6">
          <div v-if="section.isH2" class="flex items-center gap-2 mb-3 mt-8 first:mt-0">
            <div class="w-1 h-5 rounded-full" :class="sectionColors[idx % sectionColors.length]"></div>
            <h2 class="text-base font-bold text-gray-800">{{ section.title }}</h2>
          </div>

          <div v-else-if="section.isH3" class="font-semibold text-sm text-gray-700 mb-2 mt-4">
            {{ section.title }}
          </div>

          <div v-if="section.text" class="text-sm text-gray-600 leading-relaxed whitespace-pre-wrap">{{ section.text }}</div>

          <ul v-if="section.items.length" class="space-y-1.5">
            <li v-for="(item, i) in section.items" :key="i" class="flex items-start gap-2 text-sm text-gray-600 leading-relaxed">
              <span class="w-1.5 h-1.5 rounded-full bg-gray-300 mt-1.5 flex-shrink-0"></span>
              <span>{{ item }}</span>
            </li>
          </ul>
        </div>
      </div>

      <div v-else class="text-center py-16">
        <div class="text-4xl mb-3">📄</div>
        <div class="text-gray-400 text-sm">暂无内容</div>
      </div>

      <!-- Footer -->
      <div class="mt-12 pt-6 border-t border-gray-100 text-center">
        <div class="text-xs text-gray-400">© 2026 乡亲 · 家庭安全监控</div>
        <div class="text-xs text-gray-300 mt-1">如有疑问请通过设置页面联系我们</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = ref('')
const content = ref('')
const loading = ref(true)

const sectionColors = ['bg-blue-500', 'bg-green-500', 'bg-purple-500', 'bg-orange-500', 'bg-cyan-500', 'bg-pink-500', 'bg-indigo-500']

const lastUpdate = computed(() => {
  const match = content.value.match(/(\d{4}年\d{1,2}月\d{1,2}日)/)
  return match ? match[1] : ''
})

const sections = computed(() => {
  const lines = content.value.split('\n')
  const result: { isH2: boolean; isH3: boolean; title: string; text: string; items: string[] }[] = []
  let current: any = null

  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue

    if (line.startsWith('## ')) {
      if (current) result.push(current)
      current = { isH2: true, isH3: false, title: line.replace(/^##\s*/, ''), text: '', items: [] }
    } else if (line.startsWith('### ')) {
      if (current) result.push(current)
      current = { isH2: false, isH3: true, title: line.replace(/^###\s*/, ''), text: '', items: [] }
    } else if (line.startsWith('- ')) {
      if (current) current.items.push(line.replace(/^-\s*/, '').replace(/\*\*/g, ''))
    } else if (line.startsWith('# ')) {
      // skip h1 title
    } else if (line.startsWith('**') && line.endsWith('**')) {
      // skip bold-only lines (dates etc)
    } else {
      if (current) {
        if (current.text) current.text += '\n'
        current.text += line.replace(/\*\*/g, '')
      }
    }
  }
  if (current) result.push(current)
  return result
})

onMounted(async () => {
  const type = route.query.type as string
  title.value = type === 'privacy' ? '隐私政策' : '用户协议'
  try {
    const res = await fetch(`/api/legal/${type}`)
    content.value = await res.text()
  } catch { content.value = '' }
  loading.value = false
})
</script>
