package com.xiangqin.app.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import com.xiangqin.app.service.MonitoringService
import java.util.concurrent.TimeUnit

/**
 * 保活 Worker — 作为 AlarmManager 看门狗的后备机制。
 *
 * - 每 15 分钟检查一次 MonitoringService 是否存活
 * - 如果挂了 → 重启
 * - MIUI 可能杀 AlarmManager，但 WorkManager 有系统级保活
 */
class KeepAliveWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!MonitoringService.isRunning) {
            android.util.Log.w("XiangQin/KeepAlive", "Service not running, restarting...")
            val intent = Intent(applicationContext, MonitoringService::class.java).apply {
                putExtra("RESTART_REASON", "workmanager_keepalive")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "xiangqin_keepalive"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            ).setBackoffCriteria(
                BackoffPolicy.LINEAR,
                1, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
