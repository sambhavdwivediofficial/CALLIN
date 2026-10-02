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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sambhavdwivedi.callin.ui.theme.CallinTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sambhavdwivedi.callin.data.repository.CallUiState
import com.sambhavdwivedi.callin.ui.navigation.CallinNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
    val context = LocalContext.current
    val container = (context.applicationContext as CallinApplication).container

    // Skip the splash animation entirely whenever a call is already
    // ringing/connecting/active by the time this Activity is
    // created — tapping the incoming-call notification, the
    // full-screen intent waking the device, or a cold start right
    // after an FCM push all land here. CallinNavHost's own call-state
    // watcher then routes straight to the Call screen with no extra
    // wiring needed.
    var showSplash by remember {
        mutableStateOf(container.callRepository.state.value is CallUiState.Idle)
    }

    val callState by container.callRepository.state.collectAsState()

    // Lets the Call screen draw directly over the lock screen and
    // wake the display the instant a call is ringing — and ONLY
    // then; the flags are cleared the moment the call ends so the
    // rest of the app never bypasses the lock screen.
    LaunchedEffect(callState) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val isIdle = callState is CallUiState.Idle
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(!isIdle)
            activity.setTurnScreenOn(!isIdle)
        } else {
            @Suppress("DEPRECATION")
            if (!isIdle) {
                activity.window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            } else {
                activity.window.clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val token = container.tokenStore.getAccessToken()
        if (!token.isNullOrBlank()) {
            val imageLoader = coil.Coil.imageLoader(context)
            fun warm(url: String?) {
                if (url.isNullOrBlank()) return
                imageLoader.enqueue(coil.request.ImageRequest.Builder(context).data(url).build())
            }

            kotlinx.coroutines.coroutineScope {
                launch {
                    container.userRepository.getMe().onSuccess { warm(it.avatar_url) }
                }
                launch {
                    container.connectionRepository.refreshConnections()
                        .onSuccess { list -> list.forEach { warm(it.avatar_url) } }
                }
                launch {
                    container.connectionRepository.refreshPending()
                        .onSuccess { list -> list.forEach { warm(it.from_avatar_url) } }
                }
                launch { container.connectionRepository.refreshHistory() }
            }
        }
    }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
    } else {
        CallinNavHost()
    }
}

// ---------- colors matching the web version ----------
private val Ink0 = Color(0xFF000000)
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
        launch { haloAlpha.animateTo(0.68f, tween(700, easing = FastOutSlowInEasing)) }
        launch { haloScale.animateTo(1f, tween(900, easing = easeOutBack)) }

        launch { delay(80); blobAlpha.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        launch { delay(80); blobRotY.animateTo(0f, tween(950, easing = easeOutBack)) }
        launch { delay(80); blobScale.animateTo(1f, tween(950, easing = easeOutBack)) }

        launch { delay(420); podAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { delay(420); podRotY.animateTo(0f, tween(900, easing = easeOutBack)) }
        launch { delay(420); podScale.animateTo(1f, tween(900, easing = easeOutBack)) }
        launch { delay(420); podOffsetX.animateTo(0f, tween(900, easing = easeOutBack)) }
        launch { delay(420); podOffsetY.animateTo(0f, tween(900, easing = easeOutBack)) }

        launch { delay(900); arcInAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { delay(900); arcInScale.animateTo(1f, tween(420, easing = easeOutBack)) }
        launch { delay(1060); arcOutAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { delay(1060); arcOutScale.animateTo(1f, tween(420, easing = easeOutBack)) }

        launch { delay(1550); wordAlpha.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        launch { delay(1550); wordOffsetY.animateTo(0f, tween(650, easing = easeOutBack)) }

        delay(2900)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink0),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(260.dp)
                .offset(x = (-90).dp, y = (-220).dp)
                .clip(CircleShape)
                .background(BeamB.copy(alpha = 0.28f))
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

private val WelcomeFont = FontFamily(
    Font(resId = R.font.montserrat_extra_bold, weight = FontWeight.ExtraBold)
)

private val CallinFont = FontFamily(
    Font(resId = R.font.great_vibes_regular, weight = FontWeight.Normal)
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
