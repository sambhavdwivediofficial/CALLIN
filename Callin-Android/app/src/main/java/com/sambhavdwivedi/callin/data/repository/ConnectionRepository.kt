package com.sambhavdwivedi.callin.data.repository

import com.sambhavdwivedi.callin.data.remote.ConnectionApi
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionDto
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionRequestDto
import com.sambhavdwivedi.callin.data.remote.dto.RespondConnectionRequest
import com.sambhavdwivedi.callin.data.remote.dto.SendConnectionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Wraps the connection-request flow and keeps a simple in-memory
 * cache of the caller's accepted connections and pending incoming
 * requests. AppContainer holds one instance of this repository for
 * the whole app process, so every screen that reads [connections] or
 * [pendingRequests] shares the same cache: a screen that has been
 * visited once shows its data instantly on every later visit — no
 * loading spinner — while [refreshConnections] / [refreshPending]
 * keep it current in the background.
 *
 * [respond] updates the backend (accept/reject is persisted
 * server-side immediately) and this local cache in the same call —
 * accepting a request removes it from the pending cache and adds the
 * new contact to the connections cache, so Notifications and
 * Contacts both reflect it without either screen needing a manual
 * reload.
 */
class ConnectionRepository(private val api: ConnectionApi) {

    private val _connections = MutableStateFlow<List<ConnectionDto>?>(null)
    val connections: StateFlow<List<ConnectionDto>?> = _connections.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<ConnectionRequestDto>?>(null)
    val pendingRequests: StateFlow<List<ConnectionRequestDto>?> = _pendingRequests.asStateFlow()

    /** Returns the resulting status: "pending" or "accepted" (if the other side already requested you). */
    suspend fun sendRequest(username: String): Result<String> =
        runCatching { api.sendRequest(SendConnectionRequest(username)).status }
            .onSuccess { status ->
                // If the other person had already requested us, the
                // backend auto-accepts on the spot — refresh so the
                // new contact appears in Contacts immediately.
                if (status == "accepted") refreshConnections()
            }

    /** Fetches the caller's accepted connections from the server and updates the cache. */
    suspend fun refreshConnections(): Result<List<ConnectionDto>> =
        runCatching { api.list().connections }
            .onSuccess { _connections.value = it }

    /** Fetches requests waiting on the caller to respond and updates the cache. */
    suspend fun refreshPending(): Result<List<ConnectionRequestDto>> =
        runCatching { api.pending().requests }
            .onSuccess { _pendingRequests.value = it }

    suspend fun respond(requestId: String, accept: Boolean): Result<Unit> =
        runCatching {
            val response = api.respond(requestId, RespondConnectionRequest(accept))
            if (!response.isSuccessful) error("Could not respond to request (${response.code()})")
        }.onSuccess {
            _pendingRequests.update { current -> current?.filterNot { it.id == requestId } }
            if (accept) refreshConnections()
        }
}
