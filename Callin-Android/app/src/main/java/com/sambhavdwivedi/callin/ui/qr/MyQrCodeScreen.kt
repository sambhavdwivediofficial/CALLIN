package com.sambhavdwivedi.callin.ui.qr

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.theme.CallinColors

/** Every CALLIN QR code encodes this scheme so the scanner can tell
 * a genuine CALLIN contact code apart from any other QR code. */
private const val CALLIN_QR_SCHEME = "callin://user/"

fun callinQrContentFor(username: String): String = "$CALLIN_QR_SCHEME$username"

/** Extracts the username from a scanned CALLIN QR payload, or null if it isn't one. */
fun usernameFromCallinQr(rawValue: String?): String? {
    if (rawValue == null || !rawValue.startsWith(CALLIN_QR_SCHEME)) return null
    val username = rawValue.removePrefix(CALLIN_QR_SCHEME).trim()
    return username.ifBlank { null }
}

private fun buildQrBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

/**
 * Shows the signed-in user's own username encoded as a QR code, so
 * another CALLIN user can scan it (via ScanQrScreen) to send them a
 * connection request instantly. Rendered on a white card since QR
 * codes need light-on-dark contrast reversed from the rest of the app.
 */
@Composable
fun MyQrCodeScreen(container: AppContainer, onBack: () -> Unit) {
    var username by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.userRepository.getMe()
            .onSuccess { me ->
                username = me.username
                displayName = me.display_name
                if (me.username.isNullOrBlank()) {
                    errorMessage = "Your profile doesn't have a username yet."
                }
            }
            .onFailure {
                errorMessage = it.message ?: "Could not load your QR code."
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
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "My QR Code",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        PulseBarsLoader(barColor = CallinColors.TextSecondary)
                    }

                    errorMessage != null -> {
                        Text(
                            text = errorMessage!!,
                            color = CallinColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    username != null -> {
                        val qrContent = remember(username) { callinQrContentFor(username!!) }
                        val bitmap = remember(qrContent) { buildQrBitmap(qrContent, 720) }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .padding(20.dp)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Your CALLIN QR code",
                                    modifier = Modifier.size(240.dp)
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            Text(
                                text = displayName ?: "",
                                color = CallinColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "@$username",
                                color = CallinColors.TextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = "Let another CALLIN user scan this to connect with you instantly.",
                                color = CallinColors.TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
