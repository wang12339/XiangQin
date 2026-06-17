package com.xiangqin.app.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.SystemLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * ⚙️ 系统设置变化监控器
 * 实时监听时间/时区/APN/语言等系统设置变更
 */
class SystemSettingsChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SysSettings"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun getIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_LOCALE_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(TelephonyManager.ACTION_SUBSCRIPTION_CARRIER_IDENTITY_CHANGED)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        scope.launch {
            val message = buildMessage(context, action, intent)
            if (message != null) {
                try {
                    XiangQinApp.instance.database.systemLogDao().insert(
                        SystemLogEntity(
                            logType = "settings_changed",
                            message = message,
                            createdTime = System.currentTimeMillis()
                        )
                    )
                    Log.d(TAG, message)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to log settings change", e)
                }
            }
        }
    }

    private fun buildMessage(context: Context, action: String, intent: Intent): String? {
        return when (action) {
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val tz = TimeZone.getDefault().displayName
                "🕐 时区变更: $tz"
            }
            Intent.ACTION_TIME_CHANGED -> {
                val isAutoTime = try {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME)
                } catch (_: Exception) { -1 }
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                "🕐 时间变更: $timeStr (自动同步=${if (isAutoTime == 1) "开" else if (isAutoTime == 0) "关" else "未知"})"
            }
            Intent.ACTION_DATE_CHANGED -> {
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date())
                "📅 日期变更: $dateStr"
            }
            Intent.ACTION_LOCALE_CHANGED -> {
                val locale = java.util.Locale.getDefault()
                "🌐 语言区域变更: ${locale.displayName} (${locale.toLanguageTag()})"
            }
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val isAirplane = intent.getBooleanExtra("state", false)
                "✈️ 飞行模式: ${if (isAirplane) "开启" else "关闭"}"
            }
            TelephonyManager.ACTION_SUBSCRIPTION_CARRIER_IDENTITY_CHANGED -> {
                "📡 运营商信息变更"
            }
            else -> null
        }
    }
}
