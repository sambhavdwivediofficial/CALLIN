package com.sambhavdwivedi.callin.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    container: AppContainer,
    onSignOut: () -> Unit,
    onOpenMyQr: () -> Unit,
    onOpenScanQr: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Cached profile: shows instantly on every revisit, no spinner
    // flash — getMe() below just refreshes it silently underneath.
    val me by container.userRepository.me.collectAsState()
    var isLoading by remember { mutableStateOf(me == null) }
    var isSigningOut by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val pendingRequests by container.connectionRepository.pendingRequests.collectAsState()
    val hasPendingRequests = !pendingRequests.isNullOrEmpty()

    LaunchedEffect(Unit) {
        container.userRepository.getMe()
        isLoading = false
    }

    // Lightweight polling so the bell's dot and the Notifications
    // list reflect new requests without the user needing to
    // manually refresh. Only runs while this screen is composed.
    LaunchedEffect(Unit) {
        while (true) {
            container.connectionRepository.refreshPending()
            delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = CallinColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Profile",
                    color = CallinColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp).clickable(onClick = onOpenScanQr)
                )

                Spacer(Modifier.width(16.dp))

                Icon(
                    imageVector = Icons.Filled.QrCode,
                    contentDescription = "QR Code",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp).clickable(onClick = onOpenMyQr)
                )

                Spacer(Modifier.width(16.dp))

                Box {
                    Icon(
                        imageVector = Icons.Filled.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp).clickable(onClick = onOpenNotifications)
                    )
                    if (hasPendingRequests) {
                        GlowingDot(modifier = Modifier.align(Alignment.TopEnd))
                    }
                }

                Spacer(Modifier.width(16.dp))

                Box {
                    CustomMenuIcon(modifier = Modifier.clickable { menuExpanded = true })

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = CallinColors.Background,
                        modifier = Modifier.border(
                            width = 0.01.dp,
                            color = Color.LightGray,
                            shape = RoundedCornerShape(6.dp)
                        )
                    ) {
                        DropdownMenuItem(
                            text = { Text("Terms of Service") },
                            onClick = { menuExpanded = false; onOpenTerms() }
                        )
                        HorizontalDivider(thickness = 0.3.dp, color = Color.LightGray)
                        DropdownMenuItem(
                            text = { Text("Privacy Policy") },
                            onClick = { menuExpanded = false; onOpenPrivacy() }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (isLoading && me == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PulseBarsLoader(barColor = CallinColors.TextSecondary)
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(CallinColors.Background)
                        .border(1.dp, CallinColors.TextSecondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!me?.avatar_url.isNullOrBlank()) {
                        AsyncImage(
                            model = me?.avatar_url,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = CallinColors.TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = me?.display_name ?: "—",
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            me?.username?.let {
                Text(
                    text = "@$it",
                    color = CallinColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = me?.email ?: "",
                color = CallinColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = {
                isSigningOut = true
                scope.launch {
                    container.signalingClient.stop()
                    container.authRepository.logout()
                    isSigningOut = false
                    onSignOut()
                }
            },
            enabled = !isSigningOut,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, CallinColors.Danger.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CallinColors.Danger),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isSigningOut) {
                PulseBarsLoader(size = 22.dp, barColor = CallinColors.Danger)
            } else {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = "Sign out", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** Small pulsing blue dot, top-right of the bell, while requests are pending. */
@Composable
private fun GlowingDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dot_glow")
    val glow by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_glow_alpha"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .background(Color(0xFF2E90FF).copy(alpha = glow), CircleShape)
    )
}

@Composable
private fun CustomMenuIcon(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.5.dp),
        horizontalAlignment = Alignment.Start,
        modifier = modifier.width(24.dp)
    ) {
        Box(Modifier.width(24.dp).height(2.dp).background(Color.White, RoundedCornerShape(50)))
        Box(Modifier.width(18.dp).height(2.dp).background(Color.White, RoundedCornerShape(50)))
        Box(Modifier.width(13.dp).height(2.dp).background(Color.White, RoundedCornerShape(50)))
    }
}
