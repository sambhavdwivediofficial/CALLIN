package com.sambhavdwivedi.callin.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sambhavdwivedi.callin.CallinApplication
import com.sambhavdwivedi.callin.data.repository.CallUiState
import com.sambhavdwivedi.callin.ui.auth.CompleteProfileScreen
import com.sambhavdwivedi.callin.ui.auth.LoginScreen
import com.sambhavdwivedi.callin.ui.call.CallRoute
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import com.sambhavdwivedi.callin.ui.home.HomeScreen
import com.sambhavdwivedi.callin.ui.legal.PrivacyScreen
import com.sambhavdwivedi.callin.ui.legal.TermsScreen
import com.sambhavdwivedi.callin.ui.notifications.NotificationsScreen
import com.sambhavdwivedi.callin.ui.qr.MyQrCodeScreen
import com.sambhavdwivedi.callin.ui.qr.ScanQrScreen
import com.sambhavdwivedi.callin.ui.theme.CallinColors

private enum class SessionState { Loading, LoggedOut, NeedsProfile, LoggedIn }

@Composable
fun CallinNavHost() {
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
            SessionState.LoggedIn
        } else {
            SessionState.NeedsProfile
        }
    }

    // Drives the call screen onto the stack the instant an incoming
    // or outgoing call starts, from whatever screen the user is on
    // — and pops it back off once the call is fully over. This runs
    // for the whole app session, independent of which destination
    // is currently showing.
    LaunchedEffect(container) {
        container.callRepository.state.collect { callState ->
            val onCallRoute = navController.currentDestination?.route == Routes.Call
            when (callState) {
                is CallUiState.Idle -> if (onCallRoute) navController.popBackStack()
                is CallUiState.Outgoing, is CallUiState.Incoming, is CallUiState.Active, is CallUiState.Ended -> {
                    if (!onCallRoute) navController.navigate(Routes.Call)
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
        SessionState.LoggedIn -> Routes.Home
        else -> Routes.Login
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
                    navController.navigate(Routes.Home) { popUpTo(Routes.CompleteProfile) { inclusive = true } }
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
        composable(Routes.MyQrCode) { MyQrCodeScreen(container = container, onBack = { navController.popBackStack() }) }
        composable(Routes.ScanQrCode) {
            ScanQrScreen(container = container, onBack = { navController.popBackStack() }, onAdded = { navController.popBackStack() })
        }
        composable(Routes.Notifications) { NotificationsScreen(container = container, onBack = { navController.popBackStack() }) }
        composable(Routes.Call) {
            CallRoute(container = container, onFinished = { /* state already Idle by the time this fires */ })
        }
    }
}
