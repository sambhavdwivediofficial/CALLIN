package com.sambhavdwivedi.callin.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.sambhavdwivedi.callin.core.storage.CallDirection
import com.sambhavdwivedi.callin.core.storage.RecentCallEntry
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RecentsScreen(container: AppContainer) {
    var entries by remember { mutableStateOf<List<RecentCallEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        entries = container.recentCallsStore.getAll()
    }

    val (today, yesterday, older) = remember(entries) { groupByDay(entries) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = CallinColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Recents", color = CallinColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No recent calls yet.",
                    color = CallinColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (today.isNotEmpty()) {
                item { SectionHeader("Today") }
                items(today) { RecentRow(it) }
            }
            if (yesterday.isNotEmpty()) {
                item { SectionHeader("Yesterday") }
                items(yesterday) { RecentRow(it) }
            }
            if (older.isNotEmpty()) {
                item { SectionHeader("Older") }
                items(older) { RecentRow(it) }
            }
        }
    }
}

private fun groupByDay(entries: List<RecentCallEntry>): Triple<List<RecentCallEntry>, List<RecentCallEntry>, List<RecentCallEntry>> {
    val cal = Calendar.getInstance()
    val todayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000

    val today = entries.filter { it.timestampMillis >= todayStart }
    val yesterday = entries.filter { it.timestampMillis in yesterdayStart until todayStart }
    val older = entries.filter { it.timestampMillis < yesterdayStart }
    return Triple(today, yesterday, older)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = CallinColors.TextSecondary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun RecentRow(entry: RecentCallEntry) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CallinColors.Background)
                .border(1.dp, CallinColors.TextSecondary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!entry.peerAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entry.peerAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = CallinColors.TextSecondary)
            }
        }

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
                    contentDescription = null,
                    tint = CallinColors.TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("•", color = CallinColors.TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (entry.missed) "Missed" else formatDuration(entry.durationSeconds),
                    color = CallinColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(entry.timestampMillis)),
            color = CallinColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
