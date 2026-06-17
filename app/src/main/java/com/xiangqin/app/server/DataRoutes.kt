package com.xiangqin.app.server

import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.service.MonitoringService
import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

internal fun Route.dataRoutes(app: XiangQinApp, context: Context, auth: AuthModule) {
    get("/api/calls") {
        if (!auth.checkAuth(call)) return@get
        try {
            val calls = app.database.callDao().getCalls(limit = 200)
            call.respond(calls)
        } catch (e: Exception) {
            call.respondText("""{"error":"查询通话记录失败","detail":"${escapedJson(e.message ?: "")}"}""", ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }
    get("/api/sms") {
        if (!auth.checkAuth(call)) return@get
        try {
            val smsList = app.database.smsDao().getSms(limit = 200)
            call.respond(smsList)
        } catch (e: Exception) {
            call.respondText("""{"error":"查询短信记录失败","detail":"${escapedJson(e.message ?: "")}"}""", ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }
    get("/api/usage") {
        if (!auth.checkAuth(call)) return@get
        val date = call.request.queryParameters["date"] ?: java.time.LocalDate.now().toString()
        call.respond(app.database.appUsageDao().getByDate(date))
    }
    get("/api/traffic") {
        if (!auth.checkAuth(call)) return@get
        val date = call.request.queryParameters["date"] ?: java.time.LocalDate.now().toString()
        val traffic = app.database.trafficDao().getByDate(date)
        call.respond(TrafficResponse(traffic, traffic.sumOf { it.rxBytes }, traffic.sumOf { it.txBytes }))
    }
    get("/api/stats/summary") {
        if (!auth.checkAuth(call)) return@get
        val today = java.time.LocalDate.now().toString()
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + 86400000L
        val ip = getLocalIpAddress()
        call.respond(StatsSummary(
            date = today,
            callCount = app.database.callDao().countByDate(todayStart, todayEnd),
            smsCount = app.database.smsDao().countByDate(todayStart, todayEnd),
            totalRx = app.database.trafficDao().getTotalByDate(today)?.rx ?: 0,
            totalTx = app.database.trafficDao().getTotalByDate(today)?.tx ?: 0,
            latestCalls = app.database.callDao().getCalls(limit = 10),
            latestSms = app.database.smsDao().getSms(limit = 10),
            serviceUptime = MonitoringService.serviceUptime,
            locationCount = app.database.locationDao().getRecent(limit = 10000).size,
            alertToday = app.database.alertDao().countSince(todayStart),
            localIp = ip,
            port = 8080
        ))
    }
    get("/api/stats/daily") {
        if (!auth.checkAuth(call)) return@get
        val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
        val result = mutableListOf<String>()
        val zone = java.time.ZoneId.systemDefault()
        for (i in days - 1 downTo 0) {
            val date = java.time.LocalDate.now().minusDays(i.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = start + 86_400_000L
            val label = date.toString().substring(5)
            val calls = app.database.callDao().countByDate(start, end)
            val sms = app.database.smsDao().countByDate(start, end)
            val alerts = app.database.alertDao().countSince(start).coerceAtMost(9999)
            result.add("""{"date":"$label","calls":$calls,"sms":$sms,"alerts":$alerts}""")
        }
        call.respondText("""{"days":[${result.joinToString(",")}]}""", ContentType.Application.Json)
    }
    get("/api/settings") {
        if (!auth.checkAuth(call)) return@get
        val ip = getLocalIpAddress()
        call.respond(SettingsResponse(
            hostname = "XiangQin", localIp = ip ?: "unknown", port = 8080,
            serviceRunning = MonitoringService.isRunning,
            deviceActive = isDeviceAdmin(app)
        ))
    }
    get("/api/health") {
        call.respondText("""{"status":"ok","time":${System.currentTimeMillis()}}""", ContentType.Application.Json)
    }
    get("/api/battery") {
        if (!auth.checkAuth(call)) return@get
        val info = com.xiangqin.app.service.BatteryState.get(context)
        val multiplier = com.xiangqin.app.service.BatteryState.intervalMultiplier(info)
        call.respond(BatteryInfoResponse(
            level = info.level, isCharging = info.isCharging,
            pluggedType = info.pluggedType, intervalMultiplier = multiplier
        ))
    }
    get("/api/speedtest") {
        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 102400
        val data = ByteArray(minOf(size, 1048576))
        java.security.SecureRandom().nextBytes(data)
        call.respondBytes(data, ContentType.Application.OctetStream)
    }

    get("/api/locations") {
        if (!auth.checkAuth(call)) return@get
        val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 1
        val from = System.currentTimeMillis() - days * 86400000L
        val to = System.currentTimeMillis()
        val locations = app.database.locationDao().getByDateRange(from, to)
        call.respond(LocationsResponse(
            count = locations.size,
            points = locations.map { LocationPoint(it.latitude, it.longitude, it.accuracy, it.recordedTime) },
            latest = locations.lastOrNull()
        ))
    }
    get("/api/locations/latest") {
        if (!auth.checkAuth(call)) return@get
        val loc = app.database.locationDao().getLastLocation()
        call.respond(LocationLatestResponse(loc, loc != null))
    }
    get("/api/bluetooth/devices") {
        if (!auth.checkAuth(call)) return@get
        val devices = app.database.bluetoothDeviceDao().getAll()
        call.respond(BluetoothResponse(devices, count = devices.size))
    }
    get("/api/wifi/networks") {
        if (!auth.checkAuth(call)) return@get
        val networks = app.database.wifiNetworkDao().getAll()
        call.respond(WifiResponse(networks, count = networks.size))
    }
    get("/api/wifi/security") {
        if (!auth.checkAuth(call)) return@get
        try {
            val securityMonitor = com.xiangqin.app.monitor.WifiSecurityMonitor(context)
            val summary = securityMonitor.getSecuritySummary()
            call.respond(summary)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
    get("/api/network") {
        if (!auth.checkAuth(call)) return@get
        try {
            val wifiNetworks = app.database.wifiNetworkDao().getAll()
            val btDevices = app.database.bluetoothDeviceDao().getAll()
            call.respondText(jsonMap("wifiNetworks" to wifiNetworks, "bluetoothDevices" to btDevices), ContentType.Application.Json)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
    get("/api/logs") {
        if (!auth.checkAuth(call)) return@get
        try {
            val logs = app.database.systemLogDao().getRecent(limit = 200)
            call.respond(logs)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, listOf<Any>())
        }
    }
    get("/api/activities") {
        if (!auth.checkAuth(call)) return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val activities = app.database.activityDao().getRecent(limit)
        call.respond(ActivityListResponse(activities, activities.size))
    }
    get("/api/sensors") {
        if (!auth.checkAuth(call)) return@get
        val type = call.request.queryParameters["type"] ?: ""
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val sensors = if (type.isNotEmpty()) app.database.sensorDao().getByType(type, limit)
        else app.database.sensorDao().getRecent(limit)
        call.respond(SensorListResponse(sensors, sensors.size))
    }
    get("/api/calendar/events") {
        if (!auth.checkAuth(call)) return@get
        val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
        val from = System.currentTimeMillis()
        val to = from + days * 86400000L
        val events = app.database.calendarEventDao().getByDateRange(from, to)
        call.respond(CalendarEventListResponse(events, events.size))
    }
    get("/api/media") {
        if (!auth.checkAuth(call)) return@get
        val type = call.request.queryParameters["type"]
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val files = if (type != null) app.database.mediaFileDao().getByType(type, limit, offset)
        else app.database.mediaFileDao().getRecent(limit, offset)
        val counts = mapOf(
            "images" to app.database.mediaFileDao().countByType("image"),
            "videos" to app.database.mediaFileDao().countByType("video"),
            "audio" to app.database.mediaFileDao().countByType("audio")
        )
        call.respond(MediaResponse(files, counts, count = files.size, type = type))
    }
    get("/api/notifications") {
        if (!auth.checkAuth(call)) return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val notifications = app.database.notificationDao().getRecent(limit = limit)
        call.respond(NotificationListResponse(notifications, notifications.size))
    }
    get("/api/notifications/recent") {
        if (!auth.checkAuth(call)) return@get
        val minutes = call.request.queryParameters["minutes"]?.toLongOrNull() ?: 60
        val since = System.currentTimeMillis() - minutes * 60_000L
        val notifications = app.database.notificationDao().getSince(since)
        call.respond(NotificationListResponse(notifications, notifications.size))
    }
    post("/api/notifications/read/{id}") {
        if (!auth.checkAuth(call)) return@post
        val id = call.parameters["id"]?.toLongOrNull() ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id")); return@post
        }
        app.database.notificationDao().markRead(id)
        call.respond(MessageResponse("marked as read"))
    }
    get("/api/stats/dashboard") {
        if (!auth.checkAuth(call)) return@get
        try {
            val today = java.time.LocalDate.now()
            val todayStart = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            val loc = app.database.locationDao().getLastLocation()
            val btCount = app.database.bluetoothDeviceDao().count()
            val wifiCount = app.database.wifiNetworkDao().count()
            val activity = app.database.activityDao().getRecent(1).firstOrNull()
            val photoCount = app.database.photoDao().count()
            val audioCount = app.database.audioRecordingDao().count()
            val alertCount = app.database.alertDao().countSince(todayStart)
            val notifCount = app.database.notificationDao().count()
            val contactCount = try {
                val cursor = context.contentResolver.query(
                    android.provider.ContactsContract.Contacts.CONTENT_URI, arrayOf("_id"), null, null, null
                )
                val count = cursor?.count ?: 0
                cursor?.close()
                count
            } catch (_: Exception) { 0 }
            val actStr = activity?.activityType ?: "unknown"
            call.respond(DashboardStatsResponse(
                callsToday = app.database.callDao().countByDate(todayStart, now),
                smsToday = app.database.smsDao().countByDate(todayStart, now),
                lastLocation = loc,
                bluetoothDeviceCount = btCount, wifiNetworkCount = wifiCount,
                currentActivity = actStr,
                photoCount = photoCount, audioRecordingCount = audioCount,
                alertToday = alertCount, notificationCount = notifCount,
                contactCount = contactCount, isRecording = false
            ))
        } catch (e: Exception) {
            call.respondText("""{"error":"${e.message ?: "unknown"}"}""", ContentType.Application.Json)
        }
    }
}

@Serializable
internal data class TrafficResponse(val traffic: List<com.xiangqin.app.data.db.TrafficEntity>, val totalRx: Long, val totalTx: Long)
@Serializable
internal data class StatsSummary(
    val date: String, val callCount: Int, val smsCount: Int,
    val totalRx: Long, val totalTx: Long,
    val latestCalls: List<com.xiangqin.app.data.db.CallEntity>,
    val latestSms: List<com.xiangqin.app.data.db.SmsEntity>,
    val serviceUptime: Long, val locationCount: Int,
    val alertToday: Int, val localIp: String?, val port: Int
)
@Serializable
internal data class SettingsResponse(
    val hostname: String, val localIp: String, val port: Int,
    val serviceRunning: Boolean, val deviceActive: Boolean
)

private fun isDeviceAdmin(app: XiangQinApp): Boolean {
    return try {
        val ctx = app.applicationContext
        val dpm = ctx.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val cn = android.content.ComponentName(ctx, com.xiangqin.app.receiver.XiangQinDeviceAdminReceiver::class.java)
        dpm.isAdminActive(cn)
    } catch (_: Exception) { false }
}

private fun getLocalIpAddress(): String? {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            if (intf.isLoopback || !intf.isUp) continue
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip
                }
            }
        }
    } catch (_: Exception) {}
    return null
}
