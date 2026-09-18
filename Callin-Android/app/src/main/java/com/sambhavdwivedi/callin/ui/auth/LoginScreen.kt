package com.sambhavdwivedi.callin.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import kotlinx.coroutines.launch

// From Google Cloud Console → APIs & Services → Credentials → the
// OAuth client of type "Web application" (NOT the Android one). The
// backend's GOOGLE_WEB_CLIENT_ID must be this exact same value.
private const val WEB_CLIENT_ID = "925911711375-7jrmmuilsijjinig8ktml2cva0uejqm6.apps.googleusercontent.com"

private val Ink0 = Color(0xFF000000)
private val Ink1 = Color(0xFF081324)
private val Ink2 = Color(0xFF0C1E38)
private val Beam = Color(0xFF2E90FF)

@Composable
fun LoginScreen(
    container: AppContainer,
    onSignedIn: (needsProfile: Boolean) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = listOf(Ink2, Ink1, Ink0), radius = 1400f))
    ) {
        Box(
            Modifier
                .size(260.dp)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-100).dp)
                .clip(CircleShape)
                .background(Beam.copy(alpha = 0.20f))
                .blur(90.dp)
        )
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 100.dp)
                .clip(CircleShape)
                .background(Beam.copy(alpha = 0.16f))
                .blur(90.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Text(
                text = "CALLIN",
                color = Color.White,
                fontWeight = FontWeight.Light,
                fontSize = 34.sp,
                letterSpacing = 6.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Crystal-clear calls over the internet.\nNo minutes, no carrier — just Wi-Fi or data.",
                color = Color(0xFFAFC3DE),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(40.dp))

            listOf(
                "Crystal-clear voice, powered by WebRTC",
                "Rings like a real call — even in the background",
                "Private and encrypted, end to end"
            ).forEach { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Beam)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = line, color = Color(0xFFDCEBFF), fontSize = 14.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            errorMessage?.let {
                Text(
                    text = it,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    scope.launch {
                        val outcome = signInWithGoogle(context as Activity, container)
                        isLoading = false
                        outcome
                            .onSuccess { needsProfile -> onSignedIn(needsProfile) }
                            .onFailure { e -> errorMessage = e.message ?: "Sign-in failed. Please try again." }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F),
                    disabledContainerColor = Color.White,
                    disabledContentColor = Color(0xFF1F1F1F)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isLoading) {
                    PulseBarsLoader(size = 28.dp, barColor = Color.Black)
                } else {
                    Text(text = "Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            val legalText = buildAnnotatedString {
                append("By continuing you agree to CALLIN's ")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "terms",
                        styles = TextLinkStyles(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold))
                    ) { onOpenTerms() }
                ) {
                    append("Terms")
                }
                append(" & ")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "privacy",
                        styles = TextLinkStyles(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold))
                    ) { onOpenPrivacy() }
                ) {
                    append("Privacy Policy")
                }
                append(".")
            }

            Text(
                text = legalText,
                color = Color(0xFF6E7F99),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

private suspend fun signInWithGoogle(activity: Activity, container: AppContainer): Result<Boolean> {
    return try {
        val option = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credentialManager = CredentialManager.create(activity)
        val result = credentialManager.getCredential(activity, request)

        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        container.authRepository.googleSignIn(googleCredential.idToken)
    } catch (e: GetCredentialException) {
        Result.failure(Exception("Google sign-in was cancelled or failed."))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
