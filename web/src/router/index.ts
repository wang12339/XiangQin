import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  { path: '/about', name: 'About', component: () => import('../views/About.vue'), meta: { title: '关于' } },
  { path: '/legal', name: 'Legal', component: () => import('../views/Legal.vue'), meta: { title: '法律' } },
  { path: '/', component: () => import('../views/Layout.vue'), children: [
    { path: '', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '总览' } },
    { path: 'calls', name: 'Calls', component: () => import('../views/Calls.vue'), meta: { title: '通讯' } },
    { path: 'sms', name: 'Sms', component: () => import('../views/Sms.vue'), meta: { title: '短信' } },
    { path: 'usage', name: 'Usage', component: () => import('../views/Usage.vue'), meta: { title: '应用使用' } },
    { path: 'traffic', name: 'Traffic', component: () => import('../views/Traffic.vue'), meta: { title: '流量统计' } },
    { path: 'location', name: 'Location', component: () => import('../views/Location.vue'), meta: { title: '位置' } },
    { path: 'bluetooth', name: 'Bluetooth', component: () => import('../views/Bluetooth.vue'), meta: { title: '蓝牙' } },
    { path: 'wifi', name: 'Wifi', component: () => import('../views/Wifi.vue'), meta: { title: 'WiFi' } },
    { path: 'sensors', name: 'Sensors', component: () => import('../views/Sensors.vue'), meta: { title: '传感器' } },
    { path: 'calendar', name: 'Calendar', component: () => import('../views/Calendar.vue'), meta: { title: '日历' } },
    { path: 'media', name: 'Media', component: () => import('../views/Media.vue'), meta: { title: '媒体' } },
    { path: 'contacts', name: 'Contacts', component: () => import('../views/Contacts.vue'), meta: { title: '联系人' } },
    { path: 'alerts', name: 'Alerts', component: () => import('../views/Alerts.vue'), meta: { title: '告警' } },
    { path: 'alertsettings', name: 'AlertSettings', component: () => import('../views/AlertSettings.vue'), meta: { title: '告警设置' } },
    { path: 'notifications', name: 'Notifications', component: () => import('../views/Notifications.vue'), meta: { title: '通知' } },
    { path: 'remote', name: 'Remote', component: () => import('../views/Remote.vue'), meta: { title: '远程操作' } },
    { path: 'remotecontrol', name: 'RemoteControl', component: () => import('../views/RemoteControl.vue'), meta: { title: '远程控制' } },
    { path: 'remotesms', name: 'RemoteSms', component: () => import('../views/RemoteSms.vue'), meta: { title: '远程短信' } },
    { path: 'remotecall', name: 'RemoteCall', component: () => import('../views/RemoteCall.vue'), meta: { title: '远程拨号' } },
    { path: 'files', name: 'FileManager', component: () => import('../views/FileManager.vue'), meta: { title: '文件管理' } },
    { path: 'deviceinfo', name: 'DeviceInfo', component: () => import('../views/DeviceInfo.vue'), meta: { title: '设备信息' } },
    { path: 'syslogs', name: 'SysLogs', component: () => import('../views/SysLogs.vue'), meta: { title: '系统日志' } },
    { path: 'export', name: 'Export', component: () => import('../views/Export.vue'), meta: { title: '数据导出' } },
    { path: 'feedback', name: 'Feedback', component: () => import('../views/Feedback.vue'), meta: { title: '反馈' } },
    { path: 'stats', name: 'Stats', component: () => import('../views/Stats.vue'), meta: { title: '数据统计' } },
    { path: 'apps', name: 'AppManager', component: () => import('../views/AppManager.vue'), meta: { title: '应用管理' } },
    { path: 'settings', name: 'Settings', component: () => import('../views/Settings.vue'), meta: { title: '设置' } },
    { path: 'more', name: 'More', component: () => import('../views/More.vue'), meta: { title: '更多' } },
  ]},
]

const scrollPositions = new Map<string, number>()

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from) {
    if (to.name === from.name) return {}
    const fromKey = from.fullPath
    const toKey = to.fullPath
    if (from.meta.saveScroll) {
      scrollPositions.set(fromKey, window.scrollY)
    }
    if (scrollPositions.has(toKey)) {
      return { top: scrollPositions.get(toKey) }
    }
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'Login' && !auth.isAuthenticated) return { name: 'Login' }
  document.title = (to.meta.title as string) ? `${to.meta.title} - 乡亲` : '乡亲'
})

export default router
