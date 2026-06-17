package com.xiangqin.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xiangqin.app.XiangQinApp
import java.util.concurrent.TimeUnit

/**
 * 数据库清理 Worker
 * 删除 30 天前的旧数据
 */
class DataCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = XiangQinApp.instance.database
            val before = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000

            db.callDao().deleteOlderThan(before)
            db.smsDao().deleteOlderThan(before)
            db.appUsageDao().deleteOlderThan(java.time.LocalDate.now().minusDays(30).toString())
            db.trafficDao().deleteOlderThan(java.time.LocalDate.now().minusDays(30).toString())
            db.systemLogDao().deleteOlderThan(before)
            // 新增实体清理
            db.locationDao().deleteOlderThan(before)
            db.bluetoothDeviceDao().deleteOlderThan(before)
            db.wifiNetworkDao().deleteOlderThan(before)
            db.activityDao().deleteOlderThan(before)
            db.sensorDao().deleteOlderThan(before)
            db.calendarEventDao().deleteOlderThan(before)
            db.mediaFileDao().deleteOlderThan(before)
            db.accountDao().deleteOlderThan(before)
            db.photoDao().deleteOlderThan(before)
            db.audioRecordingDao().deleteOlderThan(before)
            db.alertDao().deleteOlderThan(before)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "data_cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DataCleanupWorker>(
                7, TimeUnit.DAYS // 每周清理一次
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
