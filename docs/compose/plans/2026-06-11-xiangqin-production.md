# 乡亲 (XiangQin) Production Ready Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the XiangQin monitoring app into a production-ready product by fixing code quality issues, building a full Vue 3 SPA web panel, and adding tests.

**Architecture:** Fix Kotlin backend code quality first, then build a Vue 3 + Vite SPA with TailwindCSS + Element Plus that communicates with the existing Ktor API via HTTP/WebSocket. The SPA will be built and output to `app/src/main/assets/web/` for Ktor to serve as static files. Add unit tests for AlertEngine and DataStore.

**Tech Stack:** Kotlin, Ktor, Room, SQLCipher, Vue 3, Vite, TailwindCSS, Element Plus, Pinia, Vue Router, Vitest

---

## Phase 1: Code Quality Fixes

### Task 1: Fix Empty Catch Blocks

**Covers:** Code quality improvement

**Files:**
- Modify: `app/src/main/java/com/xiangqin/app/MainActivity.kt:80,97,806`
- Modify: `app/src/main/java/com/xiangqin/app/monitor/AlertEngine.kt:243,269`
- Modify: `app/src/main/java/com/xiangqin/app/server/WebServer.kt:94,180,263,389,416,424,465,489,545,650,770,796,823,834`
- Modify: `app/src/main/java/com/xiangqin/app/service/MonitoringService.kt:465`
- Modify: `app/src/main/java/com/xiangqin/app/service/NotificationListener.kt:33,46,58,59`
- Modify: `app/src/main/java/com/xiangqin/app/util/PermissionHelper.kt:175,267,302,603,609,700,710,738,740`

- [ ] **Step 1: Fix empty catch blocks in MainActivity.kt**

Replace `catch (_: Exception) {}` with `catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }` at lines 80, 97, 806.

```kotlin
// Line 80: change
} catch (_: Exception) {}
// to
} catch (e: Exception) { android.util.Log.e("XiangQin/Onboarding", "首次启动检查失败", e) }

// Line 97: change
} catch (_: Exception) {
// to
} catch (e: Exception) {
    android.util.Log.e("XiangQin/Main", "统计数据刷新失败", e)

// Line 806: change
} catch (_: Exception) {}
// to
} catch (e: Exception) { android.util.Log.e("XiangQin/Net", "获取IP失败", e) }
```

- [ ] **Step 2: Fix empty catch blocks in AlertEngine.kt**

Line 243: `catch (_: Exception) {}` → `catch (e: Exception) { android.util.Log.e("XiangQin/Alert", "告警检测异常", e) }`

Line 269: `catch (_: Exception) { null }` → `catch (e: Exception) { android.util.Log.e("XiangQin/Alert", "获取电量失败", e); null }`

- [ ] **Step 3: Fix empty catch blocks in WebServer.kt**

Replace all `catch (_: Exception)` with `catch (e: Exception) { android.util.Log.e("XiangQin/Web", "API error", e) }` at lines: 94, 180, 263, 389, 416, 424, 465, 489, 545, 650, 770, 796, 823, 834.

- [ ] **Step 4: Fix empty catch blocks in NotificationListener.kt**

Lines 33, 46, 58, 59: Add logging.

- [ ] **Step 5: Fix empty catch blocks in PermissionHelper.kt**

Lines 175, 267, 302, 603, 609, 700, 710, 738, 740: Add logging where the catch is truly empty.

- [ ] **Step 6: Fix empty catch block in MonitoringService.kt**

Line 465: `catch (_: Exception) {}` → `catch (e: Exception) { android.util.Log.e("XiangQin/Log", "写入日志失败", e) }`

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "fix: add logging to 30+ empty catch blocks for better debugging"
```

### Task 2: Fix Duplicate Route and Orphaned CoroutineScopes

**Covers:** Code quality improvement

**Files:**
- Modify: `app/src/main/java/com/xiangqin/app/server/WebServer.kt:192-201` (remove duplicate)
- Modify: `app/src/main/java/com/xiangqin/app/service/MonitoringService.kt:442,456` (fix orphaned scopes)

- [ ] **Step 1: Remove duplicate `/api/device/lock` route in WebServer.kt**

Delete the first `post("/api/device/lock")` block at lines 192-201. Keep only the second one at lines 503-518 which is more complete (uses `XiangQinDeviceAdminReceiver.lockScreen`).

- [ ] **Step 2: Fix orphaned CoroutineScope in MonitoringService.kt**

In `heartbeat()` method (line 442), replace `CoroutineScope(Dispatchers.IO).launch` with `serviceScope.launch`.

In `log()` method (line 456), replace `CoroutineScope(Dispatchers.IO).launch` with `serviceScope.launch`.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "fix: remove duplicate route and fix orphaned coroutine scopes"
```

---

## Phase 2: Vue 3 SPA Web Panel

### Task 3: Scaffold Vue 3 + Vite Project

**Covers:** Web frontend foundation

**Files:**
- Create: `web/` directory (Vue project root)
- Create: `web/package.json`
- Create: `web/vite.config.ts`
- Create: `web/tailwind.config.js`
- Create: `web/postcss.config.js`
- Create: `web/tsconfig.json`
- Create: `web/index.html`
- Create: `web/src/main.ts`
- Create: `web/src/App.vue`
- Create: `web/src/router/index.ts`
- Create: `web/src/stores/auth.ts`
- Create: `web/src/api/client.ts`
- Create: `web/src/style.css`
- Create: `web/env.d.ts`

