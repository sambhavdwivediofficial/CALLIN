package com.sambhavdwivedi.callin.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Purely on-device call history. Any entry older than exactly 30
 * days is dropped every time [getAll] runs — on app launch and
 * whenever Recents is opened — so nothing lingers past its window,
 * matching a normal phone's call log. Capped at 200 entries total. */
class RecentCallsStore(private val context: Context) {
    private val key = stringPreferencesKey("entries_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val maxEntries = 200
    private val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000

    private val _entries = MutableStateFlow<List<RecentCallEntry>?>(null)
    val entries: StateFlow<List<RecentCallEntry>?> = _entries.asStateFlow()

    suspend fun getAll(): List<RecentCallEntry> {
        val raw = context.recentCallsDataStore.data.first()[key]
        val all = if (raw == null) emptyList() else runCatching { json.decodeFromString<List<RecentCallEntry>>(raw) }.getOrDefault(emptyList())

        val cutoff = System.currentTimeMillis() - thirtyDaysMillis
        val fresh = all.filter { it.timestampMillis >= cutoff }
        if (fresh.size != all.size) persist(fresh)

        _entries.value = fresh
        return fresh
    }

    suspend fun record(entry: RecentCallEntry) {
        val updated = (listOf(entry) + getAll()).take(maxEntries)
        persist(updated)
        _entries.value = updated
    }

    private suspend fun persist(list: List<RecentCallEntry>) {
        context.recentCallsDataStore.edit { it[key] = json.encodeToString(list) }
    }
}
