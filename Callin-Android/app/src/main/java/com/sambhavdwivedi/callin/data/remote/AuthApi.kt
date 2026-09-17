package com.sambhavdwivedi.callin.data.remote

import com.sambhavdwivedi.callin.data.remote.dto.AuthResponse
import com.sambhavdwivedi.callin.data.remote.dto.GoogleSignInRequest
import com.sambhavdwivedi.callin.data.remote.dto.LoginRequest
import com.sambhavdwivedi.callin.data.remote.dto.RefreshRequest
import com.sambhavdwivedi.callin.data.remote.dto.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse

    @POST("api/v1/auth/google")
    suspend fun googleSignIn(@Body request: GoogleSignInRequest): AuthResponse
}