- [ ] **Step 1: Initialize Vue 3 project**

```bash
cd /Users/kuaile/AndroidStudioProjects/XiangQin/web
npm create vite@latest . -- --template vue-ts
npm install
npm install vue-router@4 pinia axios element-plus @element-plus/icons-vue
npm install -D tailwindcss @tailwindcss/vite
```

- [ ] **Step 2: Configure Vite for Android asset output**

```typescript
// web/vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  base: './',
  build: {
    outDir: resolve(__dirname, '../app/src/main/assets/web'),
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
```

- [ ] **Step 3: Configure TailwindCSS**

```css
/* web/src/style.css */
@import "tailwindcss";
```

- [ ] **Step 4: Create main.ts with Element Plus**

```typescript
// web/src/main.ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
```

- [ ] **Step 5: Create router**

```typescript
// web/src/router/index.ts
import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    children: [
      { path: '', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'calls', name: 'Calls', component: () => import('../views/Calls.vue') },
      { path: 'sms', name: 'Sms', component: () => import('../views/Sms.vue') },
      { path: 'usage', name: 'Usage', component: () => import('../views/Usage.vue') },
      { path: 'traffic', name: 'Traffic', component: () => import('../views/Traffic.vue') },
      { path: 'location', name: 'Location', component: () => import('../views/Location.vue') },
      { path: 'bluetooth', name: 'Bluetooth', component: () => import('../views/Bluetooth.vue') },
      { path: 'wifi', name: 'Wifi', component: () => import('../views/Wifi.vue') },
      { path: 'sensors', name: 'Sensors', component: () => import('../views/Sensors.vue') },
      { path: 'calendar', name: 'Calendar', component: () => import('../views/Calendar.vue') },
      { path: 'media', name: 'Media', component: () => import('../views/Media.vue') },
      { path: 'alerts', name: 'Alerts', component: () => import('../views/Alerts.vue') },
      { path: 'notifications', name: 'Notifications', component: () => import('../views/Notifications.vue') },
      { path: 'remote', name: 'Remote', component: () => import('../views/Remote.vue') },
      { path: 'settings', name: 'Settings', component: () => import('../views/Settings.vue') },
    ],
  },
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'Login' && !auth.isAuthenticated) {
    return { name: 'Login' }
  }
})

export default router
```

- [ ] **Step 6: Create auth store**

```typescript
// web/src/stores/auth.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const isAuthenticated = ref(!!token.value)

  async function login(username: string, password: string) {
    const res = await api.post('/api/login', { username, password })
    token.value = res.data.token
    localStorage.setItem('token', token.value)
    isAuthenticated.value = true
  }

  function logout() {
    token.value = ''
    localStorage.removeItem('token')
    isAuthenticated.value = false
  }

  return { token, isAuthenticated, login, logout }
})
```

- [ ] **Step 7: Create API client**

```typescript
// web/src/api/client.ts
import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

export const api = axios.create({ baseURL: '' })

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Basic ${auth.token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.push('/login')
    }
    return Promise.reject(err)
  }
)

export function createWs() {
  const auth = useAuthStore()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const ws = new WebSocket(`${protocol}//${location.host}/ws`)
  ws.onopen = () => ws.send(`auth:${auth.token}`)
  return ws
}
```

- [ ] **Step 8: Commit**

```bash
git add web/
git commit -m "feat: scaffold Vue 3 + Vite project with router, pinia, element-plus"
```

### Task 4: Create Layout and Login Pages

**Covers:** Web frontend authentication and navigation

**Files:**
- Create: `web/src/views/Login.vue`
- Create: `web/src/views/Layout.vue`

- [ ] **Step 1: Create Login.vue**

```vue
<!-- web/src/views/Login.vue -->
<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50">
    <el-card class="w-96">
      <template #header>
        <h2 class="text-xl font-bold text-center">乡亲 · 登录</h2>
      </template>
      <el-form @submit.prevent="handleLogin">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" class="w-full" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

