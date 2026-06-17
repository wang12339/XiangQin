package com.xiangqin.app.relay

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RelayClient {
    private const val TAG = "XiangQin/Relay"
    private const val RELAY_URL = "wss://xiangqin.whanghui.top/ws/"

    @Volatile var isConnected = false; private set
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = HttpClient(OkHttp) { install(WebSockets) { pingInterval = 30_000 } }

    fun start() { Log.i(TAG, "Starting"); scope.launch { connectLoop() } }
    fun stop() { isConnected = false; client.close() }

    private suspend fun connectLoop() {
        while (true) {
            try {
                client.webSocket(RELAY_URL) {
                    Log.i(TAG, "Connected"); isConnected = true
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val msg = JSONObject(text)
                                val type = msg.optString("type")
                                if (type == "request") {
                                    launch { handleRequest(text, this@webSocket) }
                                } else if (type == "direct_upload") {
                                    launch { handleDirectUpload(msg, this@webSocket) }
                                }
                            } catch (e: Exception) { Log.w(TAG, "处理中继消息失败: ${e.message}") }
                        }
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
            isConnected = false; delay(5_000)
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

            val conn = URL("http://127.0.0.1:8080$path").openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.doOutput = body.isNotEmpty()
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000

            val hKeys = origHeaders.keys()
            while (hKeys.hasNext()) {
                val k = hKeys.next()
                conn.setRequestProperty(k, origHeaders.getString(k))
            }
            if (body.isNotEmpty()) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val status = conn.responseCode
            val respHeaders = mutableMapOf<String, String>()
            conn.headerFields.forEach { (k, v) ->
                if (k != null && k != "null") respHeaders[k] = v?.firstOrNull() ?: ""
            }
            val ct = respHeaders["Content-Type"] ?: respHeaders["content-type"] ?: ""
            val isBin = ct.startsWith("image/") || ct.startsWith("audio/") || ct.startsWith("video/")
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
                    // 限制中继传输大小：超过 2MB 的文件返回提示
                    if (bytes.size > 2 * 1024 * 1024) {
                        put("body", "")
                        put("oversized", true)
                        put("size", bytes.size)
                    } else {
                        put("body", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                    }
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

    private suspend fun handleDirectUpload(req: JSONObject, ws: WebSocketSession) {
        try {
            val id = req.getString("id")
            val path = req.getString("path")
            val file = java.io.File(path)
            if (!file.exists() || !file.isFile) {
                ws.send(Frame.Text("""{"type":"upload_result","id":"$id","error":"not found"}"""))
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
            Log.e(TAG, "Direct upload error: ${e.message}")
            val errMsg = (e.message ?: "unknown").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            try { ws.send(Frame.Text("""{"type":"upload_result","id":"${req.optString("id","")}","error":"$errMsg"}""")) } catch (e: Exception) { Log.w(TAG, "发送上传结果失败: ${e.message}") }
        }
    }
}
