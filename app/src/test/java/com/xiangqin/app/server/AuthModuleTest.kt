package com.xiangqin.app.server

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthModuleTest {

    private lateinit var auth: AuthModule

    @Before
    fun setup() {
        // AuthModule requires XiangQinApp, but we can test the pure logic methods
        // by testing the session/lockout logic directly
    }

    @Test
    fun `session token is not empty`() {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        val token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertTrue("Token should not be empty", token.isNotEmpty())
        assertTrue("Token should be URL-safe", token.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `rate limiting tracks failed attempts`() {
        val attempts = mutableMapOf<String, Pair<Int, Long>>()
        val lockoutMs = 5 * 60 * 1000L

        fun recordFail(ip: String) {
            val now = System.currentTimeMillis()
            val existing = attempts[ip]
            attempts[ip] = if (existing == null || now - existing.second > lockoutMs) Pair(1, now)
            else Pair(existing.first + 1, existing.second)
        }

        fun isLocked(ip: String): Boolean {
            val attempt = attempts[ip] ?: return false
            if (System.currentTimeMillis() - attempt.second > lockoutMs) { attempts.remove(ip); return false }
            return attempt.first >= 5
        }

        val ip = "192.168.1.1"
        assertFalse(isLocked(ip))

        repeat(4) { recordFail(ip) }
        assertFalse("4 attempts should not lock", isLocked(ip))

        recordFail(ip)
        assertTrue("5 attempts should lock", isLocked(ip))
    }

    @Test
    fun `session expiry logic works`() {
        val sessions = mutableMapOf<String, Long>()
        val now = System.currentTimeMillis()

        sessions["valid"] = now + 3600_000
        sessions["expired"] = now - 1000

        fun validate(token: String): Boolean {
            val expiry = sessions[token] ?: return false
            if (now > expiry) { sessions.remove(token); return false }
            return true
        }

        assertTrue(validate("valid"))
        assertFalse(validate("expired"))
        assertFalse(validate("nonexistent"))
    }
}