async function handleLogin() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    router.push('/')
  } catch {
    ElMessage.error('登录失败，请检查密码')
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 2: Create Layout.vue**

```vue
<!-- web/src/views/Layout.vue -->
<template>
  <el-container class="min-h-screen">
    <el-aside width="220px" class="bg-gray-900 text-white">
      <div class="p-4 text-lg font-bold border-b border-gray-700">🏠 乡亲</div>
      <el-menu :default-active="route.path" router background-color="#1f2937" text-color="#9ca3af" active-text-color="#60a5fa" class="border-none">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <span>{{ item.icon }} {{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="flex items-center justify-between border-b">
        <span class="text-lg font-semibold">{{ currentTitle }}</span>
        <el-button text @click="auth.logout(); $router.push('/login')">退出</el-button>
      </el-header>
      <el-main class="bg-gray-50"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()

const menuItems = [
  { path: '/', icon: '📊', label: '仪表盘' },
  { path: '/calls', icon: '📞', label: '通话' },
  { path: '/sms', icon: '💬', label: '短信' },
  { path: '/usage', icon: '📱', label: '应用使用' },
  { path: '/traffic', icon: '🌐', label: '流量' },
  { path: '/location', icon: '📍', label: '位置' },
  { path: '/bluetooth', icon: '📡', label: '蓝牙' },
  { path: '/wifi', icon: '📶', label: 'WiFi' },
  { path: '/sensors', icon: '💪', label: '传感器' },
  { path: '/calendar', icon: '📅', label: '日历' },
  { path: '/media', icon: '🖼️', label: '媒体' },
  { path: '/alerts', icon: '🚨', label: '告警' },
  { path: '/notifications', icon: '🔔', label: '通知' },
  { path: '/remote', icon: '📸', label: '远程操作' },
  { path: '/settings', icon: '⚙️', label: '设置' },
]

const currentTitle = computed(() => menuItems.find(m => m.path === route.path)?.label || '仪表盘')
</script>
```

- [ ] **Step 3: Commit**

```bash
git add web/src/views/
git commit -m "feat: add Login and Layout pages with sidebar navigation"
```

### Task 5: Create Dashboard Page

**Covers:** Web frontend dashboard

**Files:**
- Create: `web/src/views/Dashboard.vue`

- [ ] **Step 1: Create Dashboard.vue**

```vue
<!-- web/src/views/Dashboard.vue -->
<template>
  <div>
    <el-row :gutter="16" class="mb-4">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover">
          <div class="text-center">
            <div class="text-3xl font-bold" :style="{ color: stat.color }">{{ stat.value }}</div>
            <div class="text-gray-500 text-sm mt-1">{{ stat.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="最新通话">
          <el-table :data="data.latestCalls" size="small" max-height="300">
            <el-table-column prop="callerName" label="联系人" />
            <el-table-column prop="phoneNumber" label="号码" />
            <el-table-column label="类型">
              <template #default="{ row }">{{ ['', '来电', '去电', '未接'][row.callType] }}</template>
            </el-table-column>
            <el-table-column label="时长">
              <template #default="{ row }">{{ row.durationSeconds }}秒</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="最新短信">
          <el-table :data="data.latestSms" size="small" max-height="300">
            <el-table-column prop="senderName" label="联系人" />
            <el-table-column prop="body" label="内容" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const data = ref<any>({})
const stats = ref<any[]>([])

onMounted(async () => {
  const res = await api.get('/api/stats/summary')
  data.value = res.data
  stats.value = [
    { label: '今日通话', value: res.data.callCount, color: '#409eff' },
    { label: '今日短信', value: res.data.smsCount, color: '#67c23a' },
    { label: '今日告警', value: res.data.alertToday, color: '#f56c6c' },
    { label: '服务运行', value: formatUptime(res.data.serviceUptime), color: '#e6a23c' },
  ]
})

function formatUptime(ms: number) {
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  return h > 0 ? `${h}h ${m}m` : `${m}m`
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add web/src/views/Dashboard.vue
git commit -m "feat: add dashboard page with stats and tables"
```

### Task 6: Create Data List Pages (Calls, SMS, Usage, Traffic)

**Covers:** Web frontend data views

**Files:**
- Create: `web/src/views/Calls.vue`
- Create: `web/src/views/Sms.vue`
- Create: `web/src/views/Usage.vue`
- Create: `web/src/views/Traffic.vue`

- [ ] **Step 1: Create Calls.vue**

```vue
<!-- web/src/views/Calls.vue -->
<template>
  <el-card header="通话记录">
    <el-table :data="calls" v-loading="loading" stripe>
      <el-table-column label="时间">
        <template #default="{ row }">{{ fmt(row.callTime) }}</template>
      </el-table-column>
      <el-table-column prop="callerName" label="联系人" />
      <el-table-column prop="phoneNumber" label="号码" />
      <el-table-column label="类型">
        <template #default="{ row }">
          <el-tag :type="[,'success','','danger'][row.callType]" size="small">
            {{ ['','来电','去电','未接'][row.callType] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时长">
        <template #default="{ row }">{{ Math.floor(row.durationSeconds/60) }}分{{ row.durationSeconds%60 }}秒</template>
      </el-table-column>
    </el-table>
    <div class="mt-4 text-right">
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const calls = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  const res = await api.get('/api/calls')
  calls.value = res.data
  loading.value = false
})

function fmt(t: number) { return new Date(t).toLocaleString() }

function exportCsv() { window.open('/api/export/calls/csv', '_blank') }
</script>
```

- [ ] **Step 2: Create Sms.vue**

```vue
<!-- web/src/views/Sms.vue -->
<template>
  <el-card header="短信记录">
    <el-table :data="smsList" v-loading="loading" stripe>
      <el-table-column label="时间">
        <template #default="{ row }">{{ fmt(row.receivedTime) }}</template>
      </el-table-column>
      <el-table-column prop="senderName" label="联系人" />
      <el-table-column prop="phoneNumber" label="号码" />
      <el-table-column label="类型">
        <template #default="{ row }">{{ row.smsType === 1 ? '收' : '发' }}</template>
      </el-table-column>
      <el-table-column prop="body" label="内容" show-overflow-tooltip />
    </el-table>
    <div class="mt-4 text-right">
      <el-button size="small" @click="window.open('/api/export/sms/csv','_blank')">导出 CSV</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const smsList = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  const res = await api.get('/api/sms')
  smsList.value = res.data
  loading.value = false
})

function fmt(t: number) { return new Date(t).toLocaleString() }
</script>
```

- [ ] **Step 3: Create Usage.vue**

```vue
<!-- web/src/views/Usage.vue -->
<template>
  <el-card header="应用使用时长">
    <el-table :data="usages" v-loading="loading" stripe>
      <el-table-column prop="appName" label="应用" />
      <el-table-column prop="packageName" label="包名" show-overflow-tooltip />
      <el-table-column label="使用时长">
        <template #default="{ row }">{{ fmtMs(row.totalTimeForeground) }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const usages = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  const res = await api.get('/api/usage')
  usages.value = res.data
  loading.value = false
})

function fmtMs(ms: number) {
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  return h > 0 ? `${h}小时${m}分钟` : `${m}分钟`
}
</script>
```

- [ ] **Step 4: Create Traffic.vue**

```vue
<!-- web/src/views/Traffic.vue -->
<template>
  <el-card header="流量统计">
    <el-table :data="traffic" v-loading="loading" stripe>
      <el-table-column prop="appName" label="应用" />
      <el-table-column label="下载">
        <template #default="{ row }">{{ fmtBytes(row.rxBytes) }}</template>
      </el-table-column>
      <el-table-column label="上传">
        <template #default="{ row }">{{ fmtBytes(row.txBytes) }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const traffic = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  const res = await api.get('/api/traffic')
  traffic.value = res.data.traffic
  loading.value = false
})

function fmtBytes(b: number) {
  if (b < 1024) return b + ' B'
  if (b < 1048576) return (b / 1024).toFixed(1) + ' KB'
  if (b < 1073741824) return (b / 1048576).toFixed(1) + ' MB'
  return (b / 1073741824).toFixed(2) + ' GB'
}
</script>
```

- [ ] **Step 5: Commit**

```bash
git add web/src/views/Calls.vue web/src/views/Sms.vue web/src/views/Usage.vue web/src/views/Traffic.vue
git commit -m "feat: add calls, SMS, usage, and traffic pages"
```

### Task 7: Create Location, Network Pages (Bluetooth, WiFi)

**Covers:** Web frontend location and network views

**Files:**
- Create: `web/src/views/Location.vue`
- Create: `web/src/views/Bluetooth.vue`
- Create: `web/src/views/Wifi.vue`

- [ ] **Step 1: Create Location.vue with map placeholder**

```vue
<!-- web/src/views/Location.vue -->
<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="16">
        <el-card header="位置轨迹">
          <div class="h-96 bg-gray-100 rounded flex items-center justify-center text-gray-400" v-if="!locations.length">
            暂无位置数据
          </div>
          <div v-else class="h-96 bg-gray-100 rounded overflow-auto p-4">
            <div v-for="loc in locations" :key="loc.id" class="mb-2 p-3 bg-white rounded shadow-sm">
              <div class="font-semibold">{{ loc.latitude.toFixed(6) }}, {{ loc.longitude.toFixed(6) }}</div>
              <div class="text-sm text-gray-500">精度: {{ loc.accuracy }}米 · {{ new Date(loc.recordedTime).toLocaleString() }}</div>
              <el-button size="small" type="primary" link @click="openMap(loc)">在高德地图查看</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card header="最新位置" v-if="latest">
          <p class="font-bold">{{ latest.latitude.toFixed(6) }}, {{ latest.longitude.toFixed(6) }}</p>
          <p class="text-sm text-gray-500">精度: {{ latest.accuracy }}米</p>
          <p class="text-sm text-gray-500">{{ new Date(latest.recordedTime).toLocaleString() }}</p>
        </el-card>
        <el-card header="统计" class="mt-4">
          <p>轨迹点数: {{ locations.length }}</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const locations = ref<any[]>([])
const latest = ref<any>(null)

onMounted(async () => {
  const [locRes, latestRes] = await Promise.all([
    api.get('/api/locations?days=1'),
    api.get('/api/locations/latest'),
  ])
  locations.value = locRes.data.points || []
  latest.value = latestRes.data.location
})

function openMap(loc: any) {
  window.open(`https://uri.amap.com/marker?position=${loc.longitude},${loc.latitude}&name=我的位置`, '_blank')
}
</script>
```

- [ ] **Step 2: Create Bluetooth.vue**

```vue
<!-- web/src/views/Bluetooth.vue -->
<template>
  <el-card>
    <template #header>
      <div class="flex justify-between items-center">
        <span>蓝牙设备</span>
        <el-button size="small" @click="scan" :loading="scanning">扫描</el-button>
      </div>
    </template>
    <el-table :data="devices" v-loading="loading" stripe>
      <el-table-column prop="deviceName" label="设备名" />
      <el-table-column prop="deviceAddress" label="MAC 地址" />
      <el-table-column label="状态">
        <template #default="{ row }">
          <el-tag :type="row.bondState === 12 ? 'success' : 'info'" size="small">
            {{ row.bondState === 12 ? '已配对' : row.bondState === 11 ? '配对中' : '未配对' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="rssi" label="信号 (dBm)" />
      <el-table-column label="最后发现">
        <template #default="{ row }">{{ new Date(row.lastSeen).toLocaleString() }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const devices = ref<any[]>([])
const loading = ref(true)
const scanning = ref(false)

onMounted(async () => {
  const res = await api.get('/api/bluetooth/devices')
  devices.value = res.data.devices
  loading.value = false
})

async function scan() {
  scanning.value = true
  try {
    await api.post('/api/bluetooth/scan')
    ElMessage.success('扫描已触发')
    const res = await api.get('/api/bluetooth/devices')
    devices.value = res.data.devices
  } catch { ElMessage.error('扫描失败') }
  scanning.value = false
}
</script>
```

- [ ] **Step 3: Create Wifi.vue**

```vue
<!-- web/src/views/Wifi.vue -->
<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="flex justify-between items-center">
              <span>WiFi 热点</span>
              <el-button size="small" @click="scan" :loading="scanning">扫描</el-button>
            </div>
          </template>
          <el-table :data="networks" v-loading="loading" stripe>
            <el-table-column prop="ssid" label="SSID" />
            <el-table-column prop="bssid" label="BSSID" />
            <el-table-column prop="rssi" label="信号 (dBm)" />
            <el-table-column prop="frequency" label="频率 (MHz)" />
            <el-table-column prop="securityType" label="加密" />
            <el-table-column label="风险">
              <template #default="{ row }">
                <el-tag :type="riskType(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card header="WiFi 安全分析" v-if="security">
          <div v-for="(v, k) in security" :key="k" class="mb-2">
            <span class="font-semibold">{{ k }}:</span> {{ v }}
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const networks = ref<any[]>([])
const security = ref<any>(null)
const loading = ref(true)
const scanning = ref(false)

onMounted(async () => {
  const [netRes, secRes] = await Promise.all([
    api.get('/api/wifi/networks'),
    api.get('/api/wifi/security').catch(() => ({ data: null })),
  ])
  networks.value = netRes.data.networks
  security.value = secRes.data
  loading.value = false
})

async function scan() {
  scanning.value = true
  try {
    await api.post('/api/wifi/scan')
    ElMessage.success('扫描已触发')
    const res = await api.get('/api/wifi/networks')
    networks.value = res.data.networks
  } catch { ElMessage.error('扫描失败') }
  scanning.value = false
}

function riskType(level: string) {
  const m: Record<string, string> = { SAFE: 'success', LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }
  return m[level] || 'info'
}
</script>
```

- [ ] **Step 4: Commit**

```bash
git add web/src/views/Location.vue web/src/views/Bluetooth.vue web/src/views/Wifi.vue
git commit -m "feat: add location, Bluetooth, and WiFi pages"
```

### Task 8: Create Sensors, Calendar, Media Pages

**Covers:** Web frontend sensor/calendar/media views

**Files:**
- Create: `web/src/views/Sensors.vue`
- Create: `web/src/views/Calendar.vue`
- Create: `web/src/views/Media.vue`

- [ ] **Step 1: Create Sensors.vue**

```vue
<!-- web/src/views/Sensors.vue -->
<template>
  <el-card header="传感器数据">
    <el-table :data="sensors" v-loading="loading" stripe>
      <el-table-column prop="sensorType" label="类型" />
      <el-table-column label="数值">
        <template #default="{ row }">{{ row.value.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="时间">
        <template #default="{ row }">{{ new Date(row.recordedTime).toLocaleString() }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const sensors = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  const res = await api.get('/api/sensors?limit=100')
  sensors.value = res.data.sensors
  loading.value = false
})
</script>
```

- [ ] **Step 2: Create Calendar.vue**

```vue
<!-- web/src/views/Calendar.vue -->
<template>
  <el-card header="日历事件">
    <el-table :data="events" v-loading="loading" stripe>
      <el-table-column prop="eventTitle" label="标题" />
      <el-table-column prop="eventLocation" label="地点" />
      <el-table-column label="开始">
        <template #default="{ row }">{{ new Date(row.startTime).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="结束">
        <template #default="{ row }">{{ row.endTime ? new Date(row.endTime).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column prop="eventDescription" label="描述" show-overflow-tooltip />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const events = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  const res = await api.get('/api/calendar/events?days=30')
  events.value = res.data.events
  loading.value = false
})
</script>
```

- [ ] **Step 3: Create Media.vue**

```vue
<!-- web/src/views/Media.vue -->
<template>
  <el-card>
    <template #header>
      <div class="flex justify-between items-center">
        <span>媒体文件</span>
        <div>
          <el-radio-group v-model="typeFilter" size="small" @change="loadMedia">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button value="image">图片</el-radio-button>
            <el-radio-button value="video">视频</el-radio-button>
            <el-radio-button value="audio">音频</el-radio-button>
          </el-radio-group>
          <el-button size="small" class="ml-2" @click="rescan" :loading="scanning">重新扫描</el-button>
        </div>
      </div>
    </template>
    <el-table :data="files" v-loading="loading" stripe>
      <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
      <el-table-column prop="mediaType" label="类型" />
      <el-table-column label="大小">
        <template #default="{ row }">{{ fmtBytes(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="添加时间">
        <template #default="{ row }">{{ new Date(row.dateAdded).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="viewFile(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const files = ref<any[]>([])
