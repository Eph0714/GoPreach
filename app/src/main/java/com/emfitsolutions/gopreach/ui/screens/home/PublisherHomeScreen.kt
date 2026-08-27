package com.emfitsolutions.gopreach.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.ui.screens.announcements.ManageAnnouncementsViewModel
import com.emfitsolutions.gopreach.ui.components.DashboardHero
import com.emfitsolutions.gopreach.ui.components.DashboardSection
import com.emfitsolutions.gopreach.ui.components.DashboardTile
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.QuickAction
import com.emfitsolutions.gopreach.ui.components.SquareStatCard
import com.emfitsolutions.gopreach.ui.components.SyncToServerButton
import com.emfitsolutions.gopreach.ui.navigation.Destinations

/** Landing point for the Ministry Report App / Publisher context (spec §5.2).
 *
 * "Role-Based Publisher Dashboard" spec §1/§21 — the square statistic cards
 * differ by [PublisherCategory]: Pioneer gets My Bible Studies/My Return
 * Visits/Preaching Hours; Regular/Unbaptized get My Bible Studies/Attended
 * Preaching only, never Preaching Hours or Preaching Time Record access
 * (spec §17/§20). Every value comes from [PublisherDashboardViewModel]'s
 * real database queries — never hard-coded (spec §1/§24's explicit
 * requirement, restated across nearly every numbered section of that spec).
 */
