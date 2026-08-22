package com.emfitsolutions.gopreach.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.ui.components.DashboardHero
import com.emfitsolutions.gopreach.ui.components.DashboardSection
import com.emfitsolutions.gopreach.ui.components.DashboardTile
import com.emfitsolutions.gopreach.ui.components.DynamicAppLogo
import com.emfitsolutions.gopreach.ui.components.QuickAction
import com.emfitsolutions.gopreach.ui.components.SyncStatusButton
import com.emfitsolutions.gopreach.ui.components.UpdateAvailableBanner
import com.emfitsolutions.gopreach.ui.navigation.Destinations

/** Landing point for the Ministry Report App / Publisher context (spec §5.2). */
@Composable
fun PublisherHomeScreen(
    onSwitchToAdmin: (() -> Unit)?,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val session by viewModel.state.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DashboardHero(
            greetingName = session.person?.firstName?.takeIf { it.isNotBlank() } ?: "there",
            roleLabel = "Ministry Report",
            isOnline = isOnline,
            pendingSyncCount = pendingSyncCount,
            logoContent = { DynamicAppLogo() },
            topEndAction = {
                Row {
                    SyncStatusButton()
                    IconButton(onClick = { onNavigate(Destinations.SETTINGS) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            },
            quickActions = listOf(
                QuickAction("Report", Icons.Rounded.Assignment) { onNavigate(Destinations.MONTHLY_REPORT) },
                QuickAction("Bible Study", Icons.AutoMirrored.Rounded.MenuBook) { onNavigate(Destinations.BIBLE_STUDY_RECORD) },
                QuickAction("Interested", Icons.Rounded.PeopleAlt) { onNavigate(Destinations.INTERESTED_PEOPLE) },
                QuickAction("Calendar", Icons.Rounded.CalendarMonth) { onNavigate(Destinations.CALENDAR) },
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            UpdateAvailableBanner()

            DashboardSection("My Ministry") {
                DashboardTile("Monthly Report", Icons.Rounded.Assignment, { onNavigate(Destinations.MONTHLY_REPORT) })
                DashboardTile("Bible Study Record", Icons.AutoMirrored.Rounded.MenuBook, { onNavigate(Destinations.BIBLE_STUDY_RECORD) })
                DashboardTile("Interested People", Icons.Rounded.PeopleAlt, { onNavigate(Destinations.INTERESTED_PEOPLE) })
                DashboardTile("Share Location", Icons.Rounded.LocationOn, { onNavigate(Destinations.SHARE_LOCATION) })
                DashboardTile("Calendar", Icons.Rounded.CalendarMonth, { onNavigate(Destinations.CALENDAR) })
            }

            DashboardSection("Account") {
                if (onSwitchToAdmin != null) {
                    DashboardTile("Admin App", Icons.Rounded.SwapHoriz, onSwitchToAdmin)
                }
                DashboardTile("Sign Out", Icons.AutoMirrored.Rounded.Logout, viewModel::signOut)
            }
        }
    }
}
