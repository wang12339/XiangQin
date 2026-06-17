package com.xiangqin.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.pm.PackageManager
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * 🔔 通知监听服务
 *
 * 监听系统所有通知，捕获通知内容存储到数据库。
 * 需要用户手动授权「通知访问权限」。
 * 设置路径：设置 → 应用管理 → 乡亲 → 通知读取权限
 */
class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.isOngoing) return // 忽略正在进行的通知（前台服务等）

            val notification = sbn.notification
            val pkg = sbn.packageName

            // 排除自身通知
            if (pkg == packageName) return

            val appName = try {
                val pm = packageManager
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                pkg
            }

            val extras = notification.extras ?: return
            val title = extras.getString(Notification.EXTRA_TITLE)?.take(200)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(500)

            // 忽略空内容
            if (title == null && text == null) return

            try {
                val app = XiangQinApp.instance
                notificationExecutor.execute {
                    try {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            app.database.notificationDao().insert(
                                NotificationEntity(
                                    packageName = pkg,
                                    appName = appName,
                                    title = title,
                                    text = text,
                                    postTime = sbn.postTime,
                                    capturedTime = System.currentTimeMillis()
                                )
                            )
                        }
                    } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
                }
            } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
        } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 不需要处理
    }

    companion object {
        private val notificationExecutor = Executors.newSingleThreadExecutor()
    }
}
