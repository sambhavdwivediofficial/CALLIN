package com.sambhavdwivedi.callin.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.offset

/**
 * The circular profile-photo picker used on the complete-profile
 * screen: shows initials or a placeholder person icon until a photo
 * is chosen, a loader while it's uploading, and the final image once
 * it's saved. The pencil badge opens Android's system photo picker —
 * no storage permission needed, since the Photo Picker only ever
 * hands back the one image the user chose.
 */
@Composable
fun AvatarPicker(
    initials: String,
    avatarUrl: String?,
    isUploading: Boolean,
    onImagePicked: (uri: Uri, mimeType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var sizeError by remember { mutableStateOf<String?>(null) }

    // The locally selected image is shown immediately.
    // This prevents an old cached server image from remaining visible
    // after the user selects a new avatar.
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)

                    if (sizeIndex != -1) {
                        val sizeBytes = it.getLong(sizeIndex)

                        if (sizeBytes > 10 * 1024 * 1024) {
                            sizeError =
                                "Avatar image size must not exceed 10 MB."
                            return@rememberLauncherForActivityResult
                        }
                    }
                }
            }

            sizeError = null

            // Immediately display the newly selected image locally.
            selectedImageUri = uri

            val mimeType =
                context.contentResolver.getType(uri) ?: "image/jpeg"

            onImagePicked(uri, mimeType)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(116.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.TopStart)
                    .offset(y = 6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF03060E))
                    .border(
                        1.dp,
                        Color(0xFFAFC3DE),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isUploading -> {
                        PulseBarsLoader(
                            size = 28.dp,
                            barColor = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    selectedImageUri != null || !avatarUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = selectedImageUri ?: avatarUrl,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    initials.isNotEmpty() -> {
                        Crossfade(
                            targetState = initials,
                            label = "avatar_initials"
                        ) { text ->
                            Text(
                                text = text,
                                color = Color(0xFFF5F9FF),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 30.sp
                            )
                        }
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF0A1624))
                    .border(
                        1.dp,
                        Color(0xFFAFC3DE),
                        CircleShape
                    )
                    .clickable {
                        pickMedia.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Change photo",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        sizeError?.let {
            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
