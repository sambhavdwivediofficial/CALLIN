package com.sambhavdwivedi.callin.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CompleteProfileRequest(
    val username: String,
    val first_name: String,
    val last_name: String
)

@Serializable
data class MeDto(
    val id: String,
    val username: String? = null,
    val email: String,
    val display_name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar_url: String? = null,
    val profile_completed: Boolean = false
)

@Serializable
data class CheckUsernameResponse(
    val available: Boolean
)

@Serializable
data class AvatarResponse(
    val avatar_url: String
)

@Serializable
data class PublicUserDto(
    val id: String,
    val username: String? = null,
    val display_name: String? = null,
    val avatar_url: String? = null
)

@Serializable
data class UsersListResponse(
    val users: List<PublicUserDto>
)
