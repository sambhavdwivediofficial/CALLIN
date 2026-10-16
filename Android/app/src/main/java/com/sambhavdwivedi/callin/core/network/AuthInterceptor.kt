package com.sambhavdwivedi.callin.core.network

import com.sambhavdwivedi.callin.data.remote.AuthApi
import com.sambhavdwivedi.callin.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val refreshApi: Lazy<AuthApi>,
) : Interceptor {

    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = runBlocking { tokenStore.getAccessToken() }

        val requestWithAuth = original.newBuilder().apply {
            if (!token.isNullOrBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()

        val response = chain.proceed(requestWithAuth)

        if (response.code != 401 || token.isNullOrBlank()) {
            return response
        }

        val newAccessToken = runBlocking {
            refreshMutex.withLock {
                val currentToken = tokenStore.getAccessToken()
                if (currentToken != token) {
                    return@withLock currentToken
                }

                val refreshToken = tokenStore.getRefreshToken() ?: return@withLock null
                runCatching { refreshApi.value.refresh(RefreshRequest(refreshToken)) }
                    .onSuccess { tokenStore.saveTokens(it.access_token, it.refresh_token) }
                    .onFailure { tokenStore.clear() }
                    .getOrNull()
                    ?.access_token
            }
        }

        if (newAccessToken.isNullOrBlank()) {
            return response
        }

        response.close()
        val retryRequest = original.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .build()
        return chain.proceed(retryRequest)
    }
}
