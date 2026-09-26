package com.sambhavdwivedi.callin.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.core.storage.NotificationHistoryEntry
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionRequestDto
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One unified feed row: either an actionable pending request, or a
 * read-only log entry (already accepted/rejected), kept local for
 * 30 days. Same visual slot either way — only the trailing icon(s)
 * change from two buttons to one fixed outcome icon. */
private sealed interface NotifRow {
    val sortKey: Long
    data class Pending(val request: ConnectionRequestDto, val createdAtMillis: Long) : NotifRow {
        override val sortKey get() = createdAtMillis
    }
    data class Logged(val entry: NotificationHistoryEntry) : NotifRow {
        override val sortKey get() = entry.respondedAtMillis
    }
}

private fun parseIsoMillis(iso: String): Long =
    runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)

@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    val pending by container.connectionRepository.pendingRequests.collectAsState()
    val history by container.connectionRepository.history.collectAsState()
    var isLoading by remember { mutableStateOf(pending == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val respondingIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        container.connectionRepository.refreshHistory()
        container.connectionRepository.refreshPending()
            .onFailure { if (pending == null) errorMessage = it.message ?: "Could not load requests." }
        isLoading = false
    }

    // Keeps this screen live while open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            container.connectionRepository.refreshPending()
        }
    }

    fun respond(request: ConnectionRequestDto, accept: Boolean) {
        respondingIds.add(request.id)
        scope.launch {
            container.connectionRepository.respond(request, accept)
            respondingIds.remove(request.id)
        }
    }

    val rows: List<NotifRow> = remember(pending, history) {
        val pendingRows = pending.orEmpty().map { NotifRow.Pending(it, parseIsoMillis(it.created_at)) }
        val historyRows = history.map { NotifRow.Logged(it) }
        (pendingRows + historyRows).sortedByDescending { it.sortKey }
    }

    Box(modifier = Modifier.fillMaxSize().background(CallinColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Notifications",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(8.dp)
                        )
                        
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Clear",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            when {
                isLoading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PulseBarsLoader(barColor = CallinColors.TextSecondary)
                }
                errorMessage != null && rows.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(errorMessage!!, color = CallinColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                rows.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No notifications yet.",
                        color = CallinColors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = rows,
                        key = { row ->
                            when (row) {
                                is NotifRow.Pending -> "pending_${row.request.id}"
                                is NotifRow.Logged -> "log_${row.entry.requestId}"
                            }
                        }
                    ) { row ->
                        when (row) {
                            is NotifRow.Pending -> PendingRow(
                                request = row.request,
                                isResponding = respondingIds.contains(row.request.id),
                                onAccept = { respond(row.request, true) },
                                onReject = { respond(row.request, false) }
                            )
                            is NotifRow.Logged -> LoggedRow(entry = row.entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifAvatar(avatarUrl: String?) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(CallinColors.Background)
            .border(1.dp, CallinColors.TextSecondary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(46.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = CallinColors.TextSecondary)
        }
    }
}

@Composable
private fun PendingRow(
    request: ConnectionRequestDto,
    isResponding: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotifAvatar(request.from_avatar_url)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.from_display_name ?: request.from_username,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Text(text = "@${request.from_username}", color = CallinColors.TextSecondary, fontSize = 12.sp)
        }

        if (isResponding) {
            PulseBarsLoader(size = 22.dp, barColor = CallinColors.TextSecondary)
        } else {
            IconButton(onClick = onReject) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(CallinColors.Danger.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Decline", tint = CallinColors.Danger, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onAccept) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(CallinColors.Success.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Accept", tint = CallinColors.Success, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Read-only log row: same layout as a pending request, but instead
 * of two action buttons there's a single icon showing what already
 * happened. Nothing here is clickable. */
@Composable
private fun LoggedRow(entry: NotificationHistoryEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotifAvatar(entry.fromAvatarUrl)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.fromDisplayName ?: entry.fromUsername,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Text(text = "@${entry.fromUsername}", color = CallinColors.TextSecondary, fontSize = 12.sp)
        }

        val (bg, tint, icon) = if (entry.accepted) {
            Triple(CallinColors.Success.copy(alpha = 0.18f), CallinColors.Success, Icons.Filled.Check)
        } else {
            Triple(CallinColors.Danger.copy(alpha = 0.18f), CallinColors.Danger, Icons.Filled.Close)
        }

        Box(
            Modifier.size(30.dp).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = if (entry.accepted) "Accepted" else "Declined", tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}
