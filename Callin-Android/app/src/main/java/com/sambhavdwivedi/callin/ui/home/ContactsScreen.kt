// package com.sambhavdwivedi.callin.ui.home

// import androidx.compose.foundation.background
// import androidx.compose.foundation.border
// import androidx.compose.foundation.layout.Box
// import androidx.compose.foundation.layout.BoxWithConstraints
// import androidx.compose.foundation.layout.Column
// import androidx.compose.foundation.layout.Row
// import androidx.compose.foundation.layout.Spacer
// import androidx.compose.foundation.layout.fillMaxSize
// import androidx.compose.foundation.layout.fillMaxWidth
// import androidx.compose.foundation.layout.height
// import androidx.compose.foundation.layout.padding
// import androidx.compose.foundation.layout.size
// import androidx.compose.foundation.layout.width
// import androidx.compose.foundation.lazy.LazyColumn
// import androidx.compose.foundation.lazy.items
// import androidx.compose.foundation.shape.CircleShape
// import androidx.compose.foundation.shape.RoundedCornerShape
// import androidx.compose.foundation.text.BasicTextField
// import androidx.compose.foundation.text.KeyboardOptions
// import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.People
// import androidx.compose.material.icons.filled.Person
// import androidx.compose.material3.Icon
// import androidx.compose.material3.Text
// import androidx.compose.runtime.Composable
// import androidx.compose.runtime.LaunchedEffect
// import androidx.compose.runtime.getValue
// import androidx.compose.runtime.mutableStateOf
// import androidx.compose.runtime.remember
// import androidx.compose.runtime.setValue
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.draw.clip
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.graphics.SolidColor
// import androidx.compose.ui.layout.ContentScale
// import androidx.compose.ui.text.TextStyle
// import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.text.input.ImeAction
// import androidx.compose.ui.text.input.KeyboardType
// import androidx.compose.ui.text.style.TextAlign
// import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp
// import coil.compose.AsyncImage
// import com.sambhavdwivedi.callin.core.di.AppContainer
// import com.sambhavdwivedi.callin.data.remote.dto.PublicUserDto
// import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
// import com.sambhavdwivedi.callin.ui.theme.CallinColors
// import androidx.compose.foundation.gestures.detectTapGestures
// import androidx.compose.ui.input.pointer.pointerInput
// import androidx.compose.ui.platform.LocalFocusManager

// @Composable
// fun ContactsScreen(container: AppContainer) {
//     val focusManager = LocalFocusManager.current

//     var users by remember {
//         mutableStateOf<List<PublicUserDto>>(emptyList())
//     }

//     var isLoading by remember {
//         mutableStateOf(true)
//     }

//     var errorMessage by remember {
//         mutableStateOf<String?>(null)
//     }

//     var searchQuery by remember {
//         mutableStateOf("")
//     }

//     LaunchedEffect(Unit) {
//         container.userRepository.listUsers()
//             .onSuccess { loadedUsers ->
//                 users = loadedUsers
//             }
//             .onFailure { error ->
//                 errorMessage = error.message
//                     ?: "Could not load contacts."
//             }

//         isLoading = false
//     }

//     val filteredUsers = remember(users, searchQuery) {
//         if (searchQuery.isBlank()) {
//             users
//         } else {
//             val query = searchQuery
//                 .trim()
//                 .lowercase()

//             users.filter { user ->
//                 val name = user.display_name
//                     .orEmpty()
//                     .lowercase()

//                 val username = user.username
//                     .orEmpty()
//                     .lowercase()

//                 name.startsWith(query) ||
//                         username.startsWith(query)
//             }
//         }
//     }

//     Column(
//         modifier = Modifier
//             .fillMaxSize()
//             .pointerInput(Unit) {
//                 detectTapGestures {
//                     focusManager.clearFocus()
//                 }
//             }
//     ) {

//         ContactsHeader(
//             searchQuery = searchQuery,
//             onSearchChange = { searchQuery = it }
//         )

//         when {

//             isLoading -> {
//                 LoadingState()
//             }

//             errorMessage != null -> {
//                 EmptyState(errorMessage!!)
//             }

