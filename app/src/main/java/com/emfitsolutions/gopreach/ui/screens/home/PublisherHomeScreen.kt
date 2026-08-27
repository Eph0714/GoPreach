package com.emfitsolutions.gopreach.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.SquareStatCard
import com.emfitsolutions.gopreach.ui.components.SyncToServerButton
import com.emfitsolutions.gopreach.ui.navigation.Destinations
import com.emfitsolutions.gopreach.ui.screens.announcements.ManageAnnouncementsViewModel

/**
 * Landing point for the Ministry Report App / Publisher context (spec §5.2).
 *
 * Redesigned per a supplied reference mockup: a gradient welcome header, a
 * "Dashboard" card wrapping the existing date-range filter, a two-column
 * feature tile grid with notification-style badges, a Quick Summary of real
 * (never fabricated — spec §1/§24) figures, a sync card, and a bottom
 * navigation bar. Colors are drawn entirely from [MaterialTheme.colorScheme]
 * (the user's chosen [com.emfitsolutions.gopreach.ui.theme.ThemeColorOption],
 * purple by default) rather than the reference's own green/amber/blue
 * per-tile palette — this app's design language deliberately stays to one
 * accent color, tonal shades only (see Color.kt: "do not leave old
 * green/blue/yellow accents").
 *
 * "Role-Based Publisher Dashboard" spec §1/§21 — the square statistic cards
 * differ by [PublisherCategory]: Pioneer gets My Bible Studies/My Return
 * Visits/Preaching Hours; Regular/Unbaptized get My Bible Studies/Attended
 * Preaching only, never Preaching Hours or Preaching Time Record access
 * (spec §17/§20). Every value comes from [PublisherDashboardViewModel]'s
 * real database queries — never hard-coded.
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

    val congregations by announcementsViewModel.congregations.collectAsStateWithLifecycle()
    val congregationName = congregations.firstOrNull { it.id == ownPublisherAssignment?.congregationId }?.name

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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PublisherWelcomeHeader(
                greetingName = session.person?.firstName?.takeIf { it.isNotBlank() } ?: "there",
                congregationName = congregationName,
                categoryLabel = category?.displayLabel(),
                isOnline = isOnline,
                pendingSyncCount = pendingSyncCount,
                unseenAnnouncements = unseenAnnouncements,
                onOpenAnnouncements = { onNavigate(Destinations.PUBLISHER_ANNOUNCEMENTS) },
                onOpenSettings = { onNavigate(Destinations.SETTINGS) },
                onSignOut = viewModel::signOut,
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (currentPersonId.isNotBlank()) {
                    PublisherStatsSection(
                        publisherPersonId = currentPersonId,
                        isPioneer = isPioneer,
                        onNavigate = onNavigate,
                        viewModel = dashboardViewModel,
                    )
                }

                FeatureTileGrid(
                    isPioneer = isPioneer,
                    unseenAnnouncements = unseenAnnouncements,
                    onNavigate = onNavigate,
                )

                if (onSwitchToAdmin != null) {
                    Card(modifier = Modifier.fillMaxWidth(), onClick = onSwitchToAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Switch to Admin App", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Keep Your Data Safe",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Sync your data regularly to keep your information secure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        SyncToServerButton()
                    }
                }
            }
        }

        PublisherBottomNavBar(activeRoute = Destinations.PUBLISHER_HOME, onNavigate = onNavigate)
    }
}

/** Gradient welcome header — colors drawn from the theme's primary/
 * primaryContainer tones (never hard-coded green/blue), rounded at the
 * bottom to read as one soft panel, matching the reference's shape. */
@Composable
private fun PublisherWelcomeHeader(
    greetingName: String,
    congregationName: String?,
    categoryLabel: String?,
    isOnline: Boolean,
    pendingSyncCount: Int,
    unseenAnnouncements: Int,
    onOpenAnnouncements: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer),
                ),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .padding(bottom = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                // "Announcement Module" — the notification balloon; opening
                // it marks everything currently in scope seen (see
                // AnnouncementsScreen's LaunchedEffect).
                IconButton(onClick = onOpenAnnouncements) {
                    BadgedBox(badge = { if (unseenAnnouncements > 0) Badge { Text(unseenAnnouncements.toString()) } }) {
                        Icon(Icons.Rounded.Campaign, contentDescription = "Announcements", tint = Color.White)
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                }
                IconButton(onClick = onSignOut) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sign Out", tint = Color.White)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    "Welcome, $greetingName 👋",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                val subtitle = listOfNotNull(congregationName, categoryLabel).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                }
                val statusCaption = if (isOnline) "Online" else "Offline" +
                    (if (pendingSyncCount > 0) " · $pendingSyncCount pending sync" else " · All synced")
                Text(
                    statusCaption,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** One feature tile — an icon circle (theme tonal color), title/subtitle,
 * and an optional notification-style badge count. */
@Composable
private fun FeatureTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp),
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(iconContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (badgeCount > 0) {
                Badge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text(badgeCount.toString()) }
            }
        }
    }
}

