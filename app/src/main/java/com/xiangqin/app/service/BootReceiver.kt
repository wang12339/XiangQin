package com.xiangqin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.xiangqin.app.receiver.KeepAliveReceiver

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 1. 启动前台服务
            val serviceIntent = Intent(context, MonitoringService::class.java)
            serviceIntent.putExtra("RESTART_REASON", "boot")
            val isEmulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("sdk_gphone")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isEmulator) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 2. 直接注册保活看门狗（如果服务启动失败，至少看门狗会重试）
            KeepAliveReceiver.scheduleNextAlarm(context)
        }
    }
}
