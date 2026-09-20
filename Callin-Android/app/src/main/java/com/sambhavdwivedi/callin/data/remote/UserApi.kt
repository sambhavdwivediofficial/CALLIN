package com.sambhavdwivedi.callin.data.remote

import com.sambhavdwivedi.callin.data.remote.dto.AvatarResponse
import com.sambhavdwivedi.callin.data.remote.dto.CheckUsernameResponse
import com.sambhavdwivedi.callin.data.remote.dto.CompleteProfileRequest
import com.sambhavdwivedi.callin.data.remote.dto.MeDto
import com.sambhavdwivedi.callin.data.remote.dto.UsersListResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface UserApi {
    @GET("api/v1/users/me")
    suspend fun me(): MeDto

    @GET("api/v1/users")
    suspend fun list(): UsersListResponse

    @POST("api/v1/users/me/complete-profile")
    suspend fun completeProfile(@Body request: CompleteProfileRequest): MeDto

    @GET("api/v1/users/check-username")
    suspend fun checkUsername(@Query("username") username: String): CheckUsernameResponse

    @Multipart
    @POST("api/v1/users/me/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): AvatarResponse
}
