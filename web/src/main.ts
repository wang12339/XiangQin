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

document.addEventListener('keydown', (e) => {
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return
  const shortcuts: Record<string, string> = {
    '1': '/',
    '2': '/calls',
    '3': '/location',
    '4': '/remote',
    '5': '/more',
    'r': '/remote',
    'a': '/alerts',
    's': '/settings',
  }
  if (shortcuts[e.key]) {
    e.preventDefault()
    router.push(shortcuts[e.key])
  }
})

app.mount('#app')
