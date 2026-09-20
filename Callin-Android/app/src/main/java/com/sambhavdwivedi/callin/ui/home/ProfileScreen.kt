package com.sambhavdwivedi.callin.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<MeDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSigningOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.userRepository.getMe()
            .onSuccess { me = it }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Profile",
            color = CallinColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp
        )

        Spacer(Modifier.height(28.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PulseBarsLoader(barColor = CallinColors.TextSecondary)
            }
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                    container.authRepository.logout()
                    isSigningOut = false
                    onSignOut()
                }
            },
            enabled = !isSigningOut,
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CallinColors.Danger.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CallinColors.Danger),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isSigningOut) {
                PulseBarsLoader(size = 22.dp, barColor = CallinColors.Danger)
            } else {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = "Sign out", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
