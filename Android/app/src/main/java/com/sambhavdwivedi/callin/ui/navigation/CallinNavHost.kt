package com.sambhavdwivedi.callin.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sambhavdwivedi.callin.CallinApplication
import com.sambhavdwivedi.callin.data.repository.CallUiState
import com.sambhavdwivedi.callin.ui.auth.CompleteProfileScreen
import com.sambhavdwivedi.callin.ui.auth.LoginScreen
import com.sambhavdwivedi.callin.ui.call.CallRoute
import com.sambhavdwivedi.callin.ui.components.InCallBanner
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.home.HomeScreen
import com.sambhavdwivedi.callin.ui.legal.PrivacyScreen
import com.sambhavdwivedi.callin.ui.legal.TermsScreen
import com.sambhavdwivedi.callin.ui.notifications.NotificationsScreen
import com.sambhavdwivedi.callin.ui.qr.MyQrCodeScreen
import com.sambhavdwivedi.callin.ui.qr.ScanQrScreen
import com.sambhavdwivedi.callin.ui.theme.CallinColors

private enum class SessionState { Loading, LoggedOut, NeedsProfile, LoggedIn }

/**
 * Decides where the user lands right after the splash animation, and
 * reacts to CallRepository's state to drive the in-call banner / Call
 * screen.
 *
 * CRITICAL FIX: the call-state collector used to be a plain
 * `LaunchedEffect(container) { ... }` that started collecting
 * immediately, regardless of whether [sessionState] had resolved
 * yet. If an FCM push had already set CallRepository's state to
 * Incoming/Active (e.g. this app process was launched BY tapping the
 * call notification), this collector would receive that value
 * instantly via StateFlow's replay — often before the async
 * sessionState resolution (DataStore reads) had finished — and call
 * `navController.navigate(Routes.Call)` before the NavHost below
 * (only composed once sessionState != Loading) had attached its
 * graph. Navigating onto a route that doesn't exist yet either
 * throws or silently no-ops depending on timing — which is exactly
 * "app opens, gets stuck for a moment, then goes back to nothing".
 * Because the crash tore down the whole process, it's also why the
 * ringtone (owned by the same process) abruptly cut out right after.
 *
 * Fix: key the collector on [sessionState] and skip entirely while
 * it's Loading. Once it resolves, the effect restarts — and because
 * this is a StateFlow (which always replays its latest value to a
 * new collector), it immediately "catches up" to whatever call state
 * was already set, so a call that arrived during the loading window
 * is never missed, just handled a beat later, once it's safe.
 */
@Composable
fun CallinNavHost(skipInitialCallAutoNav: Boolean = false) {
    val context = LocalContext.current
    val container = (context.applicationContext as CallinApplication).container

    var sessionState by remember { mutableStateOf(SessionState.Loading) }
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        val token = container.tokenStore.getAccessToken()
        sessionState = if (token.isNullOrBlank()) {
            SessionState.LoggedOut
        } else if (container.tokenStore.getProfileCompleted()) {
            container.signalingClient.start()
            container.callRepository.start()
            container.userRepository.registerDeviceToken()
            SessionState.LoggedIn
        } else {
            SessionState.NeedsProfile
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val suppressNextAutoNav = remember { mutableStateOf(skipInitialCallAutoNav) }

    // Keyed on sessionState — see the doc comment above for why this
    // is the fix for the "stuck, then goes back" crash.
    LaunchedEffect(container, sessionState) {
        if (sessionState == SessionState.Loading) return@LaunchedEffect
        container.callRepository.state.collect { callState ->
            val onCallRoute =
                navController.currentBackStackEntry?.destination?.route == Routes.Call
            when (callState) {
                is CallUiState.Idle -> if (onCallRoute) navController.popBackStack()
                is CallUiState.Outgoing, is CallUiState.Incoming, is CallUiState.Active, is CallUiState.Ended -> {
                    if (suppressNextAutoNav.value) {
                        suppressNextAutoNav.value = false
                    } else if (!onCallRoute) {
                        navController.navigate(Routes.Call)
                    }
                }
            }
        }
    }

    if (sessionState == SessionState.Loading) {
        Box(
            modifier = Modifier.fillMaxSize().background(CallinColors.Background),
            contentAlignment = Alignment.Center
        ) {
            PulseBarsLoader(barColor = CallinColors.TextSecondary)
        }
        return
    }

    val startDestination = when (sessionState) {
        SessionState.NeedsProfile -> Routes.CompleteProfile
        SessionState.LoggedIn -> {
            // Skip the Home → Call flicker entirely when this process
            // was launched via a notification tap (skipInitialCallAutoNav
            // == false) and CallRepository already has a live call
            // (set by an FCM push that landed before this composable
            // even ran). In that exact case, start directly on the
            // Call route instead of Home, so there's nothing to
            // navigate away FROM.
            if (!skipInitialCallAutoNav && container.callRepository.state.value !is CallUiState.Idle) {
                Routes.Call
            } else {
                Routes.Home
            }
        }
        else -> Routes.Login
    }

    val callState by container.callRepository.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (currentRoute != Routes.Call) {
            when (val s = callState) {
                is CallUiState.Incoming -> InCallBanner(s.info, "Incoming") {
                    navController.navigate(Routes.Call)
                }
                is CallUiState.Outgoing -> InCallBanner(s.info, if (s.ringing) "Ringing" else "Connecting") {
                    navController.navigate(Routes.Call)
                }
                is CallUiState.Active -> InCallBanner(s.info, "Tap to return") {
                    navController.navigate(Routes.Call)
                }
                else -> Unit
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f),
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(180)) }
        ) {
            composable(Routes.Login) {
                LoginScreen(
                    container = container,
                    onSignedIn = { needsProfile ->
                        val dest = if (needsProfile) Routes.CompleteProfile else Routes.Home
                        navController.navigate(dest) { popUpTo(Routes.Login) { inclusive = true } }
                    },
                    onOpenTerms = { navController.navigate(Routes.Terms) },
                    onOpenPrivacy = { navController.navigate(Routes.Privacy) }
                )
            }
            composable(Routes.CompleteProfile) {
                CompleteProfileScreen(
                    container = container,
                    onCompleted = {
                        container.signalingClient.start()
                        container.callRepository.start()
                        container.userRepository.registerDeviceToken()
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.CompleteProfile) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.Home) {
                HomeScreen(
                    container = container,
                    onSignOut = { navController.navigate(Routes.Login) { popUpTo(0) { inclusive = true } } },
                    onOpenMyQr = { navController.navigate(Routes.MyQrCode) },
                    onOpenScanQr = { navController.navigate(Routes.ScanQrCode) },
                    onOpenNotifications = { navController.navigate(Routes.Notifications) },
                    onOpenTerms = { navController.navigate(Routes.Terms) },
                    onOpenPrivacy = { navController.navigate(Routes.Privacy) }
                )
            }
            composable(Routes.Terms) { TermsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.Privacy) { PrivacyScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.MyQrCode) {
                MyQrCodeScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(Routes.ScanQrCode) {
                ScanQrScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onAdded = { navController.popBackStack() }
                )
            }
            composable(Routes.Notifications) {
                NotificationsScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(Routes.Call) {
                CallRoute(container = container, onFinished = { })
            }
        }
    }
}