//             users.isEmpty() -> {
//                 EmptyState(
//                     "No other CALLIN users yet.\n" +
//                             "Invite a friend to get started."
//                 )
//             }

//             filteredUsers.isEmpty() &&
//                     searchQuery.isNotBlank() -> {

//                 EmptyState(
//                     "No contacts found for \"$searchQuery\"."
//                 )
//             }

//             else -> {
//                 LazyColumn(
//                     modifier = Modifier.fillMaxSize()
//                 ) {
//                     items(
//                         items = filteredUsers,
//                         key = { user ->
//                             user.username
//                                 ?: user.display_name
//                                 ?: ""
//                         }
//                     ) { user ->
//                         ContactRow(user)
//                     }
//                 }
//             }
//         }
//     }
// }

// @Composable
// private fun ContactsHeader(
//     searchQuery: String,
//     onSearchChange: (String) -> Unit
// ) {
//     BoxWithConstraints(
//         modifier = Modifier
//             .fillMaxWidth()
//             .padding(
//                 horizontal = 20.dp,
//                 vertical = 16.dp
//             )
//     ) {

//         val availableWidth = maxWidth

//         /*
//          * Responsive search width.
//          *
//          * PHONE
//          * -> compact fixed width
//          *
//          * TABLET
//          * -> wider, but not huge
//          *
//          * LARGE TABLET
//          * -> grows further
//          *
//          * The search box never fills the entire remaining width.
//          */

//         val searchWidth = when {
//             availableWidth < 600.dp -> {
//                 250.dp
//             }

//             availableWidth < 900.dp -> {
//                 320.dp
//             }

//             availableWidth < 1200.dp -> {
//                 390.dp
//             }

//             else -> {
//                 460.dp
//             }
//         }

//         Row(
//             modifier = Modifier.fillMaxWidth(),
//             verticalAlignment = Alignment.CenterVertically
//         ) {

//             /*
//              * TITLE
//              *
//              * Natural width only.
//              * Never stretches on tablet.
//              */
//             Row(
//                 verticalAlignment = Alignment.CenterVertically
//             ) {

//                 Icon(
//                     imageVector = Icons.Filled.People,
//                     contentDescription = null,
//                     tint = CallinColors.TextPrimary,
//                     modifier = Modifier.size(24.dp)
//                 )

//                 Spacer(
//                     modifier = Modifier.width(8.dp)
//                 )

//                 Text(
//                     text = "Contacts",
//                     color = CallinColors.TextPrimary,
//                     fontWeight = FontWeight.SemiBold,
//                     fontSize = 24.sp,
//                     maxLines = 1
//                 )
//             }

//             /*
//              * PHONE:
//              *
//              * Keep search close to title.
//              *
//              * TABLET:
//              *
//              * Push search completely toward the right edge.
//              */
//             if (availableWidth < 600.dp) {

//                 Spacer(
//                     modifier = Modifier.width(12.dp)
//                 )

//             } else {

//                 Spacer(
//                     modifier = Modifier.weight(1f)
//                 )
//             }

//             /*
//              * Search width is controlled independently.
//              *
//              * Right edge stays aligned with the header's
//              * right padding.
//              */
//             SearchBox(
//                 modifier = Modifier.width(searchWidth),
//                 value = searchQuery,
//                 onSearchChange = onSearchChange
//             )
//         }
//     }
// }

// @Composable
// private fun SearchBox(
//     modifier: Modifier = Modifier,
//     value: String,
//     onSearchChange: (String) -> Unit
// ) {
//     Box(
//         modifier = modifier
//             .height(40.dp)
//             .clip(
//                 RoundedCornerShape(20.dp)
//             )
//             .background(
//                 Color.Transparent
//             )
//             .border(
//                 width = 1.dp,
//                 color = CallinColors.TextSecondary.copy(
//                     alpha = 0.22f
//                 ),
//                 shape = RoundedCornerShape(20.dp)
//             )
//             .padding(
//                 horizontal = 12.dp
//             ),
//         contentAlignment = Alignment.CenterStart
//     ) {

