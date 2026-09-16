package com.sambhavdwivedi.callin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sambhavdwivedi.callin.ui.theme.CallinTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            CallinTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
    } else {
        WelcomeScreen()
    }
}

// ---------- colors matching the web version ----------
private val Ink0 = Color(0xFF03060E)
private val Ink1 = Color(0xFF081324)
private val Ink2 = Color(0xFF0C1E38)
private val Beam = Color(0xFF2E90FF)
private val BeamSoft = Color(0xFF59B0FF)
private val BeamB = Color(0xFF0B5ED8)
private val WordColor = Color(0xFFDCEBFF)

@Composable
fun SplashScreen(onFinished: () -> Unit) {

    val haloAlpha = remember { Animatable(0f) }
    val haloScale = remember { Animatable(0.2f) }

    val blobAlpha = remember { Animatable(0f) }
    val blobRotY = remember { Animatable(-78f) }
    val blobScale = remember { Animatable(0.66f) }

    val podAlpha = remember { Animatable(0f) }
    val podRotY = remember { Animatable(72f) }
    val podScale = remember { Animatable(0.55f) }
    val podOffsetX = remember { Animatable(28f) }
    val podOffsetY = remember { Animatable(-48f) }

    val arcInAlpha = remember { Animatable(0f) }
    val arcInScale = remember { Animatable(0.25f) }
    val arcOutAlpha = remember { Animatable(0f) }
    val arcOutScale = remember { Animatable(0.25f) }

    val wordAlpha = remember { Animatable(0f) }
    val wordOffsetY = remember { Animatable(16f) }

    val easeOutBack = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    LaunchedEffect(Unit) {
        // halo
        launch {
            haloAlpha.animateTo(0.68f, tween(700, easing = FastOutSlowInEasing))
        }
        launch {
            haloScale.animateTo(1f, tween(900, easing = easeOutBack))
        }

        // blob — starts almost immediately
        launch {
            delay(80)
            blobAlpha.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        launch {
            delay(80)
            blobRotY.animateTo(0f, tween(950, easing = easeOutBack))
        }
        launch {
            delay(80)
            blobScale.animateTo(1f, tween(950, easing = easeOutBack))
        }

        // pod — a bit after the blob
        launch {
            delay(420)
            podAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
        launch {
            delay(420)
            podRotY.animateTo(0f, tween(900, easing = easeOutBack))
        }
        launch {
            delay(420)
            podScale.animateTo(1f, tween(900, easing = easeOutBack))
        }
        launch {
            delay(420)
            podOffsetX.animateTo(0f, tween(900, easing = easeOutBack))
        }
        launch {
            delay(420)
            podOffsetY.animateTo(0f, tween(900, easing = easeOutBack))
        }

        // wifi arcs — pop in one after another
        launch {
            delay(900)
            arcInAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
        launch {
            delay(900)
            arcInScale.animateTo(1f, tween(420, easing = easeOutBack))
        }
        launch {
            delay(1060)
            arcOutAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
        launch {
            delay(1060)
            arcOutScale.animateTo(1f, tween(420, easing = easeOutBack))
        }

        // wordmark
        launch {
            delay(1550)
            wordAlpha.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        launch {
            delay(1550)
            wordOffsetY.animateTo(0f, tween(650, easing = easeOutBack))
        }

        // hold, then hand off to the real app
        delay(2900)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Ink2, Ink1, Ink0),
                    radius = 1400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        // drifting glow orbs
        Box(
            Modifier
                .size(260.dp)
                .offset(x = (-90).dp, y = (-220).dp)
                .clip(CircleShape)
                .background(Beam.copy(alpha = 0.28f))
                .blur(70.dp)
        )
        Box(
            Modifier
                .size(220.dp)
                .offset(x = 110.dp, y = 230.dp)
                .clip(CircleShape)
                .background(BeamB.copy(alpha = 0.24f))
                .blur(70.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.statusBarsPadding()
        ) {

            Box(
                modifier = Modifier.size(190.dp),
                contentAlignment = Alignment.Center
            ) {

                // halo glow behind the logo
                Box(
                    Modifier
                        .size(280.dp)
                        .graphicsLayer {
                            alpha = haloAlpha.value
                            scaleX = haloScale.value
                            scaleY = haloScale.value
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Beam.copy(alpha = 0.55f),
                                    Beam.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                        .blur(22.dp)
                )

                // blob (main logo shape)
                Image(
                    painter = painterResource(id = R.drawable.logo_blob),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(190.dp)
                        .graphicsLayer {
                            alpha = blobAlpha.value
                            rotationY = blobRotY.value
                            scaleX = blobScale.value
                            scaleY = blobScale.value
                            cameraDistance = 14 * density
                        }
                )

                // pod (small capsule)
                Image(
                    painter = painterResource(id = R.drawable.logo_pod),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(190.dp)
                        .graphicsLayer {
                            alpha = podAlpha.value
                            rotationY = podRotY.value
                            scaleX = podScale.value
                            scaleY = podScale.value
                            translationX = podOffsetX.value
                            translationY = podOffsetY.value
                            cameraDistance = 14 * density
                        }
                )

                // inner wifi arc
                Image(
                    painter = painterResource(id = R.drawable.logo_arc_in),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(190.dp)
                        .graphicsLayer {
                            alpha = arcInAlpha.value
                            scaleX = arcInScale.value
                            scaleY = arcInScale.value
                        }
                )

                // outer wifi arc
                Image(
                    painter = painterResource(id = R.drawable.logo_arc_out),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(190.dp)
                        .graphicsLayer {
                            alpha = arcOutAlpha.value
                            scaleX = arcOutScale.value
                            scaleY = arcOutScale.value
                        }
                )
            }

            // Callin
            Text(
                text = "",
                color = WordColor,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Light,
                fontSize = 30.sp,
                letterSpacing = 5.sp,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .graphicsLayer {
                        alpha = wordAlpha.value
                        translationY = wordOffsetY.value
                    }
            )
        }
    }
}

// ---------- your existing welcome screen, unchanged ----------

private val WelcomeFont = FontFamily(
    Font(
        resId = R.font.montserrat_extra_bold,
        weight = FontWeight.ExtraBold
    )
)

private val CallinFont = FontFamily(
    Font(
        resId = R.font.great_vibes_regular,
        weight = FontWeight.Normal
    )
)

@Composable
fun WelcomeScreen() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Text(
            text = "Welcome",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 28.dp),
            color = Color.White,
            fontFamily = WelcomeFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 48.sp,
            letterSpacing = 1.sp
        )

        Text(
            text = "Hello Callin!",
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 4.dp),
            color = Color.White,
            fontFamily = CallinFont,
            fontWeight = FontWeight.Normal,
            fontSize = 40.sp,
            letterSpacing = 0.sp
        )
    }
}
