package com.xiangqin.app.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.AlertEntity
import com.xiangqin.app.data.datastore.AppDataStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 🚨 异常检测引擎
 *
 * 周期性地检查各种异常场景，生成告警记录。
 * 告警由 [AlertPushManager] 统一推送。
 */
class AlertEngine(private val context: Context) {

    private val app get() = XiangQinApp.instance
    private val db get() = app.database
    private val store get() = app.dataStore

    /** 深夜时段：22:00-06:00 */
    private val isLateNight: Boolean
        get() {
            val h = LocalTime.now().hour
            return h >= 22 || h < 6
        }

    /** 凌晨时段：00:00-05:59 */
    private val isOffHour: Boolean
        get() = LocalTime.now().hour in 0..5

    /** 检查类型在 N 毫秒内是否已触发过 */
    private suspend fun alreadyTriggered(type: String, withinMs: Long): Boolean {
        return db.alertDao().countByTypeSince(System.currentTimeMillis() - withinMs, type) > 0
    }

    /** 检测：位置相关告警（每 5 分钟） */
    suspend fun checkLocationAlerts(): List<AlertEntity> {
        if (!store.isAlertEnabled(AppDataStore.ALERT_LATE_NIGHT_LEAVE)) return emptyList()
        if (!isLateNight) return emptyList()

        val homeZone = store.getHomeZone() ?: return emptyList()
        val lastLoc = db.locationDao().getLastLocation() ?: return emptyList()

        val distance = distanceBetween(homeZone, lastLoc.latitude, lastLoc.longitude)
        if (distance <= homeZone.radiusMeters) return emptyList()
        if (alreadyTriggered("late_night_leave", 30 * 60 * 1000L)) return emptyList()

        val now = LocalTime.now()
        return listOf(AlertEntity(
            type = "late_night_leave",
            title = "🌙 深夜离家",
            message = "在 ${now.hour}:${"%02d".format(now.minute)} 检测到离开家，当前位置距家约 ${distance.toInt()} 米",
            severity = "critical",
            triggeredTime = System.currentTimeMillis()
        ))
    }

    /** 检测：低电量告警（每 15 分钟） */
    suspend fun checkBatteryAlerts(): List<AlertEntity> {
        if (!store.isAlertEnabled(AppDataStore.ALERT_LOW_BATTERY)) return emptyList()

        val batteryInfo = getBatteryInfo() ?: return emptyList()
        if (batteryInfo.level > 20 || batteryInfo.isCharging) return emptyList()
        if (alreadyTriggered("low_battery", 60 * 60 * 1000L)) return emptyList()

        return listOf(AlertEntity(
            type = "low_battery",
            title = "🔋 电量不足",
            message = "当前电量 ${batteryInfo.level}%，未在充电，建议及时充电",
            severity = if (batteryInfo.level <= 10) "critical" else "warning",
            triggeredTime = System.currentTimeMillis()
        ))
    }

    /** 检测：心跳丢失（每 1 小时检测一次） */
    suspend fun checkHeartbeatAlerts(): List<AlertEntity> {
        if (!store.isAlertEnabled(AppDataStore.ALERT_NO_HEARTBEAT)) return emptyList()

        val lastHb = store.getLastHeartbeatTime()
        if (lastHb <= 0) return emptyList()

        val elapsed = System.currentTimeMillis() - lastHb
        if (elapsed <= 6 * 60 * 60 * 1000L) return emptyList()
        if (alreadyTriggered("no_heartbeat", 24 * 60 * 60 * 1000L)) return emptyList()

        val hoursAgo = elapsed / (60 * 60 * 1000)
        return listOf(AlertEntity(
            type = "no_heartbeat",
            title = "💤 长时间无心跳",
            message = "已 ${hoursAgo} 小时未收到设备心跳信号，可能已关机、服务被暂停或不在服务区",
            severity = "critical",
            triggeredTime = System.currentTimeMillis()
        ))
    }

    /** 检测：凌晨通话（每小时检测一次） */
    suspend fun checkOffHourAlerts(): List<AlertEntity> {
        if (!store.isAlertEnabled(AppDataStore.ALERT_OFF_HOUR_CALL)) return emptyList()
        if (!isOffHour) return emptyList()

        val since = System.currentTimeMillis() - 60 * 60 * 1000
        val recentCalls = db.callDao().countByDate(since, System.currentTimeMillis())
        if (recentCalls == 0) return emptyList()

        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (alreadyTriggered("off_hour_call", System.currentTimeMillis() - todayStart)) return emptyList()

        val now = LocalTime.now()
        return listOf(AlertEntity(
            type = "off_hour_call",
            title = "🌙 凌晨通话",
            message = "凌晨 ${now.hour}:${"%02d".format(now.minute)} 检测到 $recentCalls 通电话",
            severity = "warning",
            triggeredTime = System.currentTimeMillis()
        ))
    }

    /** 检测：设备重启 */
    suspend fun checkBootAlerts(): List<AlertEntity> {
        if (!store.isAlertEnabled(AppDataStore.ALERT_BOOT)) return emptyList()

        val bootTime = store.getLastBootTime()
        if (bootTime <= 0) return emptyList()

        val now = System.currentTimeMillis()
        if (now - bootTime >= 2 * 60 * 1000) return emptyList()
        if (alreadyTriggered("device_boot", 10 * 60 * 1000L)) return emptyList()

        return listOf(AlertEntity(
            type = "device_boot",
            title = "🔄 设备重启",
            message = "手机刚刚重启完成",
            severity = "info",
            triggeredTime = bootTime
        ))
    }

