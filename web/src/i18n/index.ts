import { ref } from 'vue'

type Lang = 'zh' | 'en'

const messages: Record<Lang, Record<string, string>> = {
  zh: {
    'app.name': '乡亲',
    'app.slogan': '家庭守护 · 安心相伴',
    'nav.dashboard': '总览',
    'nav.calls': '通讯',
    'nav.location': '位置',
    'nav.remote': '远程',
    'nav.more': '更多',
    'status.online': '在线',
    'status.offline': '离线',
    'status.realtime': '实时',
    'status.stopped': '监控已停止',
    'status.running': '监控运行中',
    'action.start': '启动监控',
    'action.stop': '停止监控',
    'action.login': '登录',
    'action.logout': '退出登录',
    'action.save': '保存',
    'action.cancel': '取消',
    'action.delete': '删除',
    'action.confirm': '确认',
    'action.refresh': '刷新',
    'action.export': '导出',
    'action.search': '搜索',
    'action.back': '返回',
    'alert.title': '告警',
    'alert.settings': '告警设置',
    'alert.noData': '暂无告警',
    'call.incoming': '来电',
    'call.outgoing': '去电',
    'call.missed': '未接',
    'device.battery': '电池',
    'device.charging': '充电中',
    'device.notCharging': '未充电',
    'settings.title': '设置',
    'settings.password': '修改密码',
    'settings.darkMode': '暗色模式',
    'about.title': '关于',
    'feedback.title': '反馈与支持',
    'export.title': '数据导出',
    'stats.title': '数据统计',
  },
  en: {
    'app.name': 'XiangQin',
    'app.slogan': 'Family Safety · Peace of Mind',
    'nav.dashboard': 'Dashboard',
    'nav.calls': 'Calls',
    'nav.location': 'Location',
    'nav.remote': 'Remote',
    'nav.more': 'More',
    'status.online': 'Online',
    'status.offline': 'Offline',
    'status.realtime': 'Live',
    'status.stopped': 'Monitoring Stopped',
    'status.running': 'Monitoring Active',
    'action.start': 'Start Monitoring',
    'action.stop': 'Stop Monitoring',
    'action.login': 'Login',
    'action.logout': 'Logout',
    'action.save': 'Save',
    'action.cancel': 'Cancel',
    'action.delete': 'Delete',
    'action.confirm': 'Confirm',
    'action.refresh': 'Refresh',
    'action.export': 'Export',
    'action.search': 'Search',
    'action.back': 'Back',
    'alert.title': 'Alerts',
    'alert.settings': 'Alert Settings',
    'alert.noData': 'No alerts',
    'call.incoming': 'Incoming',
    'call.outgoing': 'Outgoing',
    'call.missed': 'Missed',
    'device.battery': 'Battery',
    'device.charging': 'Charging',
    'device.notCharging': 'Not Charging',
    'settings.title': 'Settings',
    'settings.password': 'Change Password',
    'settings.darkMode': 'Dark Mode',
    'about.title': 'About',
    'feedback.title': 'Feedback & Support',
    'export.title': 'Data Export',
    'stats.title': 'Statistics',
  }
}

const currentLang = ref<Lang>((localStorage.getItem('lang') as Lang) || 'zh')

export function useI18n() {
  function t(key: string): string {
    return messages[currentLang.value][key] || key
  }

  function setLang(lang: Lang) {
    currentLang.value = lang
    localStorage.setItem('lang', lang)
    document.documentElement.lang = lang
  }

  function toggleLang() {
    setLang(currentLang.value === 'zh' ? 'en' : 'zh')
  }

  return { t, setLang, toggleLang, currentLang }
}
