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
data class AuthResponse(
    val user: MeDto,
    val access_token: String,
    val refresh_token: String
)
