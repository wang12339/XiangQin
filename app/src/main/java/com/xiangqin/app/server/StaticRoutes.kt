package com.xiangqin.app.server

import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.service.MonitoringService
import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.staticRoutes(app: XiangQinApp, context: Context) {
    get("/health") {
        call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
    }
    get("/api/legal/privacy") {
        val content = readAsset(context, "privacy_policy.md") ?: "Privacy policy not found"
        call.respondText(content, ContentType.Text.Plain)
    }
    get("/api/legal/agreement") {
        val content = readAsset(context, "user_agreement.md") ?: "User agreement not found"
        call.respondText(content, ContentType.Text.Plain)
    }
    get("/") {
        val html = readAsset(context, "web/index.html")
        if (html != null) call.respondText(html, ContentType.Text.Html)
        else call.respondText("404 - Web UI not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
    }
    get("/{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: return@get
        if (path.endsWith(".map")) {
            call.respondText("404", ContentType.Text.Plain, HttpStatusCode.NotFound)
            return@get
        }
        val assetPath = "web/$path"
        val isBinary = assetPath.endsWith(".js") || assetPath.endsWith(".css") ||
                assetPath.endsWith(".png") || assetPath.endsWith(".jpg") ||
                assetPath.endsWith(".jpeg") || assetPath.endsWith(".gif") ||
                assetPath.endsWith(".svg") || assetPath.endsWith(".ico") ||
                assetPath.endsWith(".mp4") || assetPath.endsWith(".woff") ||
                assetPath.endsWith(".woff2") || assetPath.endsWith(".ttf")
        val contentType = resolveContentType(assetPath)
        if (isBinary) {
            val data = readAssetBytes(context, assetPath)
            if (data != null) call.respondBytes(data, contentType)
            else call.respondText("404", ContentType.Text.Plain, HttpStatusCode.NotFound)
        } else {
            val content = readAsset(context, assetPath)
            if (content != null) call.respondText(content, contentType)
            else call.respondText("404", ContentType.Text.Plain, HttpStatusCode.NotFound)
        }
    }
}

internal fun resolveContentType(path: String): ContentType = when {
    path.endsWith(".css") -> ContentType.Text.CSS
    path.endsWith(".js") -> ContentType.Application.JavaScript
    path.endsWith(".html") -> ContentType.Text.Html
    path.endsWith(".png") -> ContentType.Image.PNG
    path.endsWith(".svg") -> ContentType.Image.SVG
    path.endsWith(".ico") -> ContentType.Image.XIcon
    path.endsWith(".jpg") || path.endsWith(".jpeg") -> ContentType.Image.JPEG
    path.endsWith(".gif") -> ContentType.Image.GIF
    path.endsWith(".mp4") -> ContentType.Video.MP4
    else -> ContentType.Application.OctetStream
}

internal fun readAsset(context: Context, path: String): String? = try {
    context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
} catch (e: Exception) {
    android.util.Log.e("XiangQin/Web", "readAsset failed: $path", e)
    null
}

internal fun readAssetBytes(context: Context, path: String): ByteArray? = try {
    context.assets.open(path).use { it.readBytes() }
} catch (e: Exception) {
    android.util.Log.e("XiangQin/Web", "readAssetBytes failed: $path", e)
    null
}
