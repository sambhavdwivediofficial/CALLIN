package com.sambhavdwivedi.callin.ui.home

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.data.remote.dto.PublicUserDto
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.theme.CallinColors

@Composable
fun ContactsScreen(container: AppContainer) {
    var users by remember { mutableStateOf<List<PublicUserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.userRepository.listUsers()
            .onSuccess { users = it }
            .onFailure { e -> errorMessage = e.message ?: "Could not load contacts." }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            text = "Contacts",
            color = CallinColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PulseBarsLoader(barColor = CallinColors.TextSecondary)
            }

            errorMessage != null -> EmptyState(errorMessage!!)

            users.isEmpty() -> EmptyState("No other CALLIN users yet.\nInvite a friend to get started.")

            else -> LazyColumn {
                items(users) { user -> ContactRow(user) }
            }
        }
    }
}

@Composable
private fun ContactRow(user: PublicUserDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CallinColors.Background)
                .border(1.dp, CallinColors.TextSecondary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!user.avatar_url.isNullOrBlank()) {
                AsyncImage(
                    model = user.avatar_url,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = CallinColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Text(
                text = user.display_name ?: user.username ?: "CALLIN user",
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            user.username?.let {
                Text(
                    text = "@$it",
                    color = CallinColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = CallinColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