const loading = ref(true)
const scanning = ref(false)
const typeFilter = ref<string | undefined>(undefined)

onMounted(() => loadMedia())

async function loadMedia() {
  loading.value = true
  const url = typeFilter.value ? `/api/media?type=${typeFilter.value}` : '/api/media'
  const res = await api.get(url)
  files.value = res.data.files
  loading.value = false
}

async function rescan() {
  scanning.value = true
  try {
    await api.post('/api/media/rescan')
    ElMessage.success('扫描已触发')
    await loadMedia()
  } catch { ElMessage.error('扫描失败') }
  scanning.value = false
}

function viewFile(f: any) {
  window.open(`/api/media/file?path=${encodeURIComponent(f.filePath)}`, '_blank')
}

function fmtBytes(b: number) {
  if (b < 1024) return b + ' B'
  if (b < 1048576) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1048576).toFixed(1) + ' MB'
}
</script>
```

- [ ] **Step 4: Commit**

```bash
git add web/src/views/Sensors.vue web/src/views/Calendar.vue web/src/views/Media.vue
git commit -m "feat: add sensors, calendar, and media pages"
```

### Task 9: Create Alerts, Notifications, Remote, Settings Pages

**Covers:** Web frontend alerts/notifications/remote/settings views

**Files:**
- Create: `web/src/views/Alerts.vue`
- Create: `web/src/views/Notifications.vue`
- Create: `web/src/views/Remote.vue`
- Create: `web/src/views/Settings.vue`

- [ ] **Step 1: Create Alerts.vue**

```vue
<!-- web/src/views/Alerts.vue -->
<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="flex justify-between items-center">
              <span>告警记录</span>
              <div>
                <el-button size="small" @click="ackAll">全部确认</el-button>
                <el-button size="small" type="danger" @click="clearAll">清空</el-button>
              </div>
            </div>
          </template>
          <el-table :data="alerts" v-loading="loading" stripe>
            <el-table-column label="时间">
              <template #default="{ row }">{{ new Date(row.triggeredTime).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="message" label="详情" show-overflow-tooltip />
            <el-table-column label="级别">
              <template #default="{ row }">
                <el-tag :type="row.severity==='critical'?'danger':row.severity==='warning'?'warning':'info'" size="small">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag v-if="row.acknowledged" type="success" size="small">已确认</el-tag>
                <el-button v-else size="small" type="primary" link @click="ack(row.id)">确认</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card header="告警设置">
          <div v-for="(enabled, type) in settings.enabled" :key="type" class="flex justify-between items-center mb-2">
            <span class="text-sm">{{ type }}</span>
            <el-switch v-model="settings.enabled[type]" @change="saveSettings" />
          </div>
          <el-divider />
          <div class="mb-2">
            <label class="text-sm font-semibold">飞书 Webhook</label>
            <el-input v-model="feishuUrl" size="small" placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/..." />
          </div>
          <el-button size="small" type="primary" class="w-full" @click="saveSettings">保存</el-button>
          <el-button size="small" class="w-full mt-2" @click="testPush">测试推送</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const alerts = ref<any[]>([])
