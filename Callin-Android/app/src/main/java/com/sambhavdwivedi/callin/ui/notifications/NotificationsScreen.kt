package com.sambhavdwivedi.callin.ui.notifications

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionRequestDto
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class NotifStatus { PENDING, ACCEPTED, REJECTED }

/**
 * A single feed row's identity and position are fixed at creation —
 * only [status] (a Compose State) changes when the user responds.
 * This is what makes accept/reject flicker-free: the master list
 * this class lives in is never rebuilt or re-sorted on response, so
 * Compose never has to remove-then-reinsert the row; it just
 * recomposes that one row's trailing content.
 */
private class NotifItem(
    val requestId: String,
    val fromUsername: String,
    val fromDisplayName: String?,
    val fromAvatarUrl: String?,
    val sortKey: Long,
    val originalRequest: ConnectionRequestDto?,
    initialStatus: NotifStatus,
) {
    var status by mutableStateOf(initialStatus)
}

private fun parseIsoMillis(iso: String): Long =
    runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)

@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    val items = remember { mutableStateListOf<NotifItem>() }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val respondingIds = remember { mutableStateListOf<String>() }
    var showClearConfirm by remember { mutableStateOf(false) }

    // One-time initial load: history (already-answered log) + the
    // currently pending requests, merged and sorted once. After
    // this, the list is only ever appended to (new incoming
    // requests) or mutated in place (a response) — never rebuilt.
    LaunchedEffect(Unit) {
        container.connectionRepository.refreshHistory()
        val pendingResult = container.connectionRepository.refreshPending()
        if (pendingResult.isFailure && container.connectionRepository.pendingRequests.value == null) {
            errorMessage = pendingResult.exceptionOrNull()?.message ?: "Could not load requests."
        }

        val historyItems = container.connectionRepository.history.value.map { entry ->
            NotifItem(
                requestId = entry.requestId,
                fromUsername = entry.fromUsername,
                fromDisplayName = entry.fromDisplayName,
                fromAvatarUrl = entry.fromAvatarUrl,
                sortKey = entry.respondedAtMillis,
                originalRequest = null,
                initialStatus = if (entry.accepted) NotifStatus.ACCEPTED else NotifStatus.REJECTED
            )
        }
        val pendingItems = container.connectionRepository.pendingRequests.value.orEmpty().map { req ->
            NotifItem(
                requestId = req.id,
                fromUsername = req.from_username,
                fromDisplayName = req.from_display_name,
                fromAvatarUrl = req.from_avatar_url,
                sortKey = parseIsoMillis(req.created_at),
                originalRequest = req,
                initialStatus = NotifStatus.PENDING
            )
        }

        items.clear()
        items.addAll((historyItems + pendingItems).sortedByDescending { it.sortKey })
        isLoading = false
    }

    // Polls for genuinely NEW incoming requests only — anything
    // already in [items] (whatever its status) is left completely
    // alone, so a response the user just made is never touched by
    // this poll.
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            container.connectionRepository.refreshPending().onSuccess { serverPending ->
                val existingIds = items.map { it.requestId }.toSet()
                val fresh = serverPending.filter { it.id !in existingIds }
                if (fresh.isNotEmpty()) {
                    val newItems = fresh.map { req ->
                        NotifItem(
                            requestId = req.id,
                            fromUsername = req.from_username,
                            fromDisplayName = req.from_display_name,
                            fromAvatarUrl = req.from_avatar_url,
                            sortKey = parseIsoMillis(req.created_at),
                            originalRequest = req,
                            initialStatus = NotifStatus.PENDING
                        )
                    }
                    items.addAll(0, newItems.sortedByDescending { it.sortKey })
                }
            }
        }
    }

    fun respond(item: NotifItem, accept: Boolean) {
        val original = item.originalRequest ?: return
        respondingIds.add(item.requestId)
        // Flip the status immediately — this alone drives the
        // crossfade below; the row never moves.
        item.status = if (accept) NotifStatus.ACCEPTED else NotifStatus.REJECTED
        scope.launch {
            container.connectionRepository.respond(original, accept)
                .onFailure { item.status = NotifStatus.PENDING }
            respondingIds.remove(item.requestId)
        }
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
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Clear", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            when {
                isLoading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PulseBarsLoader(barColor = CallinColors.TextSecondary)
                }
                errorMessage != null && items.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(errorMessage!!, color = CallinColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                items.isEmpty() -> Box(
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
                    items(items = items, key = { it.requestId }) { item ->
                        NotifRow(
                            item = item,
                            isResponding = respondingIds.contains(item.requestId),
                            onAccept = { respond(item, true) },
                            onReject = { respond(item, false) }
                        )
                    }
                }
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear notifications?") },
                text = { Text("This removes your notification history from this device. Pending requests you haven't responded to are not affected.") },
                confirmButton = {
                    TextButton(onClick = {
                        showClearConfirm = false
                        scope.launch {
                            container.connectionRepository.clearHistory()
                            items.removeAll { it.status != NotifStatus.PENDING }
                        }
                    }) {
                        Text("Clear", color = CallinColors.Danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel", color = CallinColors.TextSecondary)
                    }
                },
                containerColor = CallinColors.Background,
                titleContentColor = Color.White,
                textContentColor = CallinColors.TextSecondary
            )
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
private fun NotifRow(
    item: NotifItem,
    isResponding: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotifAvatar(item.fromAvatarUrl)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fromDisplayName ?: item.fromUsername,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Text(text = "@${item.fromUsername}", color = CallinColors.TextSecondary, fontSize = 12.sp)
        }

        val displayKey = when {
            isResponding -> "loading"
            item.status == NotifStatus.PENDING -> "pending"
            item.status == NotifStatus.ACCEPTED -> "accepted"
            else -> "rejected"
        }

        Crossfade(targetState = displayKey, animationSpec = tween(200), label = "notif_trailing") { key ->
            when (key) {
                "loading" -> PulseBarsLoader(size = 22.dp, barColor = CallinColors.TextSecondary)
                "pending" -> Row(verticalAlignment = Alignment.CenterVertically) {
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
                "accepted" -> OutcomePill(text = "Accept", color = CallinColors.Success)
                else -> OutcomePill(text = "Reject", color = CallinColors.Danger)
            }
        }
    }
}

/** Thin, small, rounded-rectangle badge showing the outcome — takes
 * the place of the two action buttons once a request is answered. */
@Composable
private fun OutcomePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
