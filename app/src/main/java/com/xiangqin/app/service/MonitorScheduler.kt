package com.xiangqin.app.service

import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.monitor.*
import kotlinx.coroutines.*
import java.time.LocalDate

internal class MonitorScheduler(
    private val service: MonitoringService,
    private val scope: CoroutineScope
) {
    private val app get() = XiangQinApp.instance
    private fun logE(tag: String, msg: String, e: Exception? = null) {
        android.util.Log.e("XiangQin/$tag", "$msg (${e?.message ?: "unknown"})", e)
    }

    private fun batteryMultiplier(): Double {
        val info = BatteryState.get(service)
        return BatteryState.intervalMultiplier(info)
    }

    private fun scaled(baseMs: Long): Long {
        return (baseMs * batteryMultiplier()).toLong().coerceAtLeast(60_000L)
    }

    fun startAll(
        callMonitor: CallMonitor, smsMonitor: SmsMonitor,
        usageMonitor: UsageMonitor, networkMonitor: NetworkMonitor,
        locationMonitor: LocationMonitor, bluetoothMonitor: BluetoothMonitor,
        wifiMonitor: WifiMonitor, activityMonitor: ActivityMonitor,
        sensorMonitor: SensorMonitor, calendarMonitor: CalendarMonitor,
        mediaIndexer: MediaIndexer, accountMonitor: AccountMonitor,
        cellMonitor: CellMonitor, alertEngine: AlertEngine
    ) {
        startFallbackSync(callMonitor, smsMonitor)
        startUsageSync(usageMonitor)
        startTrafficSync(networkMonitor)
        startLocationSync(locationMonitor)
        startBluetoothSync(bluetoothMonitor)
        startWifiSync(wifiMonitor)
        startActivitySync(activityMonitor)
        startSensorSync(sensorMonitor)
        startCalendarSync(calendarMonitor)
        startMediaSync(mediaIndexer)
        startAccountSync(accountMonitor)
        startCellSync(cellMonitor)
        startHeartbeatSync(callMonitor, smsMonitor, alertEngine)
        startAlertCheck(alertEngine)
    }

    private fun startFallbackSync(callMonitor: CallMonitor, smsMonitor: SmsMonitor) {
        scope.launch {
            delay(60_000L)
            while (isActive) {
                try { callMonitor.sync(); smsMonitor.sync() }
                catch (e: Exception) { logE("Sync", "通话/短信同步失败", e) }
                delay(scaled(MonitoringService.FALLBACK_SYNC_INTERVAL_MS))
            }
        }
    }

    private fun startUsageSync(usageMonitor: UsageMonitor) {
        scope.launch {
            delay(10_000L)
            while (isActive) {
                try { usageMonitor.sync(LocalDate.now()) } catch (e: Exception) { logE("Usage", "使用统计同步失败", e) }
                delay(scaled(MonitoringService.USAGE_SYNC_INTERVAL_MS))
            }
        }
    }

    private fun startTrafficSync(networkMonitor: NetworkMonitor) {
        scope.launch {
            delay(15_000L)
            while (isActive) {
                try { networkMonitor.sync(LocalDate.now()) } catch (e: Exception) { logE("Network", "流量统计同步失败", e) }
                delay(scaled(MonitoringService.TRAFFIC_SYNC_INTERVAL_MS))
            }
        }
    }

    private fun startLocationSync(locationMonitor: LocationMonitor) {
        scope.launch {
            delay(5_000L)
            while (isActive) {
                try {
                    val loc = locationMonitor.sync()
                    if (loc != null) {
                        service.broadcastEvent("location_updated", """{"latitude":${loc.latitude},"longitude":${loc.longitude},"accuracy":${loc.accuracy},"time":${loc.recordedTime}}""")
                    }
                } catch (e: Exception) { logE("Location", "位置采集失败", e) }
                delay(scaled(MonitoringService.LOCATION_INTERVAL_MS))
            }
        }
    }

    private fun startBluetoothSync(bluetoothMonitor: BluetoothMonitor) {
        scope.launch {
            delay(30_000L)
            while (isActive) {
                try { bluetoothMonitor.sync() } catch (e: Exception) { logE("Bluetooth", "蓝牙扫描失败", e) }
                delay(scaled(MonitoringService.BLUETOOTH_INTERVAL_MS))
            }
        }
    }

    private fun startWifiSync(wifiMonitor: WifiMonitor) {
        scope.launch {
            delay(35_000L)
            while (isActive) {
                try {
                    wifiMonitor.sync()
                    try { WifiSecurityMonitor(service).analyzeAll() } catch (e: Exception) { logE("WiFi", "安全分析失败", e) }
                } catch (e: Exception) { logE("WiFi", "WiFi扫描失败", e) }
                delay(scaled(MonitoringService.WIFI_INTERVAL_MS))
            }
        }
    }

    private fun startActivitySync(activityMonitor: ActivityMonitor) {
        scope.launch {
            delay(20_000L)
            while (isActive) {
                try { activityMonitor.sync() } catch (e: Exception) { logE("Activity", "活动识别失败", e) }
                delay(scaled(MonitoringService.ACTIVITY_INTERVAL_MS))
            }
        }
    }

    private fun startSensorSync(sensorMonitor: SensorMonitor) {
        scope.launch {
            delay(25_000L)
            while (isActive) {
                try { sensorMonitor.sync() } catch (e: Exception) { logE("Sensor", "传感器采集失败", e) }
                delay(scaled(MonitoringService.SENSOR_INTERVAL_MS))
            }
        }
    }

    private fun startCalendarSync(calendarMonitor: CalendarMonitor) {
        scope.launch {
            delay(60_000L)
            while (isActive) {
                try { calendarMonitor.sync() } catch (e: Exception) { logE("Calendar", "日历同步失败", e) }
                delay(scaled(MonitoringService.CALENDAR_INTERVAL_MS))
            }
        }
    }

    private fun startMediaSync(mediaIndexer: MediaIndexer) {
        scope.launch {
            delay(120_000L)
            while (isActive) {
                try { mediaIndexer.syncIncremental() } catch (e: Exception) { logE("Media", "媒体索引失败", e) }
                delay(scaled(MonitoringService.MEDIA_INTERVAL_MS))
            }
        }
    }

    private fun startAccountSync(accountMonitor: AccountMonitor) {
        scope.launch {
            delay(60_000L)
            while (isActive) {
                try { accountMonitor.sync() } catch (e: Exception) { logE("Account", "账户同步失败", e) }
                delay(scaled(MonitoringService.ACCOUNT_INTERVAL_MS))
            }
        }
    }

    private fun startCellSync(cellMonitor: CellMonitor) {
        scope.launch {
            delay(90_000L)
            while (isActive) {
                try { cellMonitor.checkCellChange() } catch (e: Exception) { logE("Cell", "基站检测失败", e) }
                delay(scaled(MonitoringService.CELL_INTERVAL_MS))
            }
        }
    }

    private fun startHeartbeatSync(callMonitor: CallMonitor, smsMonitor: SmsMonitor, alertEngine: AlertEngine) {
        scope.launch {
            try { callMonitor.sync(); smsMonitor.sync() }
            catch (e: Exception) { logE("Heartbeat", "心跳初始化同步失败", e) }
            while (isActive) {
                delay(scaled(MonitoringService.HEARTBEAT_INTERVAL_MS))
                try {
                    XiangQinApp.instance.database.systemLogDao().insert(
                        com.xiangqin.app.data.db.SystemLogEntity(
                            logType = "service_heartbeat", message = "Service alive",
                            createdTime = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) { logE("Heartbeat", "心跳写入失败", e) }
                try {
                    app.dataStore.updateHeartbeat()
                    val hbAlerts = alertEngine.checkHeartbeatAlerts()
                    if (hbAlerts.isNotEmpty()) {
                        alertEngine.persistAll(hbAlerts)
                        service.showAlertNotifications(hbAlerts)
                        AlertPushManager.pushPendingAlerts()
                    }
                } catch (e: Exception) { logE("Heartbeat", "心跳告警检测失败", e) }
            }
        }
    }

    private fun startAlertCheck(alertEngine: AlertEngine) {
        scope.launch {
            delay(60_000L)
            while (isActive) {
                try {
                    val alerts = alertEngine.checkAll()
                    if (alerts.isNotEmpty()) {
                        val saved = alertEngine.persistAll(alerts)
                        service.showAlertNotifications(saved)
                        AlertPushManager.pushPendingAlerts()
                        service.broadcastEvent("alert_new", """{"count":${saved.size},"time":${System.currentTimeMillis()}}""")
                    }
                } catch (e: Exception) { logE("Alert", "告警检测失败", e) }
                delay(scaled(MonitoringService.ALERT_CHECK_INTERVAL_MS))
            }
        }
    }
}
