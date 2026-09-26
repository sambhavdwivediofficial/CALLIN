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

private val Context.notificationHistoryDataStore by preferencesDataStore(name = "notification_history")

@Serializable
data class NotificationHistoryEntry(
    val requestId: String,
    val fromUserId: String,
    val fromUsername: String,
    val fromDisplayName: String? = null,
    val fromAvatarUrl: String? = null,
    val accepted: Boolean,
    val respondedAtMillis: Long
)

/**
 * Purely local, on-device record of connection requests the user
 * has already responded to. The backend never stores this — once a
 * request is answered it's gone from the pending list except as an
 * accepted connection — so the "you accepted/declined @x" log the
 * Notifications screen shows is device-only, survives app restarts,
 * and self-prunes anything older than 30 days on every read.
 */
class NotificationHistoryStore(private val context: Context) {

    private val key = stringPreferencesKey("entries_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000

    suspend fun getAll(): List<NotificationHistoryEntry> {
        val raw = context.notificationHistoryDataStore.data.first()[key] ?: return emptyList()
        val entries = runCatching {
            json.decodeFromString<List<NotificationHistoryEntry>>(raw)
        }.getOrDefault(emptyList())

        val cutoff = System.currentTimeMillis() - thirtyDaysMillis
        val fresh = entries.filter { it.respondedAtMillis >= cutoff }
        if (fresh.size != entries.size) persist(fresh) // prune expired entries from disk
        return fresh
    }

    suspend fun record(entry: NotificationHistoryEntry) {
        val current = getAll()
        val updated = listOf(entry) + current.filterNot { it.requestId == entry.requestId }
        persist(updated)
    }

    private suspend fun persist(entries: List<NotificationHistoryEntry>) {
        val raw = json.encodeToString(entries)
        context.notificationHistoryDataStore.edit { it[key] = raw }
    }
}
