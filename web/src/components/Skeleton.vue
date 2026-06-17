<template>
  <div class="space-y-3">
    <div v-for="i in rows" :key="i" class="bg-white rounded-xl p-4 shadow-sm skeleton">
      <div class="flex items-center gap-3">
        <div v-if="avatar" class="w-10 h-10 rounded-full skeleton-bg"></div>
        <div class="flex-1 space-y-2">
          <div class="h-4 skeleton-bg rounded" :style="{ width: titleWidth }"></div>
          <div v-if="subtitle" class="h-3 skeleton-bg-light rounded w-1/2"></div>
        </div>
        <div v-if="action" class="w-12 h-6 skeleton-bg rounded"></div>
      </div>
      <div v-if="lines && lines > 0" class="mt-3 space-y-2">
        <div v-for="j in lines" :key="j" class="h-3 skeleton-bg-light rounded"
          :style="{ width: (100 - j * 15) + '%' }"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  rows?: number
  avatar?: boolean
  subtitle?: boolean
  action?: boolean
  titleWidth?: string
  lines?: number
}>()
</script>

<style scoped>
.skeleton { animation: skeleton-pulse 1.5s ease-in-out infinite; }
.skeleton-bg { background: linear-gradient(90deg, #e5e7eb 25%, #f3f4f6 50%, #e5e7eb 75%); background-size: 200% 100%; animation: skeleton-shine 1.5s ease-in-out infinite; }
.skeleton-bg-light { background: linear-gradient(90deg, #f3f4f6 25%, #f9fafb 50%, #f3f4f6 75%); background-size: 200% 100%; animation: skeleton-shine 1.5s ease-in-out infinite; }
@keyframes skeleton-shine { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
@keyframes skeleton-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.8; } }
</style>
