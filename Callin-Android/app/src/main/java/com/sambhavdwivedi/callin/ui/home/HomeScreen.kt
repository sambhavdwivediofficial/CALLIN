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
import androidx.compose.runtime.remember
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
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(HomeTab.Contacts) }

    Scaffold(
        containerColor = CallinColors.Background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF070B14)) {
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
                HomeTab.Profile -> ProfileScreen(container = container, onSignOut = onSignOut)
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
    indicatorColor = CallinColors.TextSecondary.copy(alpha = 0.16f)
)
