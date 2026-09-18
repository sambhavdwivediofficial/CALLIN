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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Dark / Blue visual palette
// ─────────────────────────────────────────────────────────────

private val Ink0 = Color(0xFF01050B)
private val Ink1 = Color(0xFF030A14)
private val Ink2 = Color(0xFF071323)

private val Beam = Color(0xFF287FD8)
private val ButtonBlue = Color(0xFF2478D4)

private val FieldBg = Color(0xFF0A1628)

private val FieldBorder = Color(0xFF234A72)
private val FieldBorderFocused = Color(0xFF338FEA)

private val SecondaryText = Color(0xFF9EB2CC)
private val MutedText = Color(0xFF71849E)
private val DisabledText = Color(0xFF71839B)

private val ErrorRed = Color(0xFFFF6B6B)

/**
 * Shown exactly once per account, right after a first-time Google
 * Sign-In. Email is locked (it came from Google); username, once
 * submitted here, is locked forever too — the backend refuses to
 * run this a second time.
 */
@Composable
fun CompleteProfileScreen(
    container: AppContainer,
    onCompleted: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var isLoadingEmail by remember { mutableStateOf(true) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
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

    // ─────────────────────────────────────────────────────────
    // Field colors
    // ─────────────────────────────────────────────────────────

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = FieldBg,
        unfocusedContainerColor = FieldBg,
        disabledContainerColor = FieldBg,

        focusedBorderColor = FieldBorderFocused,
        unfocusedBorderColor = FieldBorder,
        disabledBorderColor = FieldBorder,

        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = DisabledText,

        cursorColor = Beam,

        focusedLabelColor = FieldBorderFocused,
        unfocusedLabelColor = MutedText,

        focusedPlaceholderColor = MutedText,
        unfocusedPlaceholderColor = MutedText
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Ink2,
                        Ink1,
                        Ink0
                    ),
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

            Spacer(Modifier.height(32.dp))

            // ─────────────────────────────────────────────────
            // Header
            // ─────────────────────────────────────────────────

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

            Spacer(Modifier.height(32.dp))

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
                    onValueChange = {
                        firstName = it
                    },
                    label = {
                        Text("First name")
                    },
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
                    onValueChange = {
                        lastName = it
                    },
                    label = {
                        Text("Last name")
                    },
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
                        .filter {
                            it.isLetterOrDigit() || it == '_'
                        }
                        .take(32)
                },
                label = {
                    Text("Username")
                },
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
                firstName.isNotBlank() &&
                        lastName.isNotBlank() &&
                        username.length >= 3

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

                    // Keep the button visually present even while
                    // validation/loading disables it.
                    disabledContainerColor = ButtonBlue,
                    disabledContentColor = Color.White
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
