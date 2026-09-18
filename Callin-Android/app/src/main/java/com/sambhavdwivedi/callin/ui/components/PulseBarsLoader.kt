package com.sambhavdwivedi.callin.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 12-bar radial pulse loader (fading spokes chasing around a
 * circle) — our branded loading indicator, used anywhere a button
 * needs to show "working" without swapping colors or layout.
 */
@Composable
fun PulseBarsLoader(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    barColor: Color = Color.Black
) {
    val barCount = 12
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_bars")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "pulse_bars_progress"
    )

    val density = LocalDensity.current
    val radiusPx = with(density) { (size / 2.6f).toPx() }
    val barWidth = size * 0.16f
    val barHeight = size * 0.34f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        for (i in 0 until barCount) {
            val angleDeg = i * 30f
            val phase = i.toFloat() / barCount
            val t = ((progress - phase) % 1f + 1f) % 1f
            val alpha = (1f - t).coerceIn(0.22f, 1f)

            val angleRad = Math.toRadians(angleDeg.toDouble())
            val offsetX = (radiusPx * sin(angleRad)).toFloat()
            val offsetY = (-radiusPx * cos(angleRad)).toFloat()

            Box(
                modifier = Modifier
                    .size(width = barWidth, height = barHeight)
                    .graphicsLayer {
                        translationX = offsetX
                        translationY = offsetY
                        rotationZ = angleDeg
                        this.alpha = alpha
                    }
                    .background(barColor, RoundedCornerShape(50))
            )
        }
    }
}
