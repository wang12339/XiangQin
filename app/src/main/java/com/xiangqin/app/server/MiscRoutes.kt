package com.xiangqin.app.server

import com.xiangqin.app.XiangQinApp
import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.fileRoutes(app: XiangQinApp, context: Context, auth: AuthModule) {
    post("/api/calls/delete") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = call.receive<Map<String, String>>()
            val callId = body["callId"]
            if (callId != null) {
                context.contentResolver.delete(android.provider.CallLog.Calls.CONTENT_URI, "${android.provider.CallLog.Calls._ID} = ?", arrayOf(callId))
                call.respond(CallDeleteResponse("通话记录已删除", callId = callId))
            } else {
                val all = context.contentResolver.delete(android.provider.CallLog.Calls.CONTENT_URI, null, null)
                call.respond(CallDeleteResponse("已删除全部通话记录", count = all))
            }
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "删除失败")}"}""", ContentType.Application.Json) }
    }
    post("/api/sms/delete") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = call.receive<Map<String, String>>()
            val smsId = body["smsId"]
            if (smsId != null) {
                context.contentResolver.delete(android.provider.Telephony.Sms.CONTENT_URI, "${android.provider.Telephony.Sms._ID} = ?", arrayOf(smsId))
                call.respond(SmsDeleteResponse("短信已删除", smsId = smsId))
            } else { call.respondText("""{"error":"需要指定 smsId"}""", ContentType.Application.Json) }
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "删除失败")}"}""", ContentType.Application.Json) }
    }
    get("/api/files/list") {
        if (!auth.checkAuth(call)) return@get
        try {
            val path = call.request.queryParameters["path"] ?: "/sdcard"
            val dir = java.io.File(path)
            val canonicalPath = dir.canonicalPath
            val allowed = canonicalPath.startsWith("/sdcard") || canonicalPath.startsWith("/storage") || canonicalPath.startsWith("/data/user/0") || canonicalPath.startsWith("/data/data") || canonicalPath.startsWith(context.filesDir.absolutePath)
            if (!allowed) { call.respond(HttpStatusCode.Forbidden, FileListResponse(path, emptyList(), 0)); return@get }
            if (!dir.exists() || !dir.isDirectory) { call.respond(FileListResponse(path, emptyList(), 0)); return@get }
            val files = dir.listFiles()?.map { f ->
                FileInfo(name = f.name, isDirectory = f.isDirectory, size = f.length(), lastModified = f.lastModified(), path = f.absolutePath)
            }?.sortedWith(compareByDescending<FileInfo> { it.isDirectory }.thenBy { it.name }) ?: emptyList()
            call.respond(FileListResponse(path, files, files.size))
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "读取失败")}","files":[]}""", ContentType.Application.Json) }
    }
}

internal fun Route.contactRoutes(context: Context, auth: AuthModule) {
    get("/api/contacts") {
        if (!auth.checkAuth(call)) return@get
        try {
            val query = call.request.queryParameters["q"] ?: ""
            val contacts = mutableListOf<ContactInfo>()
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.Contacts._ID, android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                if (query.isNotEmpty()) "${android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?" else null,
                if (query.isNotEmpty()) arrayOf("%$query%") else null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val name = it.getString(1) ?: continue
                    val phoneCursor = context.contentResolver.query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id), null
                    )
                    val phone = phoneCursor?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: ""
                    contacts.add(ContactInfo(id = id, name = name, phone = phone))
                }
            }
            call.respond(ContactListResponse(contacts, contacts.size))
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "查询失败")}","contacts":[],"count":0}""", ContentType.Application.Json) }
    }
    post("/api/contacts/add") {
        if (!auth.checkAuth(call)) return@post
        try {
            val text = call.receiveText()
            val name = Regex(""""name"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1) ?: ""
            val phone = Regex(""""phone"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1) ?: ""
            if (name.isEmpty() || phone.isEmpty()) { call.respondText("""{"error":"name and phone required"}""", ContentType.Application.Json); return@post }
            val ops = android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.RawContacts.CONTENT_URI)
                .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_NAME, null).build()
            val ops2 = android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(android.provider.ContactsContract.Data.MIMETYPE, android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build()
            val ops3 = android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(android.provider.ContactsContract.Data.MIMETYPE, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE, android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build()
            context.contentResolver.applyBatch("com.android.contacts", arrayListOf(ops, ops2, ops3))
            call.respond(MessageResponse("联系人已添加: $name ($phone)"))
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "add contact failed")}"}""", ContentType.Application.Json) }
    }
    post("/api/contacts/delete") {
        if (!auth.checkAuth(call)) return@post
        try {
            val text = call.receiveText()
            val contactId = Regex(""""contactId"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1) ?: ""
            if (contactId.isEmpty()) { call.respondText("""{"error":"contactId required"}""", ContentType.Application.Json); return@post }
            context.contentResolver.delete(android.provider.ContactsContract.Contacts.CONTENT_URI, "${android.provider.ContactsContract.Contacts._ID} = ?", arrayOf(contactId))
            call.respond(MessageResponse("联系人已删除"))
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "delete failed")}"}""", ContentType.Application.Json) }
    }
}

