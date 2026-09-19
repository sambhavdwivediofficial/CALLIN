package com.sambhavdwivedi.callin.data.repository

import com.sambhavdwivedi.callin.data.remote.UserApi
import com.sambhavdwivedi.callin.data.remote.dto.CompleteProfileRequest
import com.sambhavdwivedi.callin.data.remote.dto.MeDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class UserRepository(private val api: UserApi) {
    suspend fun getMe(): Result<MeDto> = runCatching { api.me() }

    suspend fun completeProfile(username: String, firstName: String, lastName: String): Result<MeDto> =
        runCatching { api.completeProfile(CompleteProfileRequest(username, firstName, lastName)) }

    suspend fun checkUsername(username: String): Result<Boolean> =
        runCatching { api.checkUsername(username).available }

    /** Uploads raw image bytes as the caller's avatar and returns the new public URL. */
    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String, fileName: String): Result<String> =
        runCatching {
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("avatar", fileName, body)
            api.uploadAvatar(part).avatar_url
        }
}
