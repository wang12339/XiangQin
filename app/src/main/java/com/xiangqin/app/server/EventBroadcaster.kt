package com.xiangqin.app.server

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 🔌 WebSocket 实时事件推送器
 * 管理所有 WebSocket 连接，广播监控事件到所有连接的 Web 客户端
 */
object EventBroadcaster {

    private val connectedClients = mutableSetOf<WebSocketSession>()
    private val json = Json { encodeDefaults = true }

    /** 添加客户端连接 */
    fun addClient(session: WebSocketSession) {
        connectedClients.add(session)
    }

    /** 移除客户端连接 */
    fun removeClient(session: WebSocketSession) {
        connectedClients.remove(session)
    }

    /** 广播事件到所有客户端 */
    suspend fun broadcast(eventType: String, data: String) {
        val payload = """{"type":"$eventType","data":$data,"time":${System.currentTimeMillis()}}"""
        val disconnected = mutableSetOf<WebSocketSession>()
        for (client in connectedClients) {
            try {
                if (client.isActive) {
                    client.send(Frame.Text(payload))
                } else {
                    disconnected.add(client)
                }
            } catch (_: Exception) {
                disconnected.add(client)
            }
        }
        connectedClients.removeAll(disconnected)
    }

    /** 客户端数量 */
    val clientCount: Int get() = connectedClients.size
}