    /** 检测：SIM 卡变化（每次检测） */
    suspend fun checkSimAlerts(): List<AlertEntity> {
        if (!store.isAlertEnabled(AppDataStore.ALERT_SIM_CHANGE)) return emptyList()

        val simMonitor = SimMonitor(context)
        val change = simMonitor.checkSimChange()
        return when (change) {
            is SimChangeResult.NoChange -> emptyList()
            is SimChangeResult.SimRemoved -> {
                if (alreadyTriggered("sim_removed", 60 * 60 * 1000L)) return emptyList()
                val operator = change.oldInfo.operator ?: "未知运营商"
                listOf(AlertEntity(
                    type = "sim_removed",
                    title = "📱 SIM卡被拔出",
                    message = "检测到 SIM 卡($operator) 已被拔出！",
                    severity = "critical",
                    triggeredTime = System.currentTimeMillis()
                ))
            }
            is SimChangeResult.SimInserted -> {
                if (alreadyTriggered("sim_inserted", 60 * 60 * 1000L)) return emptyList()
                val operator = change.newInfo.operator ?: "未知运营商"
                listOf(AlertEntity(
                    type = "sim_inserted",
                    title = "📱 SIM卡已插入",
                    message = "新插入 SIM 卡: $operator",
                    severity = "warning",
                    triggeredTime = System.currentTimeMillis()
                ))
            }
            is SimChangeResult.SimReplaced -> {
                if (alreadyTriggered("sim_replaced", 60 * 60 * 1000L)) return emptyList()
                val oldOp = change.oldInfo.operator ?: "未知"
                val newOp = change.newInfo.operator ?: "未知"
                listOf(AlertEntity(
                    type = "sim_replaced",
                    title = "🚨 SIM卡被更换！",
                    message = "SIM 卡已被更换！\n旧卡: $oldOp\n新卡: $newOp",
                    severity = "critical",
                    triggeredTime = System.currentTimeMillis()
                ))
            }
        }
    }

    /** 检测：应用安装/卸载变化 */
    suspend fun checkAppChangeAlerts(): List<AlertEntity> {
        if (!store.isAlertEnabled(AppDataStore.ALERT_APP_INSTALL)) return emptyList()
        // 应用变化由 AppChangeReceiver 写入 system_logs，这里只检查是否有未处理的
        val recent = db.systemLogDao().getRecent(limit = 10)
        val appChanges = recent.filter {
            it.logType == "app_installed" || it.logType == "app_uninstalled"
        }
        val pending = appChanges.filter { it.createdTime > System.currentTimeMillis() - 300_000 }
        if (pending.isEmpty()) return emptyList()

        return pending.map { log ->
            val isInstall = log.logType == "app_installed"
            AlertEntity(
                type = if (isInstall) "app_installed" else "app_uninstalled",
                title = if (isInstall) "📦 安装了新应用" else "🗑️ 应用被卸载",
                message = log.message,
                severity = "info",
                triggeredTime = log.createdTime
            )
        }
    }

    /** 检测：WiFi 连接变化 */
    suspend fun checkWifiChangeAlerts(): List<AlertEntity> {
        if (!store.isAlertEnabled(AppDataStore.ALERT_WIFI_CHANGE)) return emptyList()
        val currentSsid = store.getLastWifiSsid() ?: return emptyList()
        val lastAlertedSsid = store.getLastAlertedWifiSsid()

        // 如果和上次告警的 SSID 相同，不重复告警
        if (currentSsid == lastAlertedSsid) return emptyList()
        if (alreadyTriggered("wifi_change", 30 * 60 * 1000L)) return emptyList()

        store.setLastAlertedWifiSsid(currentSsid)

        val verb = if (lastAlertedSsid == null) "连接了" else "切换到了"
        return listOf(AlertEntity(
            type = "wifi_change",
            title = "📶 连接了新WiFi",
            message = "$verb $currentSsid",
            severity = "info",
            triggeredTime = System.currentTimeMillis()
        ))
    }

    /** 执行所有检测（不含心跳，心跳由 MonitoringService 独立触发） */
    suspend fun checkAll(): List<AlertEntity> {
        val all = mutableListOf<AlertEntity>()
        for (check in listOf(
            ::checkBootAlerts, ::checkLocationAlerts,
            ::checkBatteryAlerts, ::checkOffHourAlerts,
            ::checkSimAlerts, ::checkAppChangeAlerts,
            ::checkWifiChangeAlerts
        )) {
            try { all.addAll(check()) } catch (e: Exception) { android.util.Log.e("XiangQin/Alert", "告警检测异常", e) }
        }
        return all
    }

    /** 批量保存告警到数据库，返回带 ID 的实体列表 */
    suspend fun persistAll(alerts: List<AlertEntity>): List<AlertEntity> {
        return alerts.map { it.copy(id = db.alertDao().insert(it)) }
    }

    // ====================== 工具 ======================

    private data class BatteryInfo(val level: Int, val isCharging: Boolean)

    private fun getBatteryInfo(): BatteryInfo? {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale < 0) return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            BatteryInfo(
                (level.toFloat() / scale * 100).toInt(),
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            )
        } catch (e: Exception) { android.util.Log.e("XiangQin/Alert", "获取电量失败", e); null }
    }

    /** Haversine 公式计算两点距离（米） */
    private fun distanceBetween(home: com.xiangqin.app.data.db.HomeZone, lat: Double, lng: Double): Float {
        val R = 6371000.0
        val dLat = Math.toRadians(lat - home.latitude)
        val dLon = Math.toRadians(lng - home.longitude)
        val a = Math.pow(Math.sin(dLat / 2), 2.0) +
                Math.cos(Math.toRadians(home.latitude)) * Math.cos(Math.toRadians(lat)) *
                Math.pow(Math.sin(dLon / 2), 2.0)
        return (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }
}
