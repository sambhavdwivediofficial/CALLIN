package com.sambhavdwivedi.callin.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.data.repository.CallPeerInfo
import com.sambhavdwivedi.callin.data.repository.CallUiState
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import kotlinx.coroutines.delay

@Composable
fun CallRoute(container: AppContainer, onFinished: () -> Unit) {
    val context = LocalContext.current
    val state by container.callRepository.state.collectAsState()
    var pendingAccept by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingAccept) container.callRepository.acceptCall()
        pendingAccept = false
    }

    fun acceptWithPermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            container.callRepository.acceptCall()
        } else {
            pendingAccept = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    when (val s = state) {
        is CallUiState.Incoming -> IncomingCallScreen(
            info = s.info,
            onAccept = { acceptWithPermission() },
            onReject = { container.callRepository.rejectCall() }
        )
        is CallUiState.Outgoing -> InCallScreen(
            info = s.info,
            statusText = if (s.ringing) "Ringing..." else "Connecting...",
            startedAtMillis = null,
            isMuted = false,
            isSpeakerOn = true,
            showMute = true,
            onToggleMute = {},
            onToggleSpeaker = { container.callRepository.toggleSpeaker() },
            onEnd = { container.callRepository.cancelCall() }
        )
        is CallUiState.Active -> {
            val muted by container.callRepository.isMuted.collectAsState()
            val speakerOn by container.callRepository.isSpeakerOn.collectAsState()
            InCallScreen(
                info = s.info,
                statusText = null,
                startedAtMillis = s.startedAtMillis,
                isMuted = muted,
                isSpeakerOn = speakerOn,
                showMute = true,
                onToggleMute = { container.callRepository.toggleMute() },
                onToggleSpeaker = { container.callRepository.toggleSpeaker() },
                onEnd = { container.callRepository.endCall() }
            )
        }
        is CallUiState.Ended -> {
            LaunchedEffect(s) {
                delay(1200)
                container.callRepository.dismissEnded()
                onFinished()
            }
            InCallScreen(
                info = s.info,
                statusText = s.reason.replaceFirstChar { it.uppercase() },
                startedAtMillis = null,
                isMuted = false,
                isSpeakerOn = true,
                showMute = false,
                onToggleMute = {},
                onToggleSpeaker = {},
                onEnd = {}
            )
        }
        is CallUiState.Idle -> Unit
    }
}

@Composable
private fun PeerAvatar(avatarUrl: String?, size: androidx.compose.ui.unit.Dp, borderAlpha: Float) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CallinColors.Background)
            .border(2.dp, CallinColors.TextSecondary.copy(alpha = borderAlpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = CallinColors.TextSecondary, modifier = Modifier.size(size / 2.5f))
        }
    }
}

@Composable
private fun InCallScreen(
    info: CallPeerInfo,
    statusText: String?,
    startedAtMillis: Long?,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    showMute: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit,
) {
    var elapsedText by remember { mutableStateOf("00:00") }

    LaunchedEffect(startedAtMillis) {
        if (startedAtMillis == null) return@LaunchedEffect
        while (true) {
            elapsedText = formatDuration(System.currentTimeMillis() - startedAtMillis)
            delay(250)
        }
    }

    val glow = rememberInfiniteTransition(label = "avatar_glow")
    val glowAlpha by glow.animateFloat(
        initialValue = if (startedAtMillis == null) 0.35f else 1f,
        targetValue = if (startedAtMillis == null) 0.9f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "avatar_glow_alpha"
    )

    Box(modifier = Modifier.fillMaxSize().background(CallinColors.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            PeerAvatar(info.peerAvatarUrl, 140.dp, glowAlpha)
            Spacer(Modifier.height(24.dp))
            Text(
                text = info.peerDisplayName ?: info.peerUsername,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(text = statusText ?: elapsedText, color = CallinColors.TextSecondary, fontSize = 15.sp)

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0E1626))
                    .padding(vertical = 16.dp, horizontal = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlButton(icon = Icons.Filled.VolumeUp, active = isSpeakerOn, onClick = onToggleSpeaker)

                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(CallinColors.Danger)
                            .clickable(onClick = onEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    if (showMute) {
                        CallControlButton(icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, active = !isMuted, onClick = onToggleMute)
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingCallScreen(info: CallPeerInfo, onAccept: () -> Unit, onReject: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(CallinColors.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(88.dp))
            PeerAvatar(info.peerAvatarUrl, 140.dp, 1f)
            Spacer(Modifier.height(24.dp))
            Text(
                text = info.peerDisplayName ?: info.peerUsername,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text("Incoming call...", color = CallinColors.TextSecondary, fontSize = 15.sp)

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(CallinColors.Danger).clickable(onClick = onReject),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Decline", color = CallinColors.TextSecondary, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(CallinColors.Success).clickable(onClick = onAccept),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Accept", color = CallinColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
