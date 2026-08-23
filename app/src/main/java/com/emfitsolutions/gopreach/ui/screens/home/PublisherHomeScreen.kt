package com.emfitsolutions.gopreach.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.ui.components.DashboardHero
import com.emfitsolutions.gopreach.ui.components.DashboardSection
import com.emfitsolutions.gopreach.ui.components.DashboardTile
import com.emfitsolutions.gopreach.ui.components.QuickAction
import com.emfitsolutions.gopreach.ui.components.SyncToServerButton
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

    // "Back Button and Page Navigation" spec §7 — this is the Main Form for the
    // Publisher context; there's nothing left in the nav stack to pop to here,
    // so Back needs its own "Exit GoPreach?" confirmation rather than silently
    // closing the app.
    val activity = LocalContext.current as? ComponentActivity
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler { showExitConfirm = true }
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit GoPreach?") },
            text = { Text("Are you sure you want to close the application?") },
            confirmButton = { TextButton(onClick = { activity?.finish() }) { Text("EXIT") } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("CANCEL") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DashboardHero(
            greetingName = session.person?.firstName?.takeIf { it.isNotBlank() } ?: "there",
            roleLabel = "Ministry Report",
            isOnline = isOnline,
            pendingSyncCount = pendingSyncCount,
            topEndAction = {
                // Sign Out relocated here from its own "Account" tile below
                // (explicit request) — next to Settings, both in the header's
                // top-right corner, rather than requiring a scroll down to
                // the bottom of the tile grid to sign out.
                Row {
                    IconButton(onClick = { onNavigate(Destinations.SETTINGS) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                    IconButton(onClick = viewModel::signOut) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sign Out", tint = Color.White)
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
            SyncToServerButton()

            DashboardSection("My Ministry") {
                DashboardTile("Monthly Report", Icons.Rounded.Assignment, { onNavigate(Destinations.MONTHLY_REPORT) })
                DashboardTile("Bible Study Record", Icons.AutoMirrored.Rounded.MenuBook, { onNavigate(Destinations.BIBLE_STUDY_RECORD) })
                DashboardTile("Interested People", Icons.Rounded.PeopleAlt, { onNavigate(Destinations.INTERESTED_PEOPLE) })
                DashboardTile("Share Location", Icons.Rounded.LocationOn, { onNavigate(Destinations.SHARE_LOCATION) })
                DashboardTile("Calendar", Icons.Rounded.CalendarMonth, { onNavigate(Destinations.CALENDAR) })
            }

            // Sign Out moved to the header, next to Settings (see topEndAction
            // above) — this section now only exists at all for a dual-role
            // account that also needs the "switch context" tile.
            if (onSwitchToAdmin != null) {
                DashboardSection("Account") {
                    DashboardTile("Admin App", Icons.Rounded.SwapHoriz, onSwitchToAdmin)
                }
            }
        }
    }
}