//         BasicTextField(
//             value = value,
//             onValueChange = onSearchChange,
//             singleLine = true,
//             cursorBrush = SolidColor(
//                 CallinColors.TextSecondary
//             ),
//             keyboardOptions = KeyboardOptions(
//                 keyboardType = KeyboardType.Text,
//                 imeAction = ImeAction.Search
//             ),
//             textStyle = TextStyle(
//                 color = CallinColors.TextPrimary,
//                 fontSize = 14.sp,
//                 lineHeight = 18.sp
//             ),
//             modifier = Modifier
//                 .fillMaxWidth()
//                 .height(18.dp),
//             decorationBox = { innerTextField ->

//                 Box(
//                     modifier = Modifier.fillMaxWidth(),
//                     contentAlignment = Alignment.CenterStart
//                 ) {

//                     if (value.isEmpty()) {
//                         Text(
//                             text = "Search",
//                             color = CallinColors.TextSecondary,
//                             fontSize = 14.sp,
//                             lineHeight = 18.sp
//                         )
//                     }

//                     innerTextField()
//                 }
//             }
//         )
//     }
// }

// @Composable
// private fun ContactRow(
//     user: PublicUserDto
// ) {
//     Row(
//         modifier = Modifier
//             .fillMaxWidth()
//             .padding(
//                 horizontal = 20.dp,
//                 vertical = 10.dp
//             ),
//         verticalAlignment = Alignment.CenterVertically
//     ) {

//         Box(
//             modifier = Modifier
//                 .size(48.dp)
//                 .clip(CircleShape)
//                 .background(
//                     CallinColors.Background
//                 )
//                 .border(
//                     width = 1.dp,
//                     color = CallinColors.TextSecondary,
//                     shape = CircleShape
//                 ),
//             contentAlignment = Alignment.Center
//         ) {

//             if (!user.avatar_url.isNullOrBlank()) {

//                 AsyncImage(
//                     model = user.avatar_url,
//                     contentDescription = null,
//                     modifier = Modifier
//                         .size(48.dp)
//                         .clip(CircleShape),
//                     contentScale = ContentScale.Crop
//                 )

//             } else {

//                 Icon(
//                     imageVector = Icons.Filled.Person,
//                     contentDescription = null,
//                     tint = CallinColors.TextSecondary
//                 )
//             }
//         }

//         Spacer(
//             modifier = Modifier.width(14.dp)
//         )

//         Column {

//             Text(
//                 text = user.display_name
//                     ?: user.username
//                     ?: "CALLIN user",
//                 color = CallinColors.TextPrimary,
//                 fontWeight = FontWeight.Medium,
//                 fontSize = 16.sp
//             )

//             user.username?.let { username ->

//                 Text(
//                     text = "@$username",
//                     color = CallinColors.TextSecondary,
//                     fontSize = 13.sp
//                 )
//             }
//         }
//     }
// }

// @Composable
// private fun LoadingState() {
//     Box(
//         modifier = Modifier.fillMaxSize(),
//         contentAlignment = Alignment.Center
//     ) {
//         PulseBarsLoader(
//             barColor = CallinColors.TextSecondary
//         )
//     }
// }

// @Composable
// fun EmptyState(
//     message: String
// ) {
//     Box(
//         modifier = Modifier
//             .fillMaxSize()
//             .padding(32.dp),
//         contentAlignment = Alignment.Center
//     ) {
//         Text(
//             text = message,
//             color = CallinColors.TextSecondary,
//             fontSize = 14.sp,
//             textAlign = TextAlign.Center
//         )
//     }
// }

package com.sambhavdwivedi.callin.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.runtime.collectAsState
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
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionDto
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.theme.CallinColors
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Shows the caller's ACCEPTED connections only — never every CALLIN
 * user. Backed by ConnectionRepository's cache, so a screen visited
 * once shows data instantly on every later visit (no reload
 * flicker), while a background refresh keeps it current. Two users
 * who haven't connected never see each other here, by construction:
 * this screen never touches the "list all users" endpoint at all.
 */
