package com.xiangqin.app.server

import com.xiangqin.app.XiangQinApp
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.websocket.*
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

internal class AuthModule(private val app: XiangQinApp) {

    private data class LoginAttempt(val count: Int, val firstFailTime: Long)
    private data class RateLimitInfo(val count: Int, val windowStart: Long)
    private val loginAttempts = ConcurrentHashMap<String, LoginAttempt>()
    private val activeSessions = ConcurrentHashMap<String, Long>()
    private val rateLimits = ConcurrentHashMap<String, RateLimitInfo>()

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L
        const val SESSION_DURATION_MS = 24 * 60 * 60 * 1000L
        const val MAX_ACTIVE_SESSIONS = 10
        const val RATE_LIMIT_WINDOW_MS = 60 * 1000L // 1 分钟窗口
        const val RATE_LIMIT_MAX_REQUESTS = 100 // 每窗口最大请求数
    }

    /** 检查 API 限流 */
    fun isRateLimited(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val info = rateLimits[ip] ?: return false

        // 窗口过期，重置
        if (now - info.windowStart > RATE_LIMIT_WINDOW_MS) {
            rateLimits.remove(ip)
            return false
        }

        return info.count >= RATE_LIMIT_MAX_REQUESTS
    }

    /** 记录 API 请求 */
    fun recordApiRequest(ip: String) {
        val now = System.currentTimeMillis()
        rateLimits.compute(ip) { _, v ->
            if (v == null || now - v.windowStart > RATE_LIMIT_WINDOW_MS)
                RateLimitInfo(1, now)
            else
                RateLimitInfo(v.count + 1, v.windowStart)
        }
    }

    fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isLockedOut(ip: String): Boolean {
        val attempt = loginAttempts[ip] ?: return false
        if (System.currentTimeMillis() - attempt.firstFailTime > LOCKOUT_DURATION_MS) {
            loginAttempts.remove(ip)
            return false
        }
        return attempt.count >= MAX_FAILED_ATTEMPTS
    }

    fun recordFailedLogin(ip: String) {
        val now = System.currentTimeMillis()
        loginAttempts.compute(ip) { _, v ->
            if (v == null || now - v.firstFailTime > LOCKOUT_DURATION_MS)
                LoginAttempt(1, now)
            else
                LoginAttempt(v.count + 1, v.firstFailTime)
        }
    }

    fun clearFailedLogin(ip: String) {
        loginAttempts.remove(ip)
    }

    fun validateSession(token: String): Boolean {
        val expiry = activeSessions[token] ?: return false
        if (System.currentTimeMillis() > expiry) {
            activeSessions.remove(token)
            return false
        }
        return true
    }

    fun createSession(): String {
        // 清理过期 session
        val now = System.currentTimeMillis()
        activeSessions.entries.removeIf { now > it.value }
        // 限制活跃 session 数量
        if (activeSessions.size >= MAX_ACTIVE_SESSIONS) {
            val oldest = activeSessions.entries.minByOrNull { it.value }
            oldest?.let { activeSessions.remove(it.key) }
        }
        val token = generateSessionToken()
        activeSessions[token] = now + SESSION_DURATION_MS
        return token
    }

    /** 验证 relay 转发请求的内部认证头 */
    private suspend fun checkRelayAuth(call: ApplicationCall): Boolean {
        val relayKey = call.request.header("X-Relay-Auth") ?: return false
        val storedToken = try {
            XiangQinApp.instance.dataStore.getRelayToken()
        } catch (_: Exception) { null }
        if (storedToken.isNullOrBlank()) return false
        return java.security.MessageDigest.isEqual(relayKey.toByteArray(), storedToken.toByteArray())
    }

    suspend fun checkAuth(call: ApplicationCall): Boolean {
        val clientIp = call.request.local.remoteAddress
        val isLoopback = clientIp == "127.0.0.1" || clientIp == "::1"

        // relay 转发的请求需通过 X-Relay-Auth 头认证，不再无条件信任 loopback
        if (isLoopback) {
            if (checkRelayAuth(call)) return true
            call.respondText("""{"error":"unauthorized relay"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return false
        }

        // 检查 API 限流
        if (isRateLimited(clientIp)) {
            call.respondText(
                """{"error":"too many requests, slow down"}""",
                ContentType.Application.Json,
                HttpStatusCode.TooManyRequests
            )
            return false
        }

        // 记录请求
        recordApiRequest(clientIp)

        val authHeader = call.request.header(HttpHeaders.Authorization) ?: run {
            call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return false
        }
        if (!authHeader.startsWith("Basic ")) {
            call.respondText("""{"error":"bad auth scheme"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return false
        }
        val rawToken = authHeader.removePrefix("Basic ").trim()

        // 1. 尝试 session token
        if (validateSession(rawToken)) return true

        // 2. 尝试 Base64 解码后的 admin:password（标准 Basic Auth 格式）
        val decoded = try { String(Base64.getDecoder().decode(rawToken)) } catch (_: Exception) {
            call.respondText("""{"error":"bad auth"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized); return false
        }
        val parts = decoded.split(":", limit = 2)
        if (parts.size != 2) {
            call.respondText("""{"error":"bad auth format"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized); return false
        }
        val pw = app.dataStore.getWebPassword()
        if (parts[0] == "admin" && java.security.MessageDigest.isEqual(parts[1].toByteArray(), pw.toByteArray())) {
            // 密码验证成功，自动创建 session 以便后续使用 token
            val token = createSession()
            // 将 token 注入响应头供前端使用
            call.response.header("X-Session-Token", token)
            return true
        }

        call.respondText("""{"error":"wrong password"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
        return false
    }

    suspend fun authenticateWs(frame: Frame.Text, session: WebSocketSession): Boolean {
        val text = frame.readText()
        if (!text.startsWith("auth:")) return false
        val token = text.removePrefix("auth:")

        // 仅接受有效的 session token，不再接受明文密码
        return if (validateSession(token)) {
            session.send(Frame.Text("""{"type":"auth_ok"}"""))
            true
        } else {
            session.send(Frame.Text("""{"type":"auth_fail","error":"invalid or expired token"}"""))
            false
        }
    }
}
