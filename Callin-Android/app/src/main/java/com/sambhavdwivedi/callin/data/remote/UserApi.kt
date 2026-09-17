package com.sambhavdwivedi.callin.data.remote

import com.sambhavdwivedi.callin.data.remote.dto.CompleteProfileRequest
import com.sambhavdwivedi.callin.data.remote.dto.MeDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface UserApi {
    @GET("api/v1/users/me")
    suspend fun me(): MeDto

    @POST("api/v1/users/me/complete-profile")
    suspend fun completeProfile(@Body request: CompleteProfileRequest): MeDto
}
