package com.sambhavdwivedi.callin.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.core.storage.CallDirection
import com.sambhavdwivedi.callin.core.storage.RecentCallEntry
import com.sambhavdwivedi.callin.ui.components.AvatarCircle
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RecentsScreen(container: AppContainer) {
    val entries by container.recentCallsStore.entries.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }

    // Already preloaded during the splash (see MainActivity.AppRoot),
    // so entries is non-null almost immediately — this just makes
    // sure it's current if this is the very first collection.
    LaunchedEffect(Unit) {
        if (entries == null) container.recentCallsStore.getAll()
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            rotation.snapTo(0f)
            rotation.animateTo(
                360f,
                animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing))
            )
        } else {
            rotation.snapTo(0f)
        }
    }

    val allEntries = entries.orEmpty()
    val (today, yesterday, older) = remember(allEntries) { groupByDay(allEntries) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = CallinColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Recents", color = CallinColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh",
                tint = CallinColors.TextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation.value }
                    .clickable(enabled = !isRefreshing) {
                        isRefreshing = true
                        scope.launch {
                            container.recentCallsStore.getAll()
                            isRefreshing = false
                        }
                    }
            )
        }

        if (entries == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.sambhavdwivedi.callin.ui.components.PulseBarsLoader(barColor = CallinColors.TextSecondary)
            }
            return@Column
        }

        if (allEntries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No recent calls yet.", color = CallinColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (today.isNotEmpty()) { item { SectionHeader("Today") }; items(today) { RecentRow(it) } }
            if (yesterday.isNotEmpty()) { item { SectionHeader("Yesterday") }; items(yesterday) { RecentRow(it) } }
            if (older.isNotEmpty()) { item { SectionHeader("Older") }; items(older) { RecentRow(it) } }
        }
    }
}

private fun groupByDay(entries: List<RecentCallEntry>): Triple<List<RecentCallEntry>, List<RecentCallEntry>, List<RecentCallEntry>> {
    val cal = Calendar.getInstance()
    val todayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000
    return Triple(
        entries.filter { it.timestampMillis >= todayStart },
        entries.filter { it.timestampMillis in yesterdayStart until todayStart },
        entries.filter { it.timestampMillis < yesterdayStart }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = CallinColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun RecentRow(entry: RecentCallEntry) {
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AvatarCircle(avatarUrl = entry.peerAvatarUrl, displayName = entry.peerDisplayName, username = entry.peerUsername, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.peerDisplayName ?: entry.peerUsername,
                color = if (entry.missed) CallinColors.Danger else CallinColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (entry.direction == CallDirection.OUTGOING) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = null, tint = CallinColors.TextSecondary, modifier = Modifier.width(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("•", color = CallinColors.TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (entry.missed) "Missed" else formatDuration(entry.durationSeconds),
                    color = CallinColors.TextSecondary, fontSize = 12.sp
                )
            }
        }
        Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(entry.timestampMillis)), color = CallinColors.TextSecondary, fontSize = 12.sp)
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600; val m = (totalSeconds % 3600) / 60; val s = totalSeconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
