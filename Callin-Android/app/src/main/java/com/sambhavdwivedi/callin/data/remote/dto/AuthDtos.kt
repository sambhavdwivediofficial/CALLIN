package com.sambhavdwivedi.callin.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val display_name: String
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refresh_token: String
)

@Serializable
data class GoogleSignInRequest(
    val id_token: String
)

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
data class AuthResponse(
    val user: MeDto,
    val access_token: String,
    val refresh_token: String
)
