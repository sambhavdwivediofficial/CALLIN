package com.sambhavdwivedi.callin.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import com.sambhavdwivedi.callin.data.remote.UserApi
import com.sambhavdwivedi.callin.data.remote.dto.CompleteProfileRequest
import com.sambhavdwivedi.callin.data.remote.dto.MeDto
import com.sambhavdwivedi.callin.data.remote.dto.PublicUserDto
import com.sambhavdwivedi.callin.data.remote.dto.RegisterDeviceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /** Sends a specific FCM token to the backend — called with a
     * token we already have in hand (e.g. from
     * CallinFirebaseMessagingService.onNewToken). */
    suspend fun registerDevice(token: String): Result<Unit> =
        runCatching {
            val response = api.registerDevice(RegisterDeviceRequest(token))
            if (!response.isSuccessful) error("Could not register device (${response.code()})")
        }

    /** Fire-and-forget: fetches this install's current FCM token and
     * registers it. Call once right after login/profile-completion so
     * a device that already had a token before ever logging in still
     * gets registered — onNewToken alone wouldn't catch that case,
     * since Firebase only fires it when the token is first minted or
     * rotated, not on every app start. */
    fun registerDeviceToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            scope.launch { registerDevice(token) }
        }
    }
}
