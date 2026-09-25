package com.sambhavdwivedi.callin.ui.home

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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sambhavdwivedi.callin.data.remote.dto.MeDto
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.theme.CallinColors
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

    var me by remember { mutableStateOf<MeDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSigningOut by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.userRepository.getMe()
            .onSuccess { me = it }

        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Profile title
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            // Header action icons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onOpenScanQr)
                )

                Spacer(Modifier.width(16.dp))

                Icon(
                    imageVector = Icons.Filled.QrCode,
                    contentDescription = "QR Code",
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onOpenMyQr)
                )

                Spacer(Modifier.width(16.dp))

                Icon(
                    imageVector = Icons.Filled.NotificationsNone,
                    contentDescription = "Notifications",
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onOpenNotifications)
                )

                Spacer(Modifier.width(16.dp))

                Box {
                    CustomMenuIcon(
                        modifier = Modifier.clickable { menuExpanded = true }
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Terms of Service") },
                            onClick = {
                                menuExpanded = false
                                onOpenTerms()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy Policy") },
                            onClick = {
                                menuExpanded = false
                                onOpenPrivacy()
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PulseBarsLoader(
                    barColor = CallinColors.TextSecondary
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(CallinColors.Background)
                        .border(
                            width = 1.dp,
                            color = CallinColors.TextSecondary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!me?.avatar_url.isNullOrBlank()) {
                        AsyncImage(
                            model = me?.avatar_url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape),
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
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                CallinColors.Danger.copy(alpha = 0.6f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = CallinColors.Danger
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isSigningOut) {
                PulseBarsLoader(
                    size = 22.dp,
                    barColor = CallinColors.Danger
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "Sign out",
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CustomMenuIcon(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.5.dp),
        horizontalAlignment = Alignment.Start,
        modifier = modifier.width(24.dp)
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(2.dp)
                .background(
                    Color.White,
                    RoundedCornerShape(50)
                )
        )

        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(
                    Color.White,
                    RoundedCornerShape(50)
                )
        )

        Box(
            modifier = Modifier
                .width(13.dp)
                .height(2.dp)
                .background(
                    Color.White,
                    RoundedCornerShape(50)
                )
        )
    }
}
