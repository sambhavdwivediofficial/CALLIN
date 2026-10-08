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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.data.repository.CallPeerInfo
import com.sambhavdwivedi.callin.data.repository.CallUiState
import com.sambhavdwivedi.callin.ui.components.AvatarCircle
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import kotlinx.coroutines.delay

/**
 * Single entry point mounted once, app-wide, whenever
 * CallRepository.state is not Idle — see CallinNavHost/MainActivity
 * for where this is hosted on top of everything else. Routes to the
 * right full-screen UI for whichever state the call is currently in.
 *
 * Mute/speaker state is owned by CallRepository (so it survives this
 * composable being torn down/recreated across configuration changes)
 * and is collected here for both the Outgoing (ringing) and Active
 * screens — a caller can set speaker/mute *before* the callee even
 * answers, and whatever they picked is exactly what's already in
 * effect the instant WebRTC connects, because CallRepository applies
 * isMuted/isSpeakerOn to the WebRtcClient the moment it is created
 * (see ensureWebRtc()/acceptCall()/onAccept() in CallRepository).
 */
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

    val muted by container.callRepository.isMuted.collectAsState()
    val speakerOn by container.callRepository.isSpeakerOn.collectAsState()

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
            isMuted = muted,
            isSpeakerOn = speakerOn,
            onToggleMute = { container.callRepository.toggleMute() },
            onToggleSpeaker = { container.callRepository.toggleSpeaker() },
            onEndCall = { container.callRepository.cancelCall() }
        )

        is CallUiState.Active -> InCallScreen(
            info = s.info,
            statusText = null,
            startedAtMillis = s.startedAtMillis,
            isMuted = muted,
            isSpeakerOn = speakerOn,
            onToggleMute = { container.callRepository.toggleMute() },
            onToggleSpeaker = { container.callRepository.toggleSpeaker() },
            onEndCall = { container.callRepository.endCall() }
        )

        is CallUiState.Ended -> {
            LaunchedEffect(s.info.callId) {
                delay(1200)
                container.callRepository.dismissEnded()
                onFinished()
            }
            EndedCallScreen(info = s.info, reason = s.reason)
        }

        CallUiState.Idle -> Unit
    }
}

// ─────────────────────────────────────────────────────────────
// Incoming call — full-screen ring UI with Accept / Decline
// ─────────────────────────────────────────────────────────────

@Composable
fun IncomingCallScreen(
    info: CallPeerInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallinColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Text(
                text = "Incoming call",
                color = CallinColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(28.dp))

            PulsingPeerAvatar(info = info)

            Spacer(Modifier.height(24.dp))

            Text(
                text = info.peerDisplayName ?: info.peerUsername,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "@${info.peerUsername}",
                color = CallinColors.TextSecondary,
                fontSize = 15.sp
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallActionButton(
                    icon = Icons.Filled.CallEnd,
                    background = CallinColors.Danger,
                    label = "Decline",
                    onClick = onReject
                )
                CallActionButton(
                    icon = Icons.Filled.Call,
                    background = CallinColors.Success,
                    label = "Accept",
                    onClick = onAccept
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Outgoing (ringing/connecting) + Active (connected) — one shared
// screen, since the layout and controls are identical; only the
// status line under the name (ringing text vs. live timer) differs.
// ─────────────────────────────────────────────────────────────

@Composable
fun InCallScreen(
    info: CallPeerInfo,
    statusText: String?,
    startedAtMillis: Long?,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallinColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            AvatarCircle(
                avatarUrl = info.peerAvatarUrl,
                displayName = info.peerDisplayName,
                username = info.peerUsername,
                size = 128.dp
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = info.peerDisplayName ?: info.peerUsername,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "@${info.peerUsername}",
                color = CallinColors.TextSecondary,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(14.dp))

            // Either a static status ("Ringing...", "Connecting...")
            // or a live timer that both ends of the call compute from
            // the SAME startedAtMillis (the server's accept
            // timestamp, see CallRepository.onAccept) — so caller and
            // callee always show the identical elapsed time, in sync
            // to the second, with no drift between devices.
            if (startedAtMillis != null) {
                LiveCallTimer(startedAtMillis = startedAtMillis)
            } else if (statusText != null) {
                Text(
                    text = statusText,
                    color = CallinColors.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.weight(1f))

            // Mute / Speaker — fully live and user-controlled from the
            // instant a call is outgoing (even before the other side
            // answers). Each toggle flips instantly with no delay and
            // stays exactly as the user left it until they tap again.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallToggleButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    active = isMuted,
                    onClick = onToggleMute
                )
                CallToggleButton(
                    icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    label = "Speaker",
                    active = isSpeakerOn,
                    onClick = onToggleSpeaker
                )
            }

            Spacer(Modifier.height(32.dp))

            CallActionButton(
                icon = Icons.Filled.CallEnd,
                background = CallinColors.Danger,
                label = "End",
                onClick = onEndCall,
                size = 64.dp
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Ended — brief terminal screen shown for ~1.2s before the caller
// is dropped back to wherever they were (see CallRoute's
// LaunchedEffect above).
// ─────────────────────────────────────────────────────────────

@Composable
fun EndedCallScreen(info: CallPeerInfo, reason: String) {
    val label = when (reason) {
        "declined" -> "Declined"
        "cancelled" -> "Cancelled"
        "ended" -> "Call ended"
        "connection_failed" -> "Connection failed"
        else -> "Call ended"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallinColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AvatarCircle(
                avatarUrl = info.peerAvatarUrl,
                displayName = info.peerDisplayName,
                username = info.peerUsername,
                size = 96.dp,
                borderAlpha = 0.5f
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = info.peerDisplayName ?: info.peerUsername,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                color = CallinColors.TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared pieces
// ─────────────────────────────────────────────────────────────

/** Recomputes elapsed time once a second from a shared base
 * timestamp — never a locally-running counter that could drift —
 * so this is always in lockstep with the same field on the other
 * device, since both read from the identical startedAtMillis. */
@Composable
private fun LiveCallTimer(startedAtMillis: Long) {
    var elapsedSeconds by remember(startedAtMillis) {
        mutableLongStateOf(((System.currentTimeMillis() - startedAtMillis) / 1000).coerceAtLeast(0))
    }

    LaunchedEffect(startedAtMillis) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }

    val h = elapsedSeconds / 3600
    val m = (elapsedSeconds % 3600) / 60
    val sec = elapsedSeconds % 60
    val text = if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, sec)
    } else {
        String.format("%02d:%02d", m, sec)
    }

    Text(
        text = text,
        color = CallinColors.TextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun PulsingPeerAvatar(info: CallPeerInfo) {
    val transition = rememberInfiniteTransition(label = "incoming_pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "incoming_pulse_scale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "incoming_pulse_ring"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .background(CallinColors.Success.copy(alpha = ringAlpha))
        )
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding((140.dp - 140.dp * scale) / -2)
            ) {
                AvatarCircle(
                    avatarUrl = info.peerAvatarUrl,
                    displayName = info.peerDisplayName,
                    username = info.peerUsername,
                    size = 140.dp
                )
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    background: Color,
    label: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(text = label, color = CallinColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun CallToggleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val background = if (active) CallinColors.TextPrimary else CallinColors.TextSecondary.copy(alpha = 0.14f)
    val tint = if (active) CallinColors.Background else CallinColors.TextPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(text = label, color = CallinColors.TextSecondary, fontSize = 12.sp)
    }
}