@Composable
fun ContactsScreen(container: AppContainer) {
    val focusManager = LocalFocusManager.current

    val connections by container.connectionRepository.connections.collectAsState()
    var isLoading by remember { mutableStateOf(connections == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Cache already has last-known data (instant paint if
        // revisiting); this just refreshes it in the background.
        container.connectionRepository.refreshConnections()
            .onFailure { error ->
                if (connections == null) errorMessage = error.message ?: "Could not load contacts."
            }
        isLoading = false
    }

    val contacts = connections.orEmpty()

    // Search only kicks in at 2+ chars, matches username OR display name.
    val filtered = remember(contacts, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.length < 2) contacts
        else contacts.filter { c ->
            c.username.lowercase().contains(q) ||
                c.display_name.orEmpty().lowercase().contains(q)
        }
    }

    // Group alphabetically by display name (fallback username),
    // first letter only; missing letters simply don't render.
    val grouped = remember(filtered) {
        filtered
            .sortedBy { (it.display_name ?: it.username).lowercase() }
            .groupBy { (it.display_name ?: it.username).firstOrNull()?.uppercaseChar() ?: '#' }
            .toSortedMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            }
    ) {
        ContactsHeader(searchQuery = searchQuery, onSearchChange = { searchQuery = it })

        when {
            isLoading -> LoadingState()
            errorMessage != null -> EmptyState(errorMessage!!)
            contacts.isEmpty() -> EmptyState(
                "No contacts yet.\nScan a CALLIN QR code to connect with someone."
            )
            filtered.isEmpty() && searchQuery.trim().length >= 2 -> EmptyState(
                "No contacts found for \"$searchQuery\"."
            )
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (letter, people) ->
                        item(key = "header_$letter") {
                            Text(
                                text = letter.toString(),
                                color = CallinColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(
                                    horizontal = 20.dp,
                                    vertical = 0.dp
                                )
                            )
                        }
                        items(items = people, key = { it.user_id }) { contact ->
                            ContactRow(contact = contact, onCall = { /* Phase 2: start call */ })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsHeader(searchQuery: String, onSearchChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                fontSize = 24.sp,
                maxLines = 1
            )
        }

        SearchBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            value = searchQuery,
            onSearchChange = onSearchChange
        )
    }
}

@Composable
private fun SearchBox(modifier: Modifier = Modifier, value: String, onSearchChange: (String) -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Transparent)
            .border(1.dp, CallinColors.TextSecondary.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onSearchChange,
            singleLine = true,
            cursorBrush = SolidColor(CallinColors.TextSecondary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            textStyle = TextStyle(color = CallinColors.TextPrimary, fontSize = 14.sp, lineHeight = 18.sp),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("Search contacts", color = CallinColors.TextSecondary, fontSize = 14.sp)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun ContactRow(contact: ConnectionDto, onCall: () -> Unit) {
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
            if (!contact.avatar_url.isNullOrBlank()) {
                AsyncImage(
                    model = contact.avatar_url,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = CallinColors.TextSecondary)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.display_name ?: contact.username,
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Text(
                text = "@${contact.username}",
                color = CallinColors.TextSecondary,
                fontSize = 13.sp
            )
        }

        RippleCallIcon(onClick = onCall)
    }
}

/** White call icon with a brief circular ripple burst from its own center on tap. */
@Composable
private fun RippleCallIcon(onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val rippleAlpha = remember { Animatable(0f) }
    val rippleScale = remember { Animatable(0.3f) }

    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = rippleAlpha.value * 0.25f))
                .then(
                    Modifier // scale visual via graphicsLayer would be smoother; kept simple here
                )
        )
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = "Call",
            tint = Color.White,
            modifier = Modifier
                .size(22.dp)
                .clickable {
                    scope.launch {
                        rippleAlpha.snapTo(1f)
                        rippleScale.snapTo(0.3f)
                        rippleScale.animateTo(1.4f, tween(350))
                    }
                    scope.launch {
                        rippleAlpha.animateTo(0f, tween(380))
                    }
                    onClick()
                }
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PulseBarsLoader(barColor = CallinColors.TextSecondary)
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