/** Two-column grid of every Main Form destination this Publisher can reach —
 * Preaching Time Record ("My Total Hours") is Pioneer-only (spec §17/§20).
 * Icon circles alternate between [MaterialTheme.colorScheme.primaryContainer]
 * and `.secondaryContainer` — still one theme, just enough tonal variety to
 * tell tiles apart at a glance, the way the reference's per-tile colors did
 * without reintroducing arbitrary hues. */
@Composable
private fun FeatureTileGrid(
    isPioneer: Boolean,
    unseenAnnouncements: Int,
    onNavigate: (String) -> Unit,
) {
    data class Tile(val title: String, val subtitle: String, val icon: ImageVector, val route: String, val badge: Int = 0)

    val tiles = buildList {
        add(Tile("Monthly Report", "Submit Report", Icons.Rounded.Assignment, Destinations.MONTHLY_REPORT))
        add(Tile("Searching", "Find Interested Ones", Icons.Rounded.PersonSearch, Destinations.SEARCHING))
        add(Tile("Return Visit", "Record & Follow-up", Icons.Rounded.PeopleAlt, Destinations.RETURN_VISIT))
        add(Tile("Bible Study", "View & Manage", Icons.AutoMirrored.Rounded.MenuBook, Destinations.BIBLE_STUDY))
        if (isPioneer) {
            add(Tile("My Total Hours", "Track Preaching Hours", Icons.Rounded.Timer, Destinations.PREACHING_TIME_RECORD))
        }
        add(Tile("My Calendar", "View Schedule", Icons.Rounded.CalendarMonth, Destinations.CALENDAR))
        add(Tile("Share My Location", "Share Live Location", Icons.Rounded.LocationOn, Destinations.SHARE_LOCATION))
        add(Tile("Announcement", "Latest Updates", Icons.Rounded.Campaign, Destinations.PUBLISHER_ANNOUNCEMENTS, unseenAnnouncements))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { index, tile ->
                    val useSecondary = (tiles.indexOf(tile)) % 2 == 1
                    FeatureTile(
                        title = tile.title,
                        subtitle = tile.subtitle,
                        icon = tile.icon,
                        iconContainerColor = if (useSecondary) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        iconTint = if (useSecondary) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        badgeCount = tile.badge,
                        onClick = { onNavigate(tile.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Odd tile count on the last row — balance it so the lone
                // tile stays the same width as every other one instead of
                // stretching to fill the row alone.
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Bottom navigation bar — Home/Reports/Calendar/Bible Study/Profile, the
 * app's first (Publisher-only) use of persistent bottom navigation; every
 * tab routes through the same [onNavigate] callback the rest of the Main
 * Form already uses, so it needs no separate nav-graph wiring. "Profile"
 * routes to Settings — the closest existing equivalent (there's no separate
 * Profile screen). */
@Composable
private fun PublisherBottomNavBar(activeRoute: String, onNavigate: (String) -> Unit) {
    data class NavTab(val label: String, val icon: ImageVector, val route: String)

    val tabs = listOf(
        NavTab("Home", Icons.Rounded.Home, Destinations.PUBLISHER_HOME),
        NavTab("Reports", Icons.Rounded.Assignment, Destinations.MONTHLY_REPORT),
        NavTab("Calendar", Icons.Rounded.CalendarMonth, Destinations.CALENDAR),
        NavTab("Bible Study", Icons.AutoMirrored.Rounded.MenuBook, Destinations.BIBLE_STUDY),
        NavTab("Profile", Icons.Rounded.Person, Destinations.SETTINGS),
    )

    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.route == activeRoute,
                onClick = { if (tab.route != activeRoute) onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}

private fun PublisherCategory.displayLabel(): String =
    name.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }

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

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            DateRangeFilterBar(range = dateRange, onRangeChange = viewModel::setDateRange)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
