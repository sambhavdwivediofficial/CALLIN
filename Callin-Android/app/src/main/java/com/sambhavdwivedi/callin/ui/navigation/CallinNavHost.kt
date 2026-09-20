package com.sambhavdwivedi.callin.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import com.sambhavdwivedi.callin.ui.auth.CompleteProfileScreen
import com.sambhavdwivedi.callin.ui.auth.LoginScreen
import com.sambhavdwivedi.callin.ui.home.HomeScreen
import com.sambhavdwivedi.callin.ui.legal.PrivacyScreen
import com.sambhavdwivedi.callin.ui.legal.TermsScreen
import com.sambhavdwivedi.callin.ui.theme.CallinColors

private enum class SessionState { Loading, LoggedOut, NeedsProfile, LoggedIn }

/**
 * Decides where the user lands right after the splash animation:
 * Login if there's no session, CompleteProfile if their Google
 * account hasn't finished onboarding, Home otherwise. This is the
 * single place that owns that decision — screens themselves don't
 * need to know how they got there.
 *
 * Every route change fades rather than cuts — this is a one-time
 * NavHost-level setting, so every screen added to the app from here
 * on gets the same smooth transition for free.
 */
@Composable
fun CallinNavHost() {
    val context = LocalContext.current
    val container = (context.applicationContext as CallinApplication).container

    var sessionState by remember { mutableStateOf(SessionState.Loading) }
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        val token = container.tokenStore.getAccessToken()
        if (token.isNullOrBlank()) {
            sessionState = SessionState.LoggedOut
            return@LaunchedEffect
        }
        container.userRepository.getMe()
            .onSuccess { me ->
                sessionState = if (me.profile_completed) SessionState.LoggedIn else SessionState.NeedsProfile
            }
            .onFailure {
                // Token invalid/expired and refresh isn't wired up yet
                // for this screen — safest is to sign the user out
                // cleanly rather than get stuck.
                container.tokenStore.clear()
                sessionState = SessionState.LoggedOut
            }
    }

    if (sessionState == SessionState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CallinColors.Background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = CallinColors.TextSecondary)
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
                    navController.navigate(dest) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onOpenTerms = { navController.navigate(Routes.Terms) },
                onOpenPrivacy = { navController.navigate(Routes.Privacy) }
            )
        }
        composable(Routes.CompleteProfile) {
            CompleteProfileScreen(
                container = container,
                onCompleted = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.CompleteProfile) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.Home) {
            HomeScreen(
                container = container,
                onSignOut = {
                    navController.navigate(Routes.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.Terms) {
            TermsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Privacy) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
