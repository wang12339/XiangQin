package com.xiangqin.app.server

import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.service.MonitoringService
import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.alertRoutes(app: XiangQinApp, context: Context, auth: AuthModule) {
    get("/api/alerts") {
        if (!auth.checkAuth(call)) return@get
        val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 1
        val since = System.currentTimeMillis() - days * 86400000L
        val alerts = app.database.alertDao().getRecent(since)
        val count = app.database.alertDao().countSince(since)
        val unacknowledged = alerts.count { !it.acknowledged }
        call.respond(AlertListResponse(alerts, count, unacknowledged))
    }
    post("/api/alerts/acknowledge/{id}") {
        if (!auth.checkAuth(call)) return@post
        val id = call.parameters["id"]?.toLongOrNull() ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id")); return@post
        }
        app.database.alertDao().acknowledge(id)
        call.respond(MessageResponse("acknowledged"))
    }
    post("/api/alerts/acknowledge-all") {
        if (!auth.checkAuth(call)) return@post
        val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
        val since = System.currentTimeMillis() - days * 86400000L
        val alerts = app.database.alertDao().getRecent(since)
        for (alert in alerts.filter { !it.acknowledged }) {
            app.database.alertDao().acknowledge(alert.id)
        }
        call.respond(MessageResponse("all acknowledged"))
    }
    get("/api/alerts/settings") {
        if (!auth.checkAuth(call)) return@get
        val settings = app.dataStore.getAllAlertSettings()
        val homeZone = app.dataStore.getHomeZone()
        val webhook = app.dataStore.getFeishuWebhook()
        call.respond(AlertSettingsResponse(enabled = settings, home = homeZone, feishuWebhook = webhook))
    }
    post("/api/alerts/settings") {
        if (!auth.checkAuth(call)) return@post
        try {
            val body = call.receive<AlertSettingsUpdateRequest>()
            if (body.enabled != null) app.dataStore.setAllAlertSettings(body.enabled)
            if (body.home != null) app.dataStore.setHomeZone(body.home)
            if (body.feishuWebhook != null) app.dataStore.setFeishuWebhook(body.feishuWebhook)
            call.respond(MessageResponse("settings updated"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid body")))
        }
    }
    post("/api/alerts/test-push") {
        if (!auth.checkAuth(call)) return@post
        val success = com.xiangqin.app.monitor.AlertPushManager.sendTestMessage()
        if (success) call.respond(MessageResponse("test message sent"))
        else call.respond(HttpStatusCode.BadRequest, mapOf("error" to "推送失败，请检查飞书 Webhook URL 是否配置正确"))
    }
    delete("/api/alerts") {
        if (!auth.checkAuth(call)) return@delete
        app.database.alertDao().deleteAll()
        call.respond(MessageResponse("all alerts cleared"))
    }
}
