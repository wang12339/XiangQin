package com.xiangqin.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.SystemLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 📦 应用安装/卸载变化监听器
 *
 * 监听系统广播，记录应用安装、卸载、更新事件。
 * 数据写入 system_logs 表，由 AlertEngine 定期检查并生成告警。
 */
class AppChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val packageName = intent.data?.encodedSchemeSpecificPart ?: return

        // 排除系统应用包
        if (packageName.startsWith("com.android.") ||
            packageName.startsWith("android.") ||
            packageName.startsWith("com.google.android.") ||
            packageName == context.packageName // 排除自己
        ) return

        val app = XiangQinApp.instance
        val pm = context.packageManager

        val logType = when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                // 如果是替换（更新）则不处理
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                "app_installed"
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                // 如果是替换（更新）则不处理
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                "app_uninstalled"
            }
            Intent.ACTION_PACKAGE_REPLACED -> "app_updated"
            else -> return
        }

        // 获取应用名
        val appName = try {
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            packageName
        }

        val message = when (logType) {
            "app_installed" -> "安装了新应用: $appName ($packageName)"
            "app_uninstalled" -> "应用被卸载: $appName ($packageName)"
            "app_updated" -> "应用已更新: $appName ($packageName)"
            else -> return
        }

        try {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    app.database.systemLogDao().insert(
                        SystemLogEntity(
                            logType = logType,
                            message = message,
                            createdTime = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
                finally { pendingResult.finish() }
            }
        } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
    }
}
