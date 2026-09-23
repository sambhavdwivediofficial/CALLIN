package com.sambhavdwivedi.callin.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SendConnectionRequest(
    val username: String
)

@Serializable
data class ConnectionStatusResponse(
    val status: String
)

@Serializable
data class ConnectionRequestDto(
    val id: String,
    val from_user_id: String,
    val from_username: String,
    val from_display_name: String? = null,
    val from_avatar_url: String? = null,
    val created_at: String
)

@Serializable
data class PendingRequestsResponse(
    val requests: List<ConnectionRequestDto> = emptyList()
)

@Serializable
data class RespondConnectionRequest(
    val accept: Boolean
)

@Serializable
data class ConnectionDto(
    val user_id: String,
    val username: String,
    val display_name: String? = null,
    val avatar_url: String? = null,
    val connected_at: String
)

@Serializable
data class ConnectionsListResponse(
    val connections: List<ConnectionDto> = emptyList()
)
