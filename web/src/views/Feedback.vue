<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <div class="flex items-center gap-2">
        <button @click="$router.back()" class="text-blue-500 text-sm">← 返回</button>
        <h1 class="text-lg font-bold">反馈与支持</h1>
      </div>
    </div>
    <div class="p-4 space-y-4">
      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📝 意见反馈</div>
        <p class="text-sm text-gray-500 mb-3">您的反馈将帮助我们改进产品</p>
        <select v-model="form.type" class="w-full border rounded-lg px-3 py-2 text-sm mb-2">
          <option value="bug">问题报告</option>
          <option value="feature">功能建议</option>
          <option value="other">其他</option>
        </select>
        <textarea v-model="form.content" placeholder="请详细描述您的问题或建议..." rows="4"
          class="w-full border rounded-lg px-3 py-2 text-sm mb-2 resize-none"></textarea>
        <input v-model="form.contact" type="text" placeholder="联系方式（可选）" class="w-full border rounded-lg px-3 py-2 text-sm mb-3" />
        <button @click="submitFeedback" :disabled="!form.content || submitting"
          class="w-full py-2.5 bg-blue-500 text-white rounded-xl font-medium active:bg-blue-600 disabled:opacity-50">
          {{ submitting ? '提交中...' : '提交反馈' }}
        </button>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">📞 联系方式</div>
        <div class="space-y-2 text-sm text-gray-600">
          <div class="flex items-center gap-2">
            <span>📧</span>
            <span>support@xiangqin.app</span>
          </div>
          <div class="flex items-center gap-2">
            <span>🌐</span>
            <span>github.com/xiangqin/issues</span>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">❓ 常见问题</div>
        <div class="space-y-3">
          <div v-for="faq in faqs" :key="faq.q" class="border-b border-gray-100 pb-3 last:border-0 last:pb-0">
            <div class="font-medium text-sm text-gray-800">{{ faq.q }}</div>
            <div class="text-xs text-gray-500 mt-1">{{ faq.a }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'

const submitting = ref(false)
const form = reactive({ type: 'bug', content: '', contact: '' })

const faqs = [
  { q: '如何启动监控？', a: '打开 App，点击"启动监控"按钮即可。确保已授予所有必要权限。' },
  { q: '如何访问 Web 管理面板？', a: '启动监控后，在同一局域网的设备浏览器中访问 http://<手机IP>:8080' },
  { q: '如何修改管理面板密码？', a: '在 App 主界面点击"显示"查看密码，或在 Web 面板设置中修改。' },
  { q: '监控服务被系统杀掉怎么办？', a: '请在"权限与保活设置"中开启所有保活选项，包括电池优化白名单和自启动权限。' },
]

async function submitFeedback() {
  if (!form.content.trim()) return
  submitting.value = true
  try {
    const subject = `[乡亲] ${form.type === 'bug' ? '问题报告' : form.type === 'feature' ? '功能建议' : '反馈'}`
    const body = `${form.content}\n\n联系方式: ${form.contact || '未提供'}`
    const mailto = `mailto:support@xiangqin.app?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`
    window.open(mailto, '_blank')
    ElMessage.success('感谢您的反馈！')
    form.content = ''; form.contact = ''
  } catch { ElMessage.error('提交失败') }
  submitting.value = false
}
</script>
