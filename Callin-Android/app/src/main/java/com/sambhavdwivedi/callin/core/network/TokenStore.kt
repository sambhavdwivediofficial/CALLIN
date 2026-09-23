package com.sambhavdwivedi.callin.core.network

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * Persists the current access/refresh token pair, plus a cached
 * profile_completed flag, on-device using Jetpack DataStore. This is
 * the single source of truth for whether the user is logged in — and,
 * since it's read synchronously with no network call, for exactly
 * where to send them (Login / CompleteProfile / Home) the instant the
 * app opens, regardless of connectivity.
 */
class TokenStore(private val context: Context) {

    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val profileCompletedKey = booleanPreferencesKey("profile_completed")

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.authDataStore.edit { prefs ->
            prefs[accessTokenKey] = accessToken
            prefs[refreshTokenKey] = refreshToken
        }
    }

    suspend fun setProfileCompleted(completed: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[profileCompletedKey] = completed
        }
    }

    suspend fun getAccessToken(): String? =
        context.authDataStore.data.first()[accessTokenKey]

    suspend fun getRefreshToken(): String? =
        context.authDataStore.data.first()[refreshTokenKey]

    suspend fun getProfileCompleted(): Boolean =
        context.authDataStore.data.first()[profileCompletedKey] ?: false

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }
}
