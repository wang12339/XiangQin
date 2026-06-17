package com.xiangqin.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.SystemLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 日报生成 Worker
 * 每日凌晨统计昨日数据并写入系统日志 + 推送到飞书
 */
class DailyReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = XiangQinApp.instance.database
            val yesterday = LocalDate.now().minusDays(1)
            val dayStart = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = dayStart + 86_400_000L

            val callCount = db.callDao().countByDate(dayStart, dayEnd)
            val smsCount = db.smsDao().countByDate(dayStart, dayEnd)
            val calls = db.callDao().getCallsByDate(dayStart, dayEnd, limit = 500)
            val smsList = db.smsDao().getSmsByDate(dayStart, dayEnd)

            // 统计通话联系人去重
            val uniqueContacts = calls.map { it.phoneNumber }.distinct().size
            // 统计短信联系人去重
            val uniqueSmsContacts = smsList.map { it.phoneNumber }.distinct().size

            val report = buildString {
                appendLine("【日报】${yesterday}")
                appendLine("📞 通话: $callCount 次 ($uniqueContacts 个联系人)")
                appendLine("💬 短信: $smsCount 条 ($uniqueSmsContacts 个号码)")
                if (callCount > 0) {
                    val incoming = calls.count { it.callType == 1 }
                    val outgoing = calls.count { it.callType == 2 }
                    val missed = calls.count { it.callType == 3 }
                    val totalDuration = calls.sumOf { it.durationSeconds.toLong() }
                    appendLine("   来电 $incoming / 去电 $outgoing / 未接 $missed")
                    appendLine("   通话时长: ${totalDuration / 60} 分钟")
                }
            }

            // 写入系统日志
            db.systemLogDao().insert(
                SystemLogEntity(
                    logType = "daily_report",
                    message = report,
                    createdTime = System.currentTimeMillis()
                )
            )

            // 推送到飞书
            try {
                pushToFeishu(report)
            } catch (e: Exception) {
                Log.e("XiangQin/Report", "飞书推送失败", e)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** 推送日报到飞书 Webhook */
    private suspend fun pushToFeishu(report: String) {
        val store = XiangQinApp.instance.dataStore
        val webhookUrl = store.getFeishuWebhook() ?: return
        if (webhookUrl.isBlank()) return

        val payload = """{"msg_type":"interactive","card":{"header":{"title":{"tag":"plain_text","content":"📊 乡亲日报"},"template":"blue"},"elements":[{"tag":"markdown","content":"${escapedJson(report)}"},{"tag":"hr"},{"tag":"note","elements":[{"tag":"plain_text","content":"乡亲家庭守护 · 每日报告"}]}]}}"""

        withContext(Dispatchers.IO) {
            val conn = URL(webhookUrl).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload); it.flush() }
            conn.disconnect()
        }
    }

    private fun escapedJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        private const val WORK_NAME = "daily_report"

        fun schedule(context: Context) {
            // 每日凌晨 1 点执行
            val request = PeriodicWorkRequestBuilder<DailyReportWorker>(
                1, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