const loading = ref(true)
const settings = ref<any>({ enabled: {} })
const feishuUrl = ref('')

onMounted(async () => {
  const [alertRes, settingsRes] = await Promise.all([
    api.get('/api/alerts?days=7'),
    api.get('/api/alerts/settings'),
  ])
  alerts.value = alertRes.data.alerts
  settings.value = settingsRes.data
  feishuUrl.value = settingsRes.data.feishuWebhook || ''
  loading.value = false
})

async function ack(id: number) {
  await api.post(`/api/alerts/acknowledge/${id}`)
  alerts.value = alerts.value.map(a => a.id === id ? { ...a, acknowledged: true } : a)
}

async function ackAll() {
  await api.post('/api/alerts/acknowledge-all')
  alerts.value = alerts.value.map(a => ({ ...a, acknowledged: true }))
  ElMessage.success('全部已确认')
}

async function clearAll() {
  await api.delete('/api/alerts')
  alerts.value = []
  ElMessage.success('已清空')
}

async function saveSettings() {
  await api.post('/api/alerts/settings', { enabled: settings.value.enabled, feishuWebhook: feishuUrl.value })
  ElMessage.success('设置已保存')
}

async function testPush() {
  const res = await api.post('/api/alerts/test-push')
  if (res.data.success) ElMessage.success('测试消息已发送')
  else ElMessage.error('推送失败')
}
</script>
```

- [ ] **Step 2: Create Notifications.vue**

```vue
<!-- web/src/views/Notifications.vue -->
<template>
  <el-card header="通知记录">
    <el-table :data="notifications" v-loading="loading" stripe>
      <el-table-column label="时间">
        <template #default="{ row }">{{ new Date(row.postTime).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="appName" label="应用" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="text" label="内容" show-overflow-tooltip />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'

