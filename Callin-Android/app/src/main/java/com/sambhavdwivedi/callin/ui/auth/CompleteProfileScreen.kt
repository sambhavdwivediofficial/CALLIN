package com.sambhavdwivedi.callin.ui.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.ui.components.AvatarPicker
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Palette: near-black / grey, blue reserved for focus + button
// ─────────────────────────────────────────────────────────────

private val Ink0 = Color(0xFF000000)
private val Ink1 = Color(0xFF0A0A0A)
private val Ink2 = Color(0xFF141414)

private val ButtonBlue = Color(0xFF2478D4)
private val FocusBlue = Color(0xFF338FEA)

private val FieldBg = Color(0xFF171717)
private val FieldBorder = Color(0xFF2C2C2C)

private val SecondaryText = Color(0xFFA6A6A6)
private val MutedText = Color(0xFF767676)
private val DisabledText = Color(0xFF6B6B6B)

private val ErrorRed = Color(0xFFFF6B6B)
private val SuccessGreen = Color(0xFF4CD97B)

private enum class UsernameStatus { Idle, Checking, Available, Taken }

private fun extensionForMime(mimeType: String): String = when (mimeType) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "jpg"
}

/**
 * Shown exactly once per account, right after a first-time Google
 * Sign-In. Email is locked (it came from Google); username, once
 * submitted here, is locked forever too — the backend refuses to
 * run this a second time.
 */
@Composable
fun CompleteProfileScreen(
    container: AppContainer,
    onCompleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var isLoadingEmail by remember { mutableStateOf(value = true) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(value = false) }

    var usernameStatus by remember { mutableStateOf(UsernameStatus.Idle) }

    var isSubmitting by remember { mutableStateOf(value = false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.userRepository.getMe()
            .onSuccess { me ->
                email = me.email
                isLoadingEmail = false
            }
            .onFailure {
                isLoadingEmail = false
            }
    }

    // Debounced live username availability check.
    LaunchedEffect(username) {
        if (username.length < 3) {
            usernameStatus = UsernameStatus.Idle
            return@LaunchedEffect
        }
        usernameStatus = UsernameStatus.Checking
        delay(500)
        container.userRepository.checkUsername(username)
            .onSuccess { available ->
                usernameStatus = if (available) UsernameStatus.Available else UsernameStatus.Taken
            }
            .onFailure { usernameStatus = UsernameStatus.Idle }
    }

    val initials = buildString {
        if (firstName.isNotBlank()) {
            append(firstName.first().uppercaseChar())
            if (lastName.isNotBlank()) append(lastName.first().uppercaseChar())
        }
    }

    // ─────────────────────────────────────────────────────────
    // Field colors
    // ─────────────────────────────────────────────────────────

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = FieldBg,
        unfocusedContainerColor = FieldBg,
        disabledContainerColor = FieldBg,

        focusedBorderColor = FocusBlue,
        unfocusedBorderColor = FieldBorder,
        disabledBorderColor = FieldBorder,

        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = DisabledText,

        cursorColor = FocusBlue,

        focusedLabelColor = FocusBlue,
        unfocusedLabelColor = MutedText,

        focusedPlaceholderColor = MutedText,
        unfocusedPlaceholderColor = MutedText
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Ink2, Ink1, Ink0),
                    radius = 1250f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Complete your profile",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "One last step before you start calling.",
                color = SecondaryText,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            // ─────────────────────────────────────────────────
            // Avatar
            // ─────────────────────────────────────────────────

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AvatarPicker(
                    initials = initials,
                    avatarUrl = avatarUrl,
                    isUploading = isUploadingAvatar,
                    onImagePicked = { uri, mimeType ->
                        scope.launch {
                            isUploadingAvatar = true
                            try {
                                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    val bytes = inputStream.readBytes()
                                    container.userRepository
                                        .uploadAvatar(bytes, mimeType, "avatar.${extensionForMime(mimeType)}")
                                        .onSuccess { url -> avatarUrl = url }
                                        .onFailure { e ->
                                            errorMessage = e.message ?: "Could not upload photo. Try again."
                                        }
                                }
                            } finally {
                                isUploadingAvatar = false
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ─────────────────────────────────────────────────
            // Email
            // ─────────────────────────────────────────────────

            OutlinedTextField(
                value = email,
                onValueChange = {},
                enabled = false,
                readOnly = true,
                label = {
                    Text(
                        text = if (isLoadingEmail) "Loading…" else "Email"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors
            )

            Spacer(Modifier.height(16.dp))

            // ─────────────────────────────────────────────────
            // First + Last name
            // ─────────────────────────────────────────────────

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { input -> firstName = input.take(10) },
                    label = { RequiredLabel("First name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { input -> lastName = input.take(10) },
                    label = { RequiredLabel("Last name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ─────────────────────────────────────────────────
            // Username
            // ─────────────────────────────────────────────────

            OutlinedTextField(
                value = username,
                onValueChange = { input ->
                    username = input
                        .lowercase()
                        .filter { it.isLetter() || it.isDigit() || (it == '_') || (it == '-') }
                        .take(18)
                },
                label = { RequiredLabel("Username") },
                placeholder = {
                    Text(
                        text = "this can never be changed later",
                        color = Color(0xFF50647F),
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            when (usernameStatus) {
                UsernameStatus.Available -> Text(
                    text = "Available",
                    color = SuccessGreen,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
                UsernameStatus.Taken -> Text(
                    text = "Already taken",
                    color = ErrorRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
                else -> {}
            }

            // ─────────────────────────────────────────────────
            // Error
            // ─────────────────────────────────────────────────

            errorMessage?.let {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = it,
                    color = ErrorRed,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // ─────────────────────────────────────────────────
            // Validation
            // ─────────────────────────────────────────────────

            val isValid =
                (firstName.length in 2..10) &&
                        (lastName.length in 2..10) &&
                        (username.length in 3..18) &&
                        (usernameStatus == UsernameStatus.Available)

            // ─────────────────────────────────────────────────
            // Continue button
            // ─────────────────────────────────────────────────

            Button(
                onClick = {
                    errorMessage = null
                    isSubmitting = true

                    scope.launch {
                        container.userRepository
                            .completeProfile(
                                username.trim().lowercase(),
                                firstName.trim(),
                                lastName.trim()
                            )
                            .onSuccess {
                                isSubmitting = false
                                onCompleted()
                            }
                            .onFailure { e ->
                                isSubmitting = false
                                errorMessage =
                                    e.message
                                        ?: "Could not save your profile. Try again."
                            }
                    }
                },
                enabled = isValid && !isSubmitting,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonBlue,
                    contentColor = Color.White,
                    disabledContainerColor = ButtonBlue.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSubmitting) {
                    PulseBarsLoader(
                        size = 26.dp,
                        barColor = Color.White
                    )
                } else {
                    Text(
                        text = "Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** A field label with a small, thin white asterisk marking it required. */
@Composable
private fun RequiredLabel(text: String) {
    Row {
        Text(text)
        Text(
            text = " *",
            color = Color.White,
            fontWeight = FontWeight.Light,
            fontSize = 12.sp,
        )
    }
}
