package com.sambhavdwivedi.callin.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val requests = remember { mutableStateListOf<ConnectionRequestDto>() }
    val respondingIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        container.connectionRepository.refreshPending()
            .onSuccess { requests.clear(); requests.addAll(it) }
            .onFailure { errorMessage = it.message ?: "Could not load requests." }
        isLoading = false
    }

    fun respond(request: ConnectionRequestDto, accept: Boolean) {
        respondingIds.add(request.id)
        scope.launch {
            container.connectionRepository.respond(request.id, accept)
                .onSuccess { requests.remove(request) }
            respondingIds.remove(request.id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallinColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Notifications",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PulseBarsLoader(barColor = CallinColors.TextSecondary)
                }
                errorMessage != null -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(errorMessage!!, color = CallinColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                requests.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No pending connection requests.",
                        color = CallinColors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = requests, key = { it.id }) { request ->
                        RequestRow(
                            request = request,
                            isResponding = respondingIds.contains(request.id),
                            onAccept = { respond(request, true) },
                            onReject = { respond(request, false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    request: ConnectionRequestDto,
    isResponding: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(CallinColors.Background)
                .border(1.dp, CallinColors.TextSecondary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!request.from_avatar_url.isNullOrBlank()) {
                AsyncImage(
                    model = request.from_avatar_url,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = CallinColors.TextSecondary)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.from_display_name ?: request.from_username,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Text(
                text = "@${request.from_username}",
                color = CallinColors.TextSecondary,
                fontSize = 12.sp
            )
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
