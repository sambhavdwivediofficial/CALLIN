package com.sambhavdwivedi.callin.data.repository

import com.sambhavdwivedi.callin.core.network.TokenStore
import com.sambhavdwivedi.callin.data.remote.AuthApi
import com.sambhavdwivedi.callin.data.remote.dto.GoogleSignInRequest
import com.sambhavdwivedi.callin.data.remote.dto.LoginRequest
import com.sambhavdwivedi.callin.data.remote.dto.RefreshRequest
import com.sambhavdwivedi.callin.data.remote.dto.RegisterRequest

/**
 * Wraps the auth API and keeps TokenStore in sync with every
 * successful auth response, so the rest of the app only ever needs
 * to ask "am I logged in" and never touches tokens directly.
 */
class AuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore
) {
    suspend fun register(username: String, email: String, password: String, displayName: String): Result<Unit> =
        runCatching {
            val response = api.register(RegisterRequest(username, email, password, displayName))
            tokenStore.saveTokens(response.access_token, response.refresh_token)
        }

    suspend fun login(identifier: String, password: String): Result<Unit> =
        runCatching {
            val response = api.login(LoginRequest(identifier, password))
            tokenStore.saveTokens(response.access_token, response.refresh_token)
        }

    /** Returns true if this account still needs to complete its profile. */
    suspend fun googleSignIn(idToken: String): Result<Boolean> = runCatching {
        val response = api.googleSignIn(GoogleSignInRequest(idToken))
        tokenStore.saveTokens(response.access_token, response.refresh_token)
        !response.user.profile_completed
    }

    suspend fun refresh(): Result<Unit> = runCatching {
        val refreshToken = tokenStore.getRefreshToken() ?: error("no refresh token stored")
        val response = api.refresh(RefreshRequest(refreshToken))
        tokenStore.saveTokens(response.access_token, response.refresh_token)
    }

    suspend fun logout() {
        tokenStore.clear()
    }

    suspend fun isLoggedIn(): Boolean = tokenStore.getAccessToken() != null
}
