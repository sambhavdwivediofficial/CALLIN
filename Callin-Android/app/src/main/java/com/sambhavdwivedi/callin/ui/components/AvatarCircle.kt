package com.sambhavdwivedi.callin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sambhavdwivedi.callin.ui.theme.CallinColors

/** First letter of the first two words of the display name (e.g.
 * "Sambhav Dwivedi" -> "SD"), falling back to the username's first
 * letter. Identical logic everywhere in the app, so once a user
 * skips the photo, the same two letters show up on their own
 * Profile, in everyone's Contacts, in Recents, and on the Call
 * screen — never a generic person icon. */
fun initialsFor(displayName: String?, username: String? = null): String {
    val words = displayName?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
    val fromName = when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(1)
        else -> ""
    }.uppercase()
    if (fromName.isNotBlank()) return fromName
    return username?.trim()?.take(1)?.uppercase().orEmpty()
}

@Composable
fun AvatarCircle(
    avatarUrl: String?,
    displayName: String?,
    username: String? = null,
    size: Dp,
    borderAlpha: Float = 1f,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CallinColors.Background)
            .border(1.dp, CallinColors.TextSecondary.copy(alpha = borderAlpha), CircleShape),
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
            val initials = initialsFor(displayName, username)
            if (initials.isNotBlank()) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value / 2.6f).sp
                )
            }
        }
    }
}
