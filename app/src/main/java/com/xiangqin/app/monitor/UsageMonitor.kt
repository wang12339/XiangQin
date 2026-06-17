package com.xiangqin.app.monitor

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.AppUsageEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * 应用使用统计采集器
 * 通过 UsageStatsManager 获取应用前台时间
 * 每次 sync 先删除当天数据再写入（防止重复）
 */
class UsageMonitor(private val context: Context) {

    suspend fun sync(date: LocalDate = LocalDate.now()) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = start + 86_400_000L

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            start,
            end
        )

        if (stats.isEmpty()) return

        val db = XiangQinApp.instance.database
        val usages = stats
            .filter { it.totalTimeInForeground > 0 }
            .map { stat ->
                val appName = try {
                    val ai = context.packageManager.getApplicationInfo(stat.packageName, 0)
                    context.packageManager.getApplicationLabel(ai).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    stat.packageName
                }
                AppUsageEntity(
                    packageName = stat.packageName,
                    appName = appName,
                    totalTimeForeground = stat.totalTimeInForeground,
                    usageDate = date.toString(),
                    lastUsedTime = stat.lastTimeUsed
                )
            }

        if (usages.isNotEmpty()) {
            // 先删除当天旧数据，再重新插入（避免重复）
            db.appUsageDao().deleteByDate(date.toString())
            db.appUsageDao().insertAll(usages)
        }
    }
}
