package com.sambhavdwivedi.callin.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.data.remote.dto.PublicUserDto
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import kotlinx.coroutines.delay

@Composable
fun ContactsScreen(container: AppContainer) {
    var users by remember { mutableStateOf<List<PublicUserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // This is used only for filtering.
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        container.userRepository.listUsers()
            .onSuccess { users = it }
            .onFailure { e ->
                errorMessage = e.message ?: "Could not load contacts."
            }

        isLoading = false
    }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) {
            users
        } else {
            val query = searchQuery.trim().lowercase()

            users.filter { user ->
                val name = user.display_name.orEmpty().lowercase()
                val username = user.username.orEmpty().lowercase()

                name.startsWith(query) || username.startsWith(query)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contacts title
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = null,
                tint = CallinColors.TextPrimary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "Contacts",
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp
            )

            Spacer(Modifier.width(12.dp))

            // Search box
            SearchBox(
                onSearchChange = { searchQuery = it }
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PulseBarsLoader(
                        barColor = CallinColors.TextSecondary
                    )
                }
            }

            errorMessage != null -> {
                EmptyState(errorMessage!!)
            }

            users.isEmpty() -> {
                EmptyState(
                    "No other CALLIN users yet.\nInvite a friend to get started."
                )
            }

            filteredUsers.isEmpty() && searchQuery.isNotBlank() -> {
                EmptyState(
                    "No contacts found for \"$searchQuery\"."
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredUsers,
                        key = { user ->
                            user.username ?: user.display_name ?: ""
                        }
                    ) { user ->
                        ContactRow(user)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    onSearchChange: (String) -> Unit
) {
    // Local state makes typed characters appear immediately.
    var localSearchQuery by remember { mutableStateOf("") }

    // Filtering updates slightly after typing, so the text field itself
    // does not wait for the parent screen to recompose.
    LaunchedEffect(localSearchQuery) {
        delay(80)
        onSearchChange(localSearchQuery)
    }

    Box(
        modifier = Modifier
            .height(40.dp)
            .width(250.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Transparent)
            .border(
                width = 1.dp,
                color = CallinColors.TextSecondary.copy(alpha = 0.22f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = localSearchQuery,
            onValueChange = {
                localSearchQuery = it
            },
            singleLine = true,
            cursorBrush = SolidColor(CallinColors.TextSecondary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            textStyle = TextStyle(
                color = CallinColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (localSearchQuery.isEmpty()) {
                        Text(
                            text = "Search",
                            color = CallinColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    }

                    innerTextField()
                }
            }
        )
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
                .border(
                    1.dp,
                    CallinColors.TextSecondary,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!user.avatar_url.isNullOrBlank()) {
                AsyncImage(
                    model = user.avatar_url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
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
                text = user.display_name
                    ?: user.username
                    ?: "CALLIN user",
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = CallinColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