internal fun Route.accountAndSystemRoutes(app: XiangQinApp, context: Context, auth: AuthModule) {
    get("/api/accounts") {
        if (!auth.checkAuth(call)) return@get
        try {
            val am = android.accounts.AccountManager.get(context)
            val accounts = am.accounts
            call.respond(AccountListResponse(
                accounts = accounts.map { com.xiangqin.app.data.db.AccountEntity(accountName = it.name, accountType = it.type, firstSeen = System.currentTimeMillis(), lastSeen = System.currentTimeMillis()) },
                count = accounts.size
            ))
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "查询失败")}"}""", ContentType.Application.Json) }
    }
    get("/api/system/settings") {
        if (!auth.checkAuth(call)) return@get
        try {
            val cm = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            call.respond(SystemSettingsResponse(
                musicVolume = cm.getStreamVolume(android.media.AudioManager.STREAM_MUSIC), musicMax = cm.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC),
                ringVolume = cm.getStreamVolume(android.media.AudioManager.STREAM_RING), ringMax = cm.getStreamMaxVolume(android.media.AudioManager.STREAM_RING),
                alarmVolume = cm.getStreamVolume(android.media.AudioManager.STREAM_ALARM), alarmMax = cm.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            ))
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "查询失败")}"}""", ContentType.Application.Json) }
    }
    post("/api/system/settings") {
        if (!auth.checkAuth(call)) return@post
        try {
            val text = call.receiveText()
            val cm = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val results = mutableListOf<String>()
            @Suppress("DEPRECATION")
            Regex(""""musicVolume"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { cm.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, it, 0); results.add("媒体音量=$it") }
            Regex(""""ringVolume"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { cm.setStreamVolume(android.media.AudioManager.STREAM_RING, it, 0); results.add("铃声音量=$it") }
            Regex(""""alarmVolume"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { cm.setStreamVolume(android.media.AudioManager.STREAM_ALARM, it, 0); results.add("闹钟音量=$it") }
            call.respond(SystemSettingsUpdateResponse("设置已更新", results))
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "更新失败")}"}""", ContentType.Application.Json) }
    }
    get("/api/apps") {
        if (!auth.checkAuth(call)) return@get
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            val apps = packages.map { pkg ->
                val appName = try { pm.getApplicationLabel(pkg.applicationInfo)?.toString() ?: pkg.packageName } catch (_: Exception) { pkg.packageName }
                AppInfo(
                    packageName = pkg.packageName, appName = appName,
                    versionName = pkg.versionName ?: "未知",
                    isSystem = (pkg.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    installTime = pkg.firstInstallTime, updateTime = pkg.lastUpdateTime
                )
            }.sortedBy { it.appName }
            call.respond(AppListResponse(apps, apps.size))
        } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "查询失败"))) }
    }
    post("/api/apps/uninstall") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = call.receive<Map<String, String>>()
            val packageName = body["packageName"] ?: run { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing packageName")); return@post }
            val intent = android.content.Intent(android.content.Intent.ACTION_DELETE)
            intent.data = android.net.Uri.parse("package:$packageName"); intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            call.respond(MessageResponse("已启动卸载: $packageName"))
        } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "卸载失败"))) }
    }
    get("/api/apps/limits") {
        if (!auth.checkAuth(call)) return@get
        try {
            val file = java.io.File(context.filesDir, "app_limits.json")
            val limits = if (file.exists()) {
                try {
                    val map = mutableMapOf<String, Int>()
                    file.readText().removeSurrounding("{").removeSurrounding("}").split(",").forEach { entry ->
                        val parts = entry.trim().removeSurrounding("\"").split(":")
                        if (parts.size == 2) map[parts[0].trim()] = parts[1].trim().toIntOrNull() ?: 0
                    }
                    map
                } catch (_: Exception) { emptyMap<String, Int>() }
            } else emptyMap()
            call.respond(mapOf("limits" to limits))
        } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "查询失败"))) }
    }
    post("/api/apps/limit") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = call.receive<Map<String, Any>>()
            val packageName = body["packageName"] as? String ?: run { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing packageName")); return@post }
            val minutes = (body["minutes"] as? Number)?.toInt() ?: 0
            val file = java.io.File(context.filesDir, "app_limits.json")
            val limits = mutableMapOf<String, Int>()
            if (file.exists()) {
                try {
                    file.readText().removeSurrounding("{").removeSurrounding("}").split(",").forEach { entry ->
                        val parts = entry.trim().removeSurrounding("\"").split(":")
                        if (parts.size == 2) limits[parts[0].trim()] = parts[1].trim().toIntOrNull() ?: 0
                    }
                } catch (e: Exception) { android.util.Log.w("XiangQin", "解析 app_limits.json 失败: ${e.message}") }
            }
            if (minutes > 0) limits[packageName] = minutes else limits.remove(packageName)
            val jsonStr = limits.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
            file.writeText("{$jsonStr}")
            call.respond(MessageResponse("应用限制已更新"))
        } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "更新失败"))) }
    }
    get("/api/system/logs") {
        if (!auth.checkAuth(call)) return@get
        try {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val logs = app.database.systemLogDao().getRecent(limit)
            call.respond(logs.map { SystemLogInfo(it.id, it.logType, it.message, it.createdTime) })
        } catch (e: Exception) { call.respondText("""{"error":"${escapedJson(e.message ?: "查询失败")}","logs":[],"count":0}""", ContentType.Application.Json) }
    }
}

