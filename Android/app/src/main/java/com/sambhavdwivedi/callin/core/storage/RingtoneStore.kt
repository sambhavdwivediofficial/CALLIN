package com.sambhavdwivedi.callin.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.ringtoneDataStore by preferencesDataStore(name = "ringtone_prefs")

/** Persists which ringtone asset file the user has chosen for
 * incoming calls. Survives app restarts; once set, that is the
 * account's ringtone until changed again — no re-picking needed. */
class RingtoneStore(private val context: Context) {
    private val key = stringPreferencesKey("selected_ringtone_file")

    suspend fun getSelected(): String? =
        context.ringtoneDataStore.data.first()[key]

    suspend fun setSelected(fileName: String) {
        context.ringtoneDataStore.edit { it[key] = fileName }
    }
}
