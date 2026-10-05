package com.sambhavdwivedi.callin.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.sambhavdwivedi.callin.core.signaling.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * CRITICAL FIX: [socket] used to never be reset to null when the
 * connection closed or failed (onClosed/onFailure only emitted a
 * connectionState event, never cleared the field). That meant the
 * client could believe it was still connected for up to the full
 * reconnect backoff window (up to 15-30s) after a connection had
 * actually died — [send] would try writing to a dead WebSocket
 * object and silently get nothing back, and any "am I connected"
 * check elsewhere would see a non-null reference and wrongly assume
 * everything was fine. This is very likely the real reason a call
 * could arrive while the app was genuinely open yet nothing happened
 * on the receiving device: the socket LOOKED alive to this client,
 * but the backend had already dropped it, and by the time the
 * client's own ping cycle noticed, several seconds had already
 * passed — exactly the kind of gap where a single incoming call
 * event can be missed entirely.
 *
 * [ensureConnected] is new: it forces an immediate reconnect attempt,
 * bypassing any pending backoff delay. It's called whenever there's
 * a fresh reason to believe connectivity just became available —
 * from a system [ConnectivityManager] callback when the OS reports a
 * network coming up, and from the app's Activity whenever it resumes
 * to the foreground (see MainActivity.onResume).
 */
class SignalingClient(
    context: Context,
    private val tokenStore: TokenStore,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var shouldRun = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val outboxMutex = Mutex()
    private val pendingOutbox = mutableListOf<String>()

    private val _incoming = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<SignalingMessage> = _incoming

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState

    fun start() {
        if (shouldRun) return
        shouldRun = true
        registerNetworkCallback()
        scope.launch { connect() }
    }

    fun stop() {
        shouldRun = false
        reconnectJob?.cancel()
        unregisterNetworkCallback()
        socket?.close(1000, "client stopping")
        socket = null
        scope.launch { outboxMutex.withLock { pendingOutbox.clear() } }
    }

    /**
     * Forces an immediate reconnect attempt if there's no currently
     * live socket, skipping any pending exponential-backoff wait.
     * Safe to call liberally — it's a no-op if a connection is
     * already open.
     */
    fun ensureConnected() {
        if (!shouldRun) {
            start()
            return
        }
        if (socket != null) return
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        scope.launch { connect() }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ensureConnected()
            }
        }
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { Log.w("SignalingClient", "registerNetworkCallback failed", it) }
    }

    private fun unregisterNetworkCallback() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val cb = networkCallback
        if (cb != null) {
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
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
                if (socket === webSocket) socket = null
                _connectionState.tryEmit(false)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                if (socket === webSocket) socket = null
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
        val delayMs = minOf(15_000L, 1000L * (1 shl minOf(reconnectAttempt, 4)))
        reconnectJob = scope.launch {
            delay(delayMs)
            if (shouldRun && socket == null) connect()
        }
    }

    /** Sends over the current socket if one is open; otherwise
     * queues the message and triggers an immediate reconnect attempt
     * (not a backoff-delayed one) so a call placed right after a
     * drop doesn't sit waiting. */
    fun send(message: SignalingMessage): Boolean {
        val text = json.encodeToString(SignalingMessage.serializer(), message)
        val current = socket
        if (current != null && current.send(text)) return true

        scope.launch { outboxMutex.withLock { pendingOutbox.add(text) } }
        ensureConnected()
        return true
    }
}
