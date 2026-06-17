package com.xiangqin.app.server

import com.xiangqin.app.XiangQinApp
import io.ktor.websocket.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.Frame

internal fun Route.webSocketRoutes(app: XiangQinApp, auth: AuthModule) {
    webSocket("/ws") {
        var authenticated = false
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text.startsWith("auth:")) {
                        if (auth.authenticateWs(frame, this)) {
                            authenticated = true
                            EventBroadcaster.addClient(this)
                            EventBroadcaster.broadcast("client_count", """{"count":${EventBroadcaster.clientCount}}""")
                        } else {
                            close()
                            return@webSocket
                        }
                    } else if (authenticated && text == "ping") {
                        send(Frame.Text("""{"type":"pong"}"""))
                    }
                } else if (frame is Frame.Close) { break }
            }
        } catch (_: Exception) {
        } finally { EventBroadcaster.removeClient(this) }
    }
}
