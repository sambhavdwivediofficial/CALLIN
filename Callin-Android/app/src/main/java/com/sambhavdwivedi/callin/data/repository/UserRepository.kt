package com.sambhavdwivedi.callin.data.repository

import com.sambhavdwivedi.callin.data.remote.UserApi
import com.sambhavdwivedi.callin.data.remote.dto.CompleteProfileRequest
import com.sambhavdwivedi.callin.data.remote.dto.MeDto
import com.sambhavdwivedi.callin.data.remote.dto.PublicUserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Wraps the user API and keeps a simple in-memory cache of the
 * caller's own profile in [me]. AppContainer holds one instance of
 * this repository for the app process, so every screen that reads
 * [me] shows the last-known profile instantly — no spinner — on
 * every visit after the first, while getMe() keeps it current in
 * the background each time it's called.
 */
class UserRepository(private val api: UserApi) {

    private val _me = MutableStateFlow<MeDto?>(null)
    val me: StateFlow<MeDto?> = _me.asStateFlow()

    suspend fun getMe(): Result<MeDto> =
        runCatching { api.me() }.onSuccess { _me.value = it }

    suspend fun listUsers(): Result<List<PublicUserDto>> = runCatching { api.list().users }

    suspend fun completeProfile(username: String, firstName: String, lastName: String): Result<MeDto> =
        runCatching { api.completeProfile(CompleteProfileRequest(username, firstName, lastName)) }
            .onSuccess { _me.value = it }

    suspend fun checkUsername(username: String): Result<Boolean> =
        runCatching { api.checkUsername(username).available }

    /** Uploads raw image bytes as the caller's avatar and returns the new public URL. */
    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String, fileName: String): Result<String> =
        runCatching {
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("avatar", fileName, body)
            val url = api.uploadAvatar(part).avatar_url
            _me.update { current -> current?.copy(avatar_url = url) }
            url
        }
}