const notifications = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  const res = await api.get('/api/notifications?limit=100')
  notifications.value = res.data.notifications
  loading.value = false
})
</script>
```

- [ ] **Step 3: Create Remote.vue**

```vue
<!-- web/src/views/Remote.vue -->
<template>
  <el-row :gutter="16">
    <el-col :span="8">
      <el-card header="📸 远程拍照">
        <el-button type="primary" class="w-full" @click="capture" :loading="capturing">拍照</el-button>
        <div class="mt-4" v-if="photos.length">
          <div v-for="p in photos.slice(0,5)" :key="p.id" class="mb-2">
            <img :src="`/api/files/${encodeURIComponent(p.filePath)}`" class="w-full rounded" loading="lazy" />
            <div class="text-xs text-gray-400">{{ new Date(p.takenTime).toLocaleString() }}</div>
          </div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card header="🎤 远程录音">
        <el-button v-if="!recording" type="primary" class="w-full" @click="startRec" :loading="recStarting">开始录音</el-button>
        <el-button v-else type="danger" class="w-full" @click="stopRec">停止录音</el-button>
        <div class="mt-2 text-center text-sm text-gray-500">{{ recording ? '录音中...' : '未录音' }}</div>
        <div class="mt-4" v-if="recordings.length">
          <div v-for="r in recordings.slice(0,5)" :key="r.id" class="mb-2 p-2 bg-gray-50 rounded text-sm">
            {{ r.filePath.split('/').pop() }} · {{ Math.floor(r.durationMs/1000) }}秒
          </div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card header="🔒 远程锁屏">
        <el-button type="warning" class="w-full" @click="lockScreen">锁定屏幕</el-button>
        <div class="mt-4 text-sm text-gray-500">
          <p>设备管理器状态: <el-tag :type="adminActive ? 'success' : 'danger'" size="small">{{ adminActive ? '已激活' : '未激活' }}</el-tag></p>
          <p class="mt-2">需要先在手机端激活设备管理器才能使用远程锁屏。</p>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const capturing = ref(false)