internal fun Route.exportRoutes(app: XiangQinApp, auth: AuthModule) {
    get("/api/export/calls/csv") { if (!auth.checkAuth(call)) return@get
        val csv = buildString { appendLine("时间,号码,联系人,类型,时长(秒)")
            app.database.callDao().getCalls(limit = 10000).forEach { c ->
                val type = when (c.callType) { 1 -> "来电"; 2 -> "去电"; 3 -> "未接"; else -> "未知" }
                appendLine("${c.callTime},${c.phoneNumber},${c.callerName ?: ""},$type,${c.durationSeconds}")
            }
        }
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=xiangqin_calls.csv")
        call.respondText(csv, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
    }
    get("/api/export/calls/json") { if (!auth.checkAuth(call)) return@get
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=xiangqin_calls.json")
        call.respond(app.database.callDao().getCalls(limit = 10000))
    }
    get("/api/export/sms/csv") { if (!auth.checkAuth(call)) return@get
        val csv = buildString { appendLine("时间,号码,类型,内容")
            app.database.smsDao().getSms(limit = 10000).forEach { m ->
                val type = if (m.smsType == 1) "收" else "发"
                val body = m.body?.replace("\"", "\"\"")?.replace("\n", " ") ?: ""
                appendLine("${m.receivedTime},${m.phoneNumber},$type,\"$body\"")
            }
        }
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=xiangqin_sms.csv")
        call.respondText(csv, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
    }
    get("/api/export/sms/json") { if (!auth.checkAuth(call)) return@get
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=xiangqin_sms.json")
        call.respond(app.database.smsDao().getSms(limit = 10000))
    }
    get("/api/export/usage/csv") { if (!auth.checkAuth(call)) return@get
        val date = call.request.queryParameters["date"] ?: java.time.LocalDate.now().toString()
        val csv = buildString { appendLine("应用,包名,使用时长(秒),日期")
            app.database.appUsageDao().getByDate(date).forEach { u ->
                appendLine("${u.appName ?: u.packageName},${u.packageName},${u.totalTimeForeground / 1000},${u.usageDate}")
            }
        }
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=xiangqin_usage_$date.csv")
        call.respondText(csv, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
    }
    get("/api/export/usage/json") { if (!auth.checkAuth(call)) return@get
        val date = call.request.queryParameters["date"] ?: java.time.LocalDate.now().toString()
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=xiangqin_usage_$date.json")
        call.respond(app.database.appUsageDao().getByDate(date))
    }
}
