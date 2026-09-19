package com.sambhavdwivedi.callin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CallinColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * CALLIN is always dark-themed — no light mode, and no per-device
 * Material You dynamic color. Every screen sets its own exact
 * colors regardless of this scheme, but keeping it fixed (instead of
 * deriving from the system wallpaper on Android 12+) is what makes
 * the app look identical on every device and OS version, which is
 * the point of a designed, branded app rather than a system-themed
 * one.
 */
@Composable
fun CallinTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CallinColorScheme,
        typography = Typography,
        content = content
    )
}
