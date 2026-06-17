package com.xiangqin.app.server

import android.content.Context
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
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
        val port = 8080

        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) { json(json) }
            install(WebSockets)
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val safeMsg = escapedJson(cause.message ?: "unknown")
                    call.respondText(
                        """{"error":"$safeMsg"}""",
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
