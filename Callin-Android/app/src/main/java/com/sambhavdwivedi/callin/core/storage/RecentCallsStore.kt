package com.sambhavdwivedi.callin.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.recentCallsDataStore by preferencesDataStore(name = "recent_calls")

enum class CallDirection { INCOMING, OUTGOING }

@Serializable
data class RecentCallEntry(
    val peerId: String,
    val peerUsername: String,
    val peerDisplayName: String?,
    val peerAvatarUrl: String?,
    val direction: CallDirection,
    val timestampMillis: Long,
    val durationSeconds: Long,
    val missed: Boolean,
)

/** Purely on-device call history — the backend never sees who
 * called whom, matching a normal phone's local call log. Capped at
 * the most recent 200 entries so it never grows unbounded. */
class RecentCallsStore(private val context: Context) {
    private val key = stringPreferencesKey("entries_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val maxEntries = 200

    suspend fun getAll(): List<RecentCallEntry> {
        val raw = context.recentCallsDataStore.data.first()[key] ?: return emptyList()
        return runCatching { json.decodeFromString<List<RecentCallEntry>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun record(entry: RecentCallEntry) {
        val updated = (listOf(entry) + getAll()).take(maxEntries)
        context.recentCallsDataStore.edit { it[key] = json.encodeToString(updated) }
    }
}
