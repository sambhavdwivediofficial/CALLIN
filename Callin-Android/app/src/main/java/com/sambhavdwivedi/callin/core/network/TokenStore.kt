package com.sambhavdwivedi.callin.core.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * Persists the current access/refresh token pair on-device using
 * Jetpack DataStore. This is the single source of truth for whether
 * the user is logged in.
 */
class TokenStore(private val context: Context) {

    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.authDataStore.edit { prefs ->
            prefs[accessTokenKey] = accessToken
            prefs[refreshTokenKey] = refreshToken
        }
    }

    suspend fun getAccessToken(): String? =
        context.authDataStore.data.first()[accessTokenKey]

    suspend fun getRefreshToken(): String? =
        context.authDataStore.data.first()[refreshTokenKey]

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }
}
