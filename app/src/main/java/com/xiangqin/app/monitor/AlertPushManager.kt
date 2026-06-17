package com.xiangqin.app.monitor

import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.AlertEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 📤 告警推送管理器
 *
 * 支持飞书 Webhook 推送告警到你的飞书。
 * 配置方式：在乡亲 Web 面板设置 → 飞书 Webhook URL
 */
object AlertPushManager {

    private const val TIMEOUT_MS = 10_000

    /** 推送一条告警（内部使用已获取的 webhook URL） */
    private suspend fun push(webhookUrl: String, alert: AlertEntity): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val payload = buildFeishuPayload(alert)
                val conn = URL(webhookUrl).openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload); it.flush() }
                val ok = conn.responseCode in 200..299
                conn.disconnect()
                ok
            }
        } catch (_: Exception) { false }
    }

    /** 推送所有未推送的告警并标记已推送 */
    suspend fun pushPendingAlerts() {
        val store = XiangQinApp.instance.dataStore
        val webhookUrl = store.getFeishuWebhook() ?: return
        if (webhookUrl.isBlank()) return

        val pending = XiangQinApp.instance.database.alertDao().getUnpushedAlerts()
        if (pending.isEmpty()) return

        for (alert in pending) {
            if (push(webhookUrl, alert)) {
                XiangQinApp.instance.database.alertDao().markPushed(
                    alert.id, "feishu", System.currentTimeMillis()
                )
            }
        }
    }

    /** 发送测试消息 */
    suspend fun sendTestMessage(): Boolean {
        val webhookUrl = XiangQinApp.instance.dataStore.getFeishuWebhook() ?: return false
        if (webhookUrl.isBlank()) return false

        val testAlert = AlertEntity(
            type = "test",
            title = "🔔 乡亲告警测试",
            message = "告警推送通道测试成功！\n如果你看到这条消息，说明配置正确。",
            severity = "info",
            triggeredTime = System.currentTimeMillis()
        )
        return push(webhookUrl, testAlert)
    }

    /** 构建飞书消息卡片 */
    private fun buildFeishuPayload(alert: AlertEntity): String {
        val (emoji, tmpl, label) = when (alert.severity) {
            "critical" -> Triple("🔴", "red", "严重")
            "warning" -> Triple("🟡", "orange", "警告")
            else -> Triple("ℹ️", "blue", "提示")
        }
        val timeStr = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(alert.triggeredTime))

        return """{"msg_type":"interactive","card":{"header":{"title":{"tag":"plain_text","content":"${emoji} [乡亲] ${escapedJson(alert.title)}"},"template":"$tmpl"},"elements":[{"tag":"markdown","content":"${escapedJson(alert.message)}"},{"tag":"hr"},{"tag":"note","elements":[{"tag":"plain_text","content":"乡亲家庭守护 · 告警级别: $label · $timeStr"}]}]}}"""
    }

    private fun escapedJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
