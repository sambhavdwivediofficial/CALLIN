package com.sambhavdwivedi.callin.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun ContactsScreen(container: AppContainer) {
    val focusManager = LocalFocusManager.current

    var users by remember {
        mutableStateOf<List<PublicUserDto>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var searchQuery by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        container.userRepository.listUsers()
            .onSuccess { loadedUsers ->
                users = loadedUsers
            }
            .onFailure { error ->
                errorMessage = error.message
                    ?: "Could not load contacts."
            }

        isLoading = false
    }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) {
            users
        } else {
            val query = searchQuery
                .trim()
                .lowercase()

            users.filter { user ->
                val name = user.display_name
                    .orEmpty()
                    .lowercase()

                val username = user.username
                    .orEmpty()
                    .lowercase()

                name.startsWith(query) ||
                        username.startsWith(query)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus()
                }
            }
    ) {

        ContactsHeader(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it }
        )

        when {

            isLoading -> {
                LoadingState()
            }

            errorMessage != null -> {
                EmptyState(errorMessage!!)
            }

            users.isEmpty() -> {
                EmptyState(
                    "No other CALLIN users yet.\n" +
                            "Invite a friend to get started."
                )
            }

            filteredUsers.isEmpty() &&
                    searchQuery.isNotBlank() -> {

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
                            user.username
                                ?: user.display_name
                                ?: ""
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
private fun ContactsHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 16.dp
            )
    ) {

        val availableWidth = maxWidth

        /*
         * Responsive search width.
         *
         * PHONE
         * -> compact fixed width
         *
         * TABLET
         * -> wider, but not huge
         *
         * LARGE TABLET
         * -> grows further
         *
         * The search box never fills the entire remaining width.
         */

        val searchWidth = when {
            availableWidth < 600.dp -> {
                250.dp
            }

            availableWidth < 900.dp -> {
                320.dp
            }

            availableWidth < 1200.dp -> {
                390.dp
            }

            else -> {
                460.dp
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            /*
             * TITLE
             *
             * Natural width only.
             * Never stretches on tablet.
             */
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Filled.People,
                    contentDescription = null,
                    tint = CallinColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text = "Contacts",
                    color = CallinColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    maxLines = 1
                )
            }

            /*
             * PHONE:
             *
             * Keep search close to title.
             *
             * TABLET:
             *
             * Push search completely toward the right edge.
             */
            if (availableWidth < 600.dp) {

                Spacer(
                    modifier = Modifier.width(12.dp)
                )

            } else {

                Spacer(
                    modifier = Modifier.weight(1f)
                )
            }

            /*
             * Search width is controlled independently.
             *
             * Right edge stays aligned with the header's
             * right padding.
             */
            SearchBox(
                modifier = Modifier.width(searchWidth),
                value = searchQuery,
                onSearchChange = onSearchChange
            )
        }
    }
}

@Composable
private fun SearchBox(
    modifier: Modifier = Modifier,
    value: String,
    onSearchChange: (String) -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(
                RoundedCornerShape(20.dp)
            )
            .background(
                Color.Transparent
            )
            .border(
                width = 1.dp,
                color = CallinColors.TextSecondary.copy(
                    alpha = 0.22f
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(
                horizontal = 12.dp
            ),
        contentAlignment = Alignment.CenterStart
    ) {

        BasicTextField(
            value = value,
            onValueChange = onSearchChange,
            singleLine = true,
            cursorBrush = SolidColor(
                CallinColors.TextSecondary
            ),
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

                    if (value.isEmpty()) {
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
private fun ContactRow(
    user: PublicUserDto
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    CallinColors.Background
                )
                .border(
                    width = 1.dp,
                    color = CallinColors.TextSecondary,
                    shape = CircleShape
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

        Spacer(
            modifier = Modifier.width(14.dp)
        )

        Column {

            Text(
                text = user.display_name
                    ?: user.username
                    ?: "CALLIN user",
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )

            user.username?.let { username ->

                Text(
                    text = "@$username",
                    color = CallinColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PulseBarsLoader(
            barColor = CallinColors.TextSecondary
        )
    }
}

@Composable
fun EmptyState(
    message: String
) {
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