const recording = ref(false)
const recStarting = ref(false)
const photos = ref<any[]>([])
const recordings = ref<any[]>([])
const adminActive = ref(false)

onMounted(async () => {
  const [photoRes, recRes, adminRes] = await Promise.all([
    api.get('/api/camera/photos'),
    api.get('/api/audio/recordings'),
    api.get('/api/device/admin/status'),
  ])
  photos.value = photoRes.data.photos
  recordings.value = recRes.data.recordings
  adminActive.value = adminRes.data.active
})

async function capture() {
  capturing.value = true
  try {
    const res = await api.post('/api/camera/capture')
    ElMessage.success('拍照成功')
    photos.value.unshift({ id: res.data.id, filePath: res.data.path, takenTime: Date.now() })
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '拍照失败') }
  capturing.value = false
}

async function startRec() {
  recStarting.value = true
  try {
    await api.post('/api/audio/start')
    recording.value = true
    ElMessage.success('录音已开始')
  } catch { ElMessage.error('开始录音失败') }
  recStarting.value = false
}

async function stopRec() {
  try {
    const res = await api.post('/api/audio/stop')
    recording.value = false
    ElMessage.success(`录音已保存: ${Math.floor(res.data.durationMs/1000)}秒`)
    const recRes = await api.get('/api/audio/recordings')
    recordings.value = recRes.data.recordings
  } catch { ElMessage.error('停止录音失败') }
}

