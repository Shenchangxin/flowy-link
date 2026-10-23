package com.flowy.link

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class LinkStream(
    private val context: Context,
    private val session: HostSession,
    private val conversationId: String,
    private val onText: (String) -> Unit,
    private val onPhase: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val pieces = linkedMapOf<String, StringBuilder>()
    private var socket: WebSocket? = null

    fun start() {
        val address = session.addresses.firstOrNull() ?: return
        val request = Request.Builder()
            .url("wss://$address:${session.port}/ws")
            .header("Authorization", "Bearer ${session.accessToken}")
            .build()
        socket = pinnedClient(session.fingerprint).newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val root = runCatching { JSONObject(text) }.getOrNull() ?: return
                val data = root.optJSONObject("data") ?: return
                if (data.optString("conversation_id") != conversationId) return
                when (root.optString("name")) {
                    "message.stream" -> {
                        if (data.optString("type") != "content") return
                        val id = data.optString("msg_id").ifBlank { "live" }
                        val buffer = pieces.getOrPut(id) { StringBuilder() }
                        if (data.optBoolean("replace")) buffer.clear()
                        buffer.append(displayText(data.opt("data")))
                        val latest = buffer.toString().trim()
                        if (latest.isNotEmpty()) main.post { onText(latest) }
                    }
                    "turn.completed", "turn.failed" -> {
                        val phase = classifyPhase(root.optString("name"), data)
                        main.post {
                            onPhase(phase)
                            LinkNotifier.showIfBackground(context, phase)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = Unit
        })
    }

    fun stop() {
        socket?.close(1000, null)
        socket = null
    }
}

private fun classifyPhase(name: String, data: JSONObject): String {
    val waiting = data.optJSONObject("runtime")?.optString("state") == "waiting_confirmation"
    return when {
        waiting -> "confirmation.pending"
        name == "turn.failed" -> "turn.failed"
        else -> "turn.completed"
    }
}
