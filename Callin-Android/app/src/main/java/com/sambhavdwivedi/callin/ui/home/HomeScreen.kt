package com.sambhavdwivedi.callin.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.ui.theme.CallinColors

private enum class HomeTab(val label: String) {
    Recents("Recents"),
    Contacts("Contacts"),
    Profile("Profile")
}

/**
 * The app's real home — a bottom-tab shell around Recents, Contacts,
 * and Profile. This replaces the placeholder Welcome screen; the
 * tabs themselves are plain local state (no nested nav graph) since
 * none of them need deep linking yet.
 *
 * selectedTab uses rememberSaveable, not remember: HomeScreen is a
 * single NavHost destination, and Navigation Compose disposes and
 * re-creates a destination's composition each time another
 * destination is pushed on top of it and then popped back to (e.g.
 * opening the QR screen from the Profile tab, then coming back with
 * the system back gesture). Plain `remember` state does not survive
 * that — it would silently reset back to the Contacts tab every
 * time. rememberSaveable persists across exactly that kind of
 * recreation, via the SaveableStateHolder Navigation Compose already
 * scopes to each back-stack entry, so the user lands back on
 * whichever tab they actually left from.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onSignOut: () -> Unit,
    onOpenMyQr: () -> Unit,
    onOpenScanQr: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Contacts) }

    Scaffold(
        containerColor = CallinColors.Background,
        bottomBar = {
            NavigationBar(containerColor = CallinColors.Background) {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Recents,
                    onClick = { selectedTab = HomeTab.Recents },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text(HomeTab.Recents.label) },
                    colors = homeNavItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Contacts,
                    onClick = { selectedTab = HomeTab.Contacts },
                    icon = { Icon(Icons.Filled.People, contentDescription = null) },
                    label = { Text(HomeTab.Contacts.label) },
                    colors = homeNavItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Profile,
                    onClick = { selectedTab = HomeTab.Profile },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text(HomeTab.Profile.label) },
                    colors = homeNavItemColors()
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.Recents -> RecentsScreen()
                HomeTab.Contacts -> ContactsScreen(container = container)
                HomeTab.Profile -> ProfileScreen(
                    container = container,
                    onSignOut = onSignOut,
                    onOpenMyQr = onOpenMyQr,
                    onOpenScanQr = onOpenScanQr,
                    onOpenNotifications = onOpenNotifications,
                    onOpenTerms = onOpenTerms,
                    onOpenPrivacy = onOpenPrivacy
                )
            }
        }
    }
}

@Composable
private fun homeNavItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = CallinColors.TextPrimary,
    selectedTextColor = CallinColors.TextPrimary,
    unselectedIconColor = CallinColors.TextSecondary,
    unselectedTextColor = CallinColors.TextSecondary,
//    indicatorColor = CallinColors.TextSecondary.copy(alpha = 0.16f)
    indicatorColor = Color(0xFF151D2A)
)