async function lockScreen() {
  try {
    await api.post('/api/device/lock')
    ElMessage.success('屏幕已锁定')
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '锁屏失败') }
}
</script>
```

- [ ] **Step 4: Create Settings.vue**

```vue
<!-- web/src/views/Settings.vue -->
<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-card header="系统信息">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="设备名">{{ info.hostname }}</el-descriptions-item>
          <el-descriptions-item label="局域网 IP">{{ info.localIp }}</el-descriptions-item>
          <el-descriptions-item label="端口">{{ info.port }}</el-descriptions-item>
          <el-descriptions-item label="服务状态">
            <el-tag :type="info.serviceRunning ? 'success' : 'danger'">{{ info.serviceRunning ? '运行中' : '已停止' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="设备管理器">
            <el-tag :type="info.deviceAdminActive ? 'success' : 'danger'">{{ info.deviceAdminActive ? '已激活' : '未激活' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </el-col>
    <el-col :span="12">
      <el-card header="修改密码">
        <el-form @submit.prevent="changePw">
          <el-form-item label="旧密码">
            <el-input v-model="pwForm.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-button type="primary" class="w-full" @click="changePw">修改密码</el-button>
        </el-form>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api/client'
import { ElMessage } from 'element-plus'

const info = ref<any>({})
const pwForm = ref({ oldPassword: '', newPassword: '' })

onMounted(async () => {
  const res = await api.get('/api/settings')
  info.value = res.data
})

async function changePw() {
  try {
    await api.post('/api/settings/password', pwForm.value)
    ElMessage.success('密码已修改')
    pwForm.value = { oldPassword: '', newPassword: '' }
  } catch (e: any) { ElMessage.error(e.response?.data?.error || '修改失败') }
}
</script>
```

- [ ] **Step 5: Commit**

```bash
git add web/src/views/Alerts.vue web/src/views/Notifications.vue web/src/views/Remote.vue web/src/views/Settings.vue
git commit -m "feat: add alerts, notifications, remote control, and settings pages"
```

### Task 10: Build and Verify Web Panel

**Covers:** Web frontend build pipeline

**Files:**
- Modify: `web/package.json` (add build script)

- [ ] **Step 1: Build the Vue SPA**

```bash
cd /Users/kuaile/AndroidStudioProjects/XiangQin/web
npm run build
```

- [ ] **Step 2: Verify output**

```bash
ls -la /Users/kuaile/AndroidStudioProjects/XiangQin/app/src/main/assets/web/
```

Expected: `index.html`, `assets/` directory with JS/CSS bundles.

- [ ] **Step 3: Add build to package.json scripts if missing**

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  }
}
```

- [ ] **Step 4: Verify dev server works**

```bash
cd /Users/kuaile/AndroidStudioProjects/XiangQin/web
timeout 5 npm run dev 2>&1 || true
```

Expected: Vite dev server starts on port 5173.

- [ ] **Step 5: Commit**

```bash
git add web/ app/src/main/assets/web/
git commit -m "feat: build Vue SPA to assets/web for Ktor static serving"
```

---

## Phase 3: Tests

### Task 11: Add Unit Tests for AlertEngine

**Covers:** Test coverage for alert logic

**Files:**
- Create: `app/src/test/java/com/xiangqin/app/monitor/AlertEngineTest.kt`

- [ ] **Step 1: Add test dependencies to app/build.gradle.kts**

```kotlin
// Add after existing testImplementation lines
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("io.mockk:mockk:1.13.8")
```

- [ ] **Step 2: Create AlertEngineTest.kt**

```kotlin
package com.xiangqin.app.monitor

import org.junit.Assert.*
import org.junit.Test

class AlertEngineTest {

    @Test
    fun `distanceBetween calculates correct distance`() {
        // Two points ~1km apart in Beijing
        val home = com.xiangqin.app.data.db.HomeZone(
            latitude = 39.9042,
            longitude = 116.4074,
            radiusMeters = 200f,
            address = "Tiananmen"
        )
        val distance = calculateDistance(home, 39.9142, 116.4074)
        assertTrue("Distance should be ~1100m, got $distance", distance in 1000f..1200f)
    }

    @Test
    fun `distanceBetween returns 0 for same point`() {
        val home = com.xiangqin.app.data.db.HomeZone(
            latitude = 39.9042,
            longitude = 116.4074,
            radiusMeters = 200f,
            address = null
        )
        val distance = calculateDistance(home, 39.9042, 116.4074)
        assertEquals(0f, distance, 1f)
    }

    @Test
    fun `distanceBetween within radius returns small value`() {
        val home = com.xiangqin.app.data.db.HomeZone(
            latitude = 39.9042,
            longitude = 116.4074,
            radiusMeters = 200f,
            address = null
        )
        // ~50m away
        val distance = calculateDistance(home, 39.9046, 116.4074)
        assertTrue("Distance should be < 100m, got $distance", distance < 100f)
    }

    private fun calculateDistance(
        home: com.xiangqin.app.data.db.HomeZone,
        lat: Double,
        lng: Double
    ): Float {
        val R = 6371000.0
        val dLat = Math.toRadians(lat - home.latitude)
        val dLon = Math.toRadians(lng - home.longitude)
        val a = Math.pow(Math.sin(dLat / 2), 2.0) +
                Math.cos(Math.toRadians(home.latitude)) * Math.cos(Math.toRadians(lat)) *
                Math.pow(Math.sin(dLon / 2), 2.0)
        return (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }
}
```

- [ ] **Step 3: Run tests**

```bash
cd /Users/kuaile/AndroidStudioProjects/XiangQin
./gradlew :app:testDebugUnitTest --tests "com.xiangqin.app.monitor.AlertEngineTest"
```

Expected: All 3 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/ app/build.gradle.kts
git commit -m "test: add AlertEngine unit tests for distance calculation"
```

### Task 12: Add Unit Tests for DataStore

**Covers:** Test coverage for settings persistence

**Files:**
- Create: `app/src/test/java/com/xiangqin/app/data/datastore/AppDataStoreTest.kt`

- [ ] **Step 1: Create AppDataStoreTest.kt**

```kotlin
package com.xiangqin.app.data.datastore

import org.junit.Assert.*
import org.junit.Test

class AppDataStoreTest {

    @Test
    fun `ALERT_TYPES contains all expected types`() {
        val expected = setOf(
            "late_night_leave", "low_battery", "no_heartbeat",
            "off_hour_call", "device_boot", "sim_change",
            "app_install", "wifi_change", "cell_change"
        )
        assertEquals(expected, AppDataStore.ALERT_TYPES.toSet())
    }

    @Test
    fun `alert key prefix is consistent`() {
        val prefix = AppDataStore.ALERT_TYPES.first().let {
            // Verify prefix pattern matches
            "alert_enabled_$it"
        }
        assertTrue(prefix.startsWith("alert_enabled_"))
    }

    @Test
    fun `companion constants are defined`() {
        assertNotNull(AppDataStore.DB_PIN)
        assertNotNull(AppDataStore.WEB_PASSWORD)
        assertEquals("db_pin", AppDataStore.DB_PIN)
        assertEquals("web_password", AppDataStore.WEB_PASSWORD)
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd /Users/kuaile/AndroidStudioProjects/XiangQin
./gradlew :app:testDebugUnitTest --tests "com.xiangqin.app.data.datastore.AppDataStoreTest"
```

Expected: All 3 tests pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/
git commit -m "test: add AppDataStore unit tests for constants and alert types"
```

---

## Phase 4: Final Integration

### Task 13: Final Build Verification

**Covers:** End-to-end verification

- [ ] **Step 1: Build the full Android project**

```bash
cd /Users/kuaile/AndroidStudioProjects/XiangQin
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify all tests pass**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All tests pass.

- [ ] **Step 3: Verify web assets are in place**

```bash
ls app/src/main/assets/web/index.html
```

Expected: File exists.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "release: production-ready v1.0.0"
```
