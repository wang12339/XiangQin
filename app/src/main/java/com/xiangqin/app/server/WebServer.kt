package com.xiangqin.app.server

import android.content.Context
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.service.MonitoringService
import kotlinx.serialization.json.Json

class WebServer(private val context: Context, private val service: MonitoringService) {

    private var server: ApplicationEngine? = null
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var auth: AuthModule

    suspend fun start() {
        System.setProperty("io.netty.noNativeTransport", "true")

        val app = XiangQinApp.instance
        auth = AuthModule(app)
        val port = app.dataStore.getWebPort()

        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) { json(json) }
            install(WebSockets)
            install(CORS) {
                allowHost("xiangqin-test.whanghui.top", schemes = listOf("https"))
                allowHost("xiangqin.whanghui.top", schemes = listOf("https"))
                allowHost("localhost")
                allowHost("127.0.0.1")
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowCredentials = true
                maxAgeInSeconds = 3600
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    android.util.Log.e("XiangQin/Web", "未处理异常", cause)
                    call.respondText(
                        """{"error":"服务器内部错误"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError
                    )
                }
            }
            intercept(ApplicationCallPipeline.Monitoring) {
                call.response.header("X-Content-Type-Options", "nosniff")
                call.response.header("X-Frame-Options", "DENY")
                call.response.header("X-XSS-Protection", "1; mode=block")
                call.response.header("Referrer-Policy", "strict-origin-when-cross-origin")
                call.response.header("Cache-Control", "no-store, no-cache, must-revalidate")
            }

            routing {
                post("/api/login") {
                    val clientIp = call.request.local.remoteAddress

                    // 检查是否被限流
                    if (auth.isLockedOut(clientIp)) {
                        call.respondText(
                            """{"error":"too many attempts, try again later"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.TooManyRequests
                        )
                        return@post
                    }

                    val body = call.receiveText()

                    // 验证请求体大小
                    if (body.length > 1024) {
                        call.respondText(
                            """{"error":"request too large"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.PayloadTooLarge
                        )
                        return@post
                    }

                    val pw = try {
                        val obj = org.json.JSONObject(body)
                        val password = obj.optString("password", "")
                        // 验证密码字段存在且非空
                        if (password.isEmpty()) {
                            call.respondText(
                                """{"error":"password is required"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.BadRequest
                            )
                            return@post
                        }
                        // 验证密码长度合理性（最大 128 字符）
                        if (password.length > 128) {
                            call.respondText(
                                """{"error":"password too long"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.BadRequest
                            )
                            return@post
                        }
                        password
                    } catch (e: Exception) {
                        android.util.Log.w("XiangQin/Web", "LOGIN parse error: ${e.message}")
                        call.respondText(
                            """{"error":"invalid request format"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    val correctPw = app.dataStore.getWebPassword()
                    if (java.security.MessageDigest.isEqual(pw.toByteArray(), correctPw.toByteArray())) {
                        auth.clearFailedLogin(clientIp)
                        val token = auth.createSession()
                        call.respondText(
                            """{"token":"${token.escapeJson()}","message":"ok"}""",
                            ContentType.Application.Json
                        )
                    } else {
                        auth.recordFailedLogin(clientIp)
                        call.respondText(
                            """{"error":"wrong password"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.Unauthorized
                        )
                    }
                }
                staticRoutes(app, context)
                dataRoutes(app, context, auth)
                deviceRoutes(app, context, service, auth)
                alertRoutes(app, context, auth)
                fileRoutes(app, context, auth)
                contactRoutes(context, auth)
                accountAndSystemRoutes(app, context, auth)
                exportRoutes(app, auth)
                webSocketRoutes(app, auth)
            }
        }
        server?.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}

internal fun escapedJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

internal fun String.escapeJson(): String = escapedJson(this)

internal fun jsonMap(vararg pairs: Pair<String, Any>): String {
    val entries = pairs.joinToString(",") { (k, v) ->
        val value = when (v) {
            is String -> "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            is Number -> v.toString()
            is Boolean -> v.toString()
            else -> "\"$v\""
        }
        "\"$k\": $value"
    }
    return "{$entries}"
}
