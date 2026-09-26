package com.sambhavdwivedi.callin.data.repository

import com.sambhavdwivedi.callin.core.storage.NotificationHistoryEntry
import com.sambhavdwivedi.callin.core.storage.NotificationHistoryStore
import com.sambhavdwivedi.callin.data.remote.ConnectionApi
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionDto
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionRequestDto
import com.sambhavdwivedi.callin.data.remote.dto.RespondConnectionRequest
import com.sambhavdwivedi.callin.data.remote.dto.SendConnectionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConnectionRepository(
    private val api: ConnectionApi,
    private val historyStore: NotificationHistoryStore
) {
    private val _connections = MutableStateFlow<List<ConnectionDto>?>(null)
    val connections: StateFlow<List<ConnectionDto>?> = _connections.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<ConnectionRequestDto>?>(null)
    val pendingRequests: StateFlow<List<ConnectionRequestDto>?> = _pendingRequests.asStateFlow()

    private val _history = MutableStateFlow<List<NotificationHistoryEntry>>(emptyList())
    val history: StateFlow<List<NotificationHistoryEntry>> = _history.asStateFlow()

    private val pendingMutex = Mutex()

    suspend fun refreshHistory() {
        _history.value = historyStore.getAll()
    }

    suspend fun clearHistory() {
        historyStore.clearAll()
        _history.value = emptyList()
    }

    suspend fun sendRequest(username: String): Result<String> =
        runCatching { api.sendRequest(SendConnectionRequest(username)).status }
            .onSuccess { status -> if (status == "accepted") refreshConnections() }

    suspend fun refreshConnections(): Result<List<ConnectionDto>> =
        runCatching { api.list().connections }
            .onSuccess { _connections.value = it }

    suspend fun refreshPending(): Result<List<ConnectionRequestDto>> = pendingMutex.withLock {
        runCatching { api.pending().requests }
            .onSuccess { _pendingRequests.value = it }
    }

    suspend fun respond(request: ConnectionRequestDto, accept: Boolean): Result<Unit> = pendingMutex.withLock {
        _pendingRequests.update { current -> current?.filterNot { it.id == request.id } }

        val result = runCatching {
            val response = api.respond(request.id, RespondConnectionRequest(accept))
            if (!response.isSuccessful) error("Could not respond to request (${response.code()})")
        }

        result
            .onSuccess {
                if (accept) refreshConnections()
                historyStore.record(
                    NotificationHistoryEntry(
                        requestId = request.id,
                        fromUserId = request.from_user_id,
                        fromUsername = request.from_username,
                        fromDisplayName = request.from_display_name,
                        fromAvatarUrl = request.from_avatar_url,
                        accepted = accept,
                        respondedAtMillis = System.currentTimeMillis()
                    )
                )
                _history.value = historyStore.getAll()
            }
            .onFailure {
                _pendingRequests.update { current -> (current ?: emptyList()) + request }
            }

        result
    }
}
