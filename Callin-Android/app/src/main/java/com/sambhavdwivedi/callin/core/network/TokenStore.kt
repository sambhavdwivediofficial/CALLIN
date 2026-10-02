package com.sambhavdwivedi.callin.core.network

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.longPreferencesKey

private val Context.authDataStore by preferencesDataStore(name = "auth")

class TokenStore(private val context: Context) {

    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val profileCompletedKey = booleanPreferencesKey("profile_completed")
    private val permissionsRequestedKey = booleanPreferencesKey("permissions_requested")
    private val lastPermissionCheckKey = longPreferencesKey("last_permission_check_millis")

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

    /** Whether the one-time, first-run permission sequence (mic,
     * notifications, camera) has already been shown to this user —
     * so it never runs a second time even across app restarts. */
    suspend fun getPermissionsRequested(): Boolean =
        context.authDataStore.data.first()[permissionsRequestedKey] ?: false

    suspend fun setPermissionsRequested(done: Boolean) {
        context.authDataStore.edit { prefs -> prefs[permissionsRequestedKey] = done }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }

    suspend fun getLastPermissionCheckMillis(): Long =
        context.authDataStore.data.first()[lastPermissionCheckKey] ?: 0L
    
    suspend fun setLastPermissionCheckMillis(millis: Long) {
        context.authDataStore.edit { prefs -> prefs[lastPermissionCheckKey] = millis }
    }
}
