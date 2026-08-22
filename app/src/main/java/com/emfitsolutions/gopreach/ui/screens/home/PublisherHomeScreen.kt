package com.emfitsolutions.gopreach.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.ui.components.AppBanner
import com.emfitsolutions.gopreach.ui.components.DashboardSection
import com.emfitsolutions.gopreach.ui.components.DashboardTile
import com.emfitsolutions.gopreach.ui.components.DynamicAppLogo
import com.emfitsolutions.gopreach.ui.navigation.Destinations

/** Landing point for the Ministry Report App / Publisher context (spec §5.2). */
@Composable
fun PublisherHomeScreen(
    onSwitchToAdmin: (() -> Unit)?,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val session by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppBanner(
            title = "Ministry Report",
            subtitle = session.person?.fullName,
            logoContent = { DynamicAppLogo() },
            topEndAction = {
                IconButton(onClick = { onNavigate(Destinations.SETTINGS) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DashboardSection("My Ministry") {
                DashboardTile("Monthly Report", Icons.Filled.Assignment, { onNavigate(Destinations.MONTHLY_REPORT) })
                DashboardTile("Bible Study Record", Icons.AutoMirrored.Filled.MenuBook, { onNavigate(Destinations.BIBLE_STUDY_RECORD) })
                DashboardTile("Interested People", Icons.Filled.PeopleAlt, { onNavigate(Destinations.INTERESTED_PEOPLE) })
                DashboardTile("Share Location", Icons.Filled.LocationOn, { onNavigate(Destinations.SHARE_LOCATION) })
                DashboardTile("Calendar", Icons.Filled.CalendarMonth, { onNavigate(Destinations.CALENDAR) })
            }

            DashboardSection("Account") {
                if (onSwitchToAdmin != null) {
                    DashboardTile("Admin App", Icons.Filled.SwapHoriz, onSwitchToAdmin)
                }
                DashboardTile("Sign Out", Icons.AutoMirrored.Filled.Logout, viewModel::signOut)
            }
        }
    }
}
