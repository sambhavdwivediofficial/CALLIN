package com.sambhavdwivedi.callin.ui.qr

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.ui.theme.CallinColors

private const val QR_SCHEME = "callin"

/**
 * Encodes "callin:<username>" rather than the bare username, so
 * ScanQrScreen can tell a genuine CALLIN QR code apart from any
 * other QR code someone might accidentally point the scanner at.
 */
fun callinQrPayload(username: String): String = "$QR_SCHEME:$username"

fun usernameFromQrPayload(payload: String): String? {
    if (!payload.startsWith("$QR_SCHEME:")) return null
    val username = payload.removePrefix("$QR_SCHEME:").trim()
    return username.ifBlank { null }
}

/**
 * The user's own QR code, so someone else can scan it (via
 * ScanQrScreen) to send a connection request straight to this
 * account.
 */
@Composable
fun MyQrCodeScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    var username by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        container.userRepository.getMe()
            .onSuccess { me ->
                username = me.username
                displayName = me.display_name
            }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallinColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CallinColors.TextPrimary
                    )
                }
                Text(
                    text = "My QR Code",
                    color = CallinColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val currentUsername = username

                when {
                    isLoading -> CircularProgressIndicator(color = CallinColors.TextSecondary)

                    currentUsername.isNullOrBlank() -> Text(
                        text = "Your QR code isn't ready yet.",
                        color = CallinColors.TextSecondary,
                        fontSize = 14.sp
                    )

                    else -> {
                        val qrBitmap = remember(currentUsername) {
                            generateQrBitmap(callinQrPayload(currentUsername), 640)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .padding(20.dp)
                            ) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "Your CALLIN QR code",
                                        modifier = Modifier.size(240.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = displayName ?: "—",
                                color = CallinColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "@$currentUsername",
                                color = CallinColors.TextSecondary,
                                fontSize = 14.sp
                            )

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "Let someone scan this to add you on CALLIN.",
                                color = CallinColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    bitmap
}.getOrNull()
