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
    private val loginAttempts = ConcurrentHashMap<String, LoginAttempt>()
    private val activeSessions = ConcurrentHashMap<String, Long>()

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L
        const val SESSION_DURATION_MS = 24 * 60 * 60 * 1000L
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
        val token = generateSessionToken()
        activeSessions[token] = System.currentTimeMillis() + SESSION_DURATION_MS
        return token
    }

    suspend fun checkAuth(call: ApplicationCall): Boolean {
        val authHeader = call.request.header(HttpHeaders.Authorization) ?: run {
            call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return false
        }
        if (!authHeader.startsWith("Basic ")) {
            call.respondText("""{"error":"bad auth scheme"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return false
        }
        val rawToken = authHeader.removePrefix("Basic ").trim()

        if (validateSession(rawToken)) return true

        val decoded = try { String(Base64.getDecoder().decode(rawToken)) } catch (_: Exception) {
            val pw = app.dataStore.getWebPassword()
            if (rawToken == pw) return true
            call.respondText("""{"error":"bad auth"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized); return false
        }
        val parts = decoded.split(":", limit = 2)
        if (parts.size != 2) {
            call.respondText("""{"error":"bad auth format"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized); return false
        }
        val pw = app.dataStore.getWebPassword()
        if (parts[0] == "admin" && parts[1] == pw) return true

        call.respondText("""{"error":"wrong password"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
        return false
    }

    suspend fun authenticateWs(frame: Frame.Text, session: WebSocketSession): Boolean {
        val text = frame.readText()
        if (!text.startsWith("auth:")) return false
        val token = text.removePrefix("auth:")
        val decoded = try { String(Base64.getDecoder().decode(token)) } catch (_: Exception) { null }
        val pw = app.dataStore.getWebPassword()
        return if (decoded == "admin:$pw" || validateSession(token)) {
            session.send(Frame.Text("""{"type":"auth_ok"}"""))
            true
        } else {
            session.send(Frame.Text("""{"type":"auth_fail","error":"invalid token"}"""))
            false
        }
    }
}
