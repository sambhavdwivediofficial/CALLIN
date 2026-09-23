package com.sambhavdwivedi.callin.data.repository

import com.sambhavdwivedi.callin.data.remote.ConnectionApi
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionDto
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionRequestDto
import com.sambhavdwivedi.callin.data.remote.dto.RespondConnectionRequest
import com.sambhavdwivedi.callin.data.remote.dto.SendConnectionRequest

/**
 * Wraps the connection-request flow: sending a request (what a QR
 * scan resolves to), listing requests waiting on the caller,
 * responding to one, and listing accepted connections.
 */
class ConnectionRepository(private val api: ConnectionApi) {

    /** Returns the resulting status: "pending" or "accepted" (if the other side already requested you). */
    suspend fun sendRequest(username: String): Result<String> =
        runCatching { api.sendRequest(SendConnectionRequest(username)).status }

    suspend fun pending(): Result<List<ConnectionRequestDto>> =
        runCatching { api.pending().requests }

    suspend fun respond(requestId: String, accept: Boolean): Result<Unit> =
        runCatching {
            val response = api.respond(requestId, RespondConnectionRequest(accept))
            if (!response.isSuccessful) error("Could not respond to request (${response.code()})")
        }

    suspend fun listConnections(): Result<List<ConnectionDto>> =
        runCatching { api.list().connections }
}
