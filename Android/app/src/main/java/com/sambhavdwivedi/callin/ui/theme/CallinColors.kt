package com.sambhavdwivedi.callin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * CALLIN's design language, everywhere in the app from here on:
 * background, white for primary content, and this soft blue-grey
 * for secondary/muted content and borders. That's it — three colors.
 * Red/green (or anything else) only shows up for specific, sparing
 * purposes (end-call, errors, success) — never as a general theme
 * color. New screens should reference these constants instead of
 * hardcoding hex values, so the whole app stays visually consistent
 * by construction.
 */
object CallinColors {
    val Background = Color(0xFF03060E)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFAFC3DE)

    // Sparing, purpose-specific accents — not general theme colors.
    val Danger = Color(0xFFFF6B6B)
    val Success = Color(0xFF4CD97B)
}
