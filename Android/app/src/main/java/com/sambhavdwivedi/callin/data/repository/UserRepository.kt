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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Wraps the user API and keeps a simple in-memory cache of the
 * caller's own profile in [me].
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

    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String, fileName: String): Result<String> =
        runCatching {
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("avatar", fileName, body)
            val url = api.uploadAvatar(part).avatar_url
            _me.update { current -> current?.copy(avatar_url = url) }
            url
        }

    suspend fun registerDevice(token: String): Result<Unit> =
        runCatching {
            val response = api.registerDevice(RegisterDeviceRequest(token))
            if (!response.isSuccessful) error("Could not register device (${response.code()})")
        }

    private suspend fun fetchFcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }

    /**
     * Fetches this install's current FCM token and registers it,
     * retrying a few times with backoff if either the token fetch or
     * the network call fails. This used to be a single fire-and-
     * forget attempt with no retry — if it failed even once (e.g. no
     * network at the exact moment the app launched), the backend
     * would never receive a valid token for this device until
     * something else happened to trigger registration again, leaving
     * that device completely unreachable by push in the meantime.
     *
     * Safe and cheap to call repeatedly — the backend's upsert is
     * idempotent for an unchanged token, so calling this on every
     * app resume (see MainActivity.onResume) is what makes a device
     * self-heal from a failed registration without needing a
     * reinstall or a fresh login.
     */
    fun registerDeviceToken() {
        scope.launch {
            var delayMs = 2000L
            repeat(3) { attempt ->
                val token = fetchFcmToken()
                if (token != null) {
                    val result = registerDevice(token)
                    if (result.isSuccess) return@launch
                }
                if (attempt < 2) delay(delayMs)
                delayMs *= 2
            }
        }
    }
}
