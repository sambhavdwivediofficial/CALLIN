package com.sambhavdwivedi.callin.core.network

import android.util.Log
import com.sambhavdwivedi.callin.core.signaling.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * One persistent WebSocket connection to the backend's /ws endpoint,
 * carrying call signaling (invite/accept/reject/cancel/end) and
 * WebRTC negotiation (offer/answer/ICE). This is the single source
 * of truth for connection state — CallRepository consumes
 * [incoming] and calls [send]; nothing else touches the socket.
 *
 * Reconnects automatically with capped exponential backoff whenever
 * the connection drops (network change, backend restart, app
 * foregrounded again). A ping every 25s keeps NAT/firewall
 * connections alive between actual signaling traffic.
 */
class SignalingClient(private val tokenStore: TokenStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var reconnectAttempt = 0
    private var shouldRun = false

    private val _incoming = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<SignalingMessage> = _incoming

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState

    fun start() {
        if (shouldRun) return
        shouldRun = true
        scope.launch { connect() }
    }

    fun stop() {
        shouldRun = false
        socket?.close(1000, "client stopping")
        socket = null
    }

    private suspend fun connect() {
        val token = tokenStore.getAccessToken() ?: return
        val wsUrl = ApiConfig.BASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws?token=$token"

        val request = Request.Builder().url(wsUrl).build()

        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                reconnectAttempt = 0
                _connectionState.tryEmit(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString(SignalingMessage.serializer(), text) }
                    .onSuccess { _incoming.tryEmit(it) }
                    .onFailure { Log.w("SignalingClient", "bad message: $text", it) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.tryEmit(false)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                _connectionState.tryEmit(false)
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldRun) return
        reconnectAttempt++
        val delayMs = minOf(30_000L, 1000L * (1 shl minOf(reconnectAttempt, 5)))
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (shouldRun) connect()
        }
    }

    fun send(message: SignalingMessage): Boolean {
        val text = json.encodeToString(SignalingMessage.serializer(), message)
        return socket?.send(text) ?: false
    }
}