@Composable
fun PublisherHomeScreen(
    onSwitchToAdmin: (() -> Unit)?,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    dashboardViewModel: PublisherDashboardViewModel = hiltViewModel(),
    announcementsViewModel: ManageAnnouncementsViewModel = hiltViewModel(),
) {
    val session by viewModel.state.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val currentPersonId = session.person?.id.orEmpty()
    // resolvedRoleTypeOrNull() (never throws) — this runs unconditionally on
    // every Main Form composition, same reasoning as AdminHomeScreen/
    // GoPreachNavGraph's own role resolution.
    val ownPublisherAssignment = remember(session.roleAssignments) {
        session.roleAssignments.firstOrNull { it.resolvedRoleTypeOrNull() is RoleType.Publisher }
    }
    val category = (ownPublisherAssignment?.resolvedRoleTypeOrNull() as? RoleType.Publisher)?.category
    val isPioneer = isPioneerCategory(category)

    // "Announcement Module" — the notification balloon's unseen count, scoped
    // to this Publisher's own congregation.
    val unseenAnnouncementsFlow = remember(ownPublisherAssignment?.congregationId, currentPersonId) {
        announcementsViewModel.unseenCountFor(ownPublisherAssignment?.congregationId, currentPersonId)
    }
    val unseenAnnouncements by unseenAnnouncementsFlow.collectAsStateWithLifecycle(initialValue = 0)

    // "Back Button and Page Navigation" spec §7 — this is the Main Form for the
    // Publisher context; there's nothing left in the nav stack to pop to here,
    // so Back needs its own "Exit GoPreach?" confirmation rather than silently
    // closing the app.
    val activity = LocalContext.current as? ComponentActivity
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler { showExitConfirm = true }

    // Monthly Report reminder notifications (see ReminderWorker) need this
    // granted on Android 13+ — asked once here, the Main Form, rather than
    // buried behind a settings toggle nobody would find.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
                    // "Announcement Module" — the notification balloon at
                    // the Main Form's upper right; opening it marks
                    // everything currently in scope seen (see
                    // AnnouncementsScreen's LaunchedEffect).
                    IconButton(onClick = { onNavigate(Destinations.PUBLISHER_ANNOUNCEMENTS) }) {
                        BadgedBox(
                            badge = { if (unseenAnnouncements > 0) Badge { Text(unseenAnnouncements.toString()) } },
                        ) {
                            Icon(Icons.Rounded.Campaign, contentDescription = "Announcements", tint = Color.White)
                        }
                    }
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
                QuickAction("Searching", Icons.Rounded.PersonSearch) { onNavigate(Destinations.SEARCHING) },
                QuickAction("Return Visit", Icons.Rounded.PeopleAlt) { onNavigate(Destinations.RETURN_VISIT) },
                QuickAction("Bible Study", Icons.AutoMirrored.Rounded.MenuBook) { onNavigate(Destinations.BIBLE_STUDY) },
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

            if (currentPersonId.isNotBlank()) {
                PublisherStatsSection(
                    publisherPersonId = currentPersonId,
                    isPioneer = isPioneer,
                    onNavigate = onNavigate,
                    viewModel = dashboardViewModel,
                )
            }

            DashboardSection("My Ministry") {
                DashboardTile("Monthly Report", Icons.Rounded.Assignment, { onNavigate(Destinations.MONTHLY_REPORT) })
                DashboardTile("Searching", Icons.Rounded.PersonSearch, { onNavigate(Destinations.SEARCHING) })
                DashboardTile("Return Visit", Icons.Rounded.PeopleAlt, { onNavigate(Destinations.RETURN_VISIT) })
                DashboardTile("Bible Study", Icons.AutoMirrored.Rounded.MenuBook, { onNavigate(Destinations.BIBLE_STUDY) })
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

/**
 * "Role-Based Publisher Dashboard" spec §2/§17/§20-§22 — the date range
 * picker plus the category-appropriate square stat cards. Pioneer gets three
 * (My Bible Studies / My Return Visits / Preaching Hours); Regular and
 * Unbaptized both get exactly two (My Bible Studies / Attended Preaching) —
 * same layout, same underlying "unique person" logic (spec §25: identical
 * counting used everywhere), just a different subset of cards.
 *
 * Each card's `onClick` reuses an existing Main Form destination
 * (Bible Study Record / Interested People / Monthly Report) or the new
 * Preaching Time Record screen — never a bare duplicate of a Main Form nav
 * button with no added value (spec §4/§23): every card here also carries a
 * live, current statistic the plain nav button doesn't.
 */
@Composable
private fun PublisherStatsSection(
    publisherPersonId: String,
    isPioneer: Boolean,
    onNavigate: (String) -> Unit,
    viewModel: PublisherDashboardViewModel,
) {
    LaunchedEffect(publisherPersonId) {
        // Only Pioneers' "My Return Visits" card needs the cross-Interested-
        // Person collection-group listener; starting it for every Publisher
        // would just be wasted reads for Regular/Unbaptized publishers, who
        // never see that card at all.
        if (isPioneer) viewModel.startVisitSync(publisherPersonId)
    }
    val statsFlow = remember(publisherPersonId) { viewModel.statsFor(publisherPersonId) }
    val stats by statsFlow.collectAsStateWithLifecycle()
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("My Dashboard", style = MaterialTheme.typography.titleMedium)
        DateRangeFilterBar(range = dateRange, onRangeChange = viewModel::setDateRange)

        val bibleStudiesCard: @Composable (Modifier) -> Unit = { modifier ->
            SquareStatCard(
                title = "MY BIBLE STUDIES",
                value = stats.bibleStudiesCount.toString(),
                onClick = { onNavigate(Destinations.BIBLE_STUDY) },
                modifier = modifier,
            )
        }

        if (isPioneer) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                bibleStudiesCard(Modifier.weight(1f))
                SquareStatCard(
                    title = "MY RETURN VISITS",
                    value = stats.returnVisitsCount.toString(),
                    onClick = { onNavigate(Destinations.RETURN_VISIT) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SquareStatCard(
                    title = "PREACHING HOURS",
                    value = "%.1f".format(stats.preachingHours),
                    onClick = { onNavigate(Destinations.PREACHING_TIME_RECORD) },
                    modifier = Modifier.weight(1f),
                )
                // Balances the row so "Preaching Hours" stays the same
                // square size as every other card instead of stretching to
                // fill the row alone (spec §5: "consistent dimensions").
                Box(modifier = Modifier.weight(1f))
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                bibleStudiesCard(Modifier.weight(1f))
                SquareStatCard(
                    title = "ATTENDED PREACHING",
                    value = if (stats.attendedPreaching) "YES" else "NO",
                    onClick = { onNavigate(Destinations.MONTHLY_REPORT) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
