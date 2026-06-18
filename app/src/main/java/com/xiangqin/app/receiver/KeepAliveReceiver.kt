package com.xiangqin.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.xiangqin.app.service.MonitoringService

/**
 * 保活看门狗 — 通过 AlarmManager(setAlarmClock 类型) 定时检查服务是否存活。
 *
 * ### 策略
 * - 使用 `setAlarmClock()` 注册闹钟（MIUI/HyperOS 不会杀闹钟类应用）
 * - 每 5 分钟触发一次，检查 MonitoringService.isRunning
 * - 如果服务挂了 → 立即重启
 * - 同时重新注册下一次闹钟（链式保活）
 *
 * ### MIUI 适配
 * - Android 12+ 需要 `SCHEDULE_EXACT_ALARM` 权限才能用精确闹钟
 * - 如果用户没给该权限，降级为 `setWindow()` 非精确模式
 * - `setAlarmClock` 类型在 MIUI 上有特殊优先级，系统不敢杀
 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_KEEP_ALIVE -> onKeepAliveTick(context)
            ACTION_BOOT_WATCHDOG -> onBootWatchdog(context)
        }
    }

    /**
     * 保活心跳触发
     */
    private fun onKeepAliveTick(context: Context) {
        val isAlive = MonitoringService.isRunning

        if (!isAlive) {
            // 服务挂了 → 重启
            val serviceIntent = Intent(context, MonitoringService::class.java)
            serviceIntent.putExtra("RESTART_REASON", "keepalive_watchdog")

            val isEmulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("sdk_gphone")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isEmulator) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        // 注册下一次心跳（链式，确保看门狗持续运行）
        scheduleNextAlarm(context)
    }

    /**
     * 开机后注册看门狗（仅一次）
     */
    private fun onBootWatchdog(context: Context) {
        scheduleNextAlarm(context)
    }

    companion object {
        const val ACTION_KEEP_ALIVE = "com.xiangqin.action.KEEP_ALIVE"
        const val ACTION_BOOT_WATCHDOG = "com.xiangqin.action.BOOT_WATCHDOG"

        /** 保活检查间隔：5 分钟 */
        private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L

        /**
         * 注册下一次保活闹钟
         *
         * 优先使用 setAlarmClock（MIUI 不杀闹钟），
         * 如果没有精确闹钟权限则降级为 setWindow。
         */
        @JvmStatic
        fun scheduleNextAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, KeepAliveReceiver::class.java).apply {
                action = ACTION_KEEP_ALIVE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_KEEP_ALIVE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val triggerAtMillis = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS

            // Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限
            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()

            if (canScheduleExact) {
                // setAlarmClock — MIUI 优先级最高，系统不敢杀
                val alarmClockInfo = AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                    null // 无需显示闹钟信息
                )
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                // 降级为非精确模式（窗口期 30 秒）
                alarmManager.setWindow(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    30_000L,
                    pendingIntent
                )
            }
        }

        /**
         * 取消保活闹钟（服务停止时调用）
         */
        @JvmStatic
        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, KeepAliveReceiver::class.java).apply {
                action = ACTION_KEEP_ALIVE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_KEEP_ALIVE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }

        private const val REQUEST_CODE_KEEP_ALIVE = 9001
    }
}
