package com.sambhavdwivedi.callin.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sambhavdwivedi.callin.ui.theme.CallinColors

/**
 * Placeholder until real calling (signaling + WebRTC) is wired up —
 * once calls actually happen, this becomes the call-history list
 * backed by GET /api/v1/calls, which the backend already exposes.
 */
@Composable
fun RecentsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
        // .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = CallinColors.TextPrimary
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "Recents",
                color = CallinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp
            )
        }

        EmptyState(
            "No recent calls yet.\nThis will fill up once calling is live."
        )
    }
}
