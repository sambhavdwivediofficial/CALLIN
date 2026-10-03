package com.sambhavdwivedi.callin.core.network

import android.util.Log
import com.sambhavdwivedi.callin.core.signaling.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * One persistent WebSocket connection to the backend's /ws endpoint.
 *
 * [send] queues the message if the socket isn't open yet (first
 * launch, or mid-reconnect after a drop) instead of silently losing
 * it, and flushes the queue the instant the connection opens. This
 * is what makes "open the app and immediately tap call" reliable
 * even before the handshake has finished.
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

    private val outboxMutex = Mutex()
    private val pendingOutbox = mutableListOf<String>()

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
        scope.launch { outboxMutex.withLock { pendingOutbox.clear() } }
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
                flushOutbox(webSocket)
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

    private fun flushOutbox(webSocket: WebSocket) {
        scope.launch {
            val toSend = outboxMutex.withLock {
                val copy = pendingOutbox.toList()
                pendingOutbox.clear()
                copy
            }
            toSend.forEach { webSocket.send(it) }
        }
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

    /** Sends over the current socket if one is open; otherwise
     * queues the message and sends it the instant the connection
     * opens. Also self-starts the client if [start] was never
     * called, so a signaling call made before login-flow wiring
     * still gets through. */
    fun send(message: SignalingMessage): Boolean {
        val text = json.encodeToString(SignalingMessage.serializer(), message)
        val current = socket
        if (current != null && current.send(text)) return true

        scope.launch { outboxMutex.withLock { pendingOutbox.add(text) } }
        if (!shouldRun) start()
        return true
    }
}
