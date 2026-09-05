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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.R
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.NotificationBell
import com.emfitsolutions.gopreach.ui.components.ProfileMenuButton
import com.emfitsolutions.gopreach.ui.components.RoundIconActionButton
import com.emfitsolutions.gopreach.ui.components.SyncToServerButton
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.navigation.Destinations
import com.emfitsolutions.gopreach.ui.screens.announcements.ManageAnnouncementsViewModel
import com.emfitsolutions.gopreach.ui.screens.notifications.NotificationCenterViewModel

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
    publisherForwardViewModel: com.emfitsolutions.gopreach.ui.screens.pipeline.PublisherForwardRequestsViewModel = hiltViewModel(),
    pipelineViewModel: com.emfitsolutions.gopreach.ui.screens.pipeline.PipelineViewModel = hiltViewModel(),
    notificationCenterViewModel: NotificationCenterViewModel = hiltViewModel(),
    groupChatViewModel: com.emfitsolutions.gopreach.ui.screens.groupchat.GroupChatViewModel = hiltViewModel(),
) {
    val session by viewModel.state.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val showToast = rememberActionToast()
    val currentPersonId = session.person?.id.orEmpty()
    // "Multiple Role Login Detection & Role Selection" spec §7 — this screen
    // only ever renders when the session's own active role already resolved
    // to Publisher (see GoPreachNavGraph's routing), so its own assignment
    // *is* [session.activeRoleAssignment] — no separate re-scan needed (the
    // old scan here also predates the ACTIVE-status bugfix that resolving
    // through activeRoleAssignment already carries).
    val ownPublisherAssignment = session.activeRoleAssignment
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

    // Unified notification balloon (spec: transfer requests, announcements,
    // calendar schedule — no Monthly Report category for a Publisher) —
    // congregation-scoped to this Publisher's own congregation, never "all".
    val notificationItemsFlow = remember(ownPublisherAssignment?.congregationId, currentPersonId) {
        notificationCenterViewModel.itemsForPublisher(currentPersonId, ownPublisherAssignment?.congregationId)
    }
    val visibleNotificationItemsFlow = remember(notificationItemsFlow, currentPersonId) {
        notificationCenterViewModel.visibleItemsFor(notificationItemsFlow, currentPersonId)
    }
    val notificationItems by visibleNotificationItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val notificationUnseenFlow = remember(visibleNotificationItemsFlow, currentPersonId) {
        notificationCenterViewModel.unseenCountFor(visibleNotificationItemsFlow, currentPersonId)
    }
    val notificationUnseenCount by notificationUnseenFlow.collectAsStateWithLifecycle(initialValue = 0)
    com.emfitsolutions.gopreach.ui.components.NewItemNotifier(
        items = notificationItemsFlow,
        onlyCategories = setOf(
            com.emfitsolutions.gopreach.data.repository.NotificationCategory.ANNOUNCEMENT,
            com.emfitsolutions.gopreach.data.repository.NotificationCategory.CALENDAR_SCHEDULE,
        ),
    )

    // "Group Chat Setting" Chat Box icon (spec §6) — same membership-by-
    // personId flow AdminHomeScreen wires in, so it's the identical set of
    // group chats whether this account is currently in its Publisher or a
    // higher-rank context.
    val chatBoxEntriesFlow = remember(currentPersonId) { groupChatViewModel.chatBoxEntriesFor(currentPersonId) }
    val chatBoxEntries by chatBoxEntriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // "FORWARD TO OTHER PUBLISHER" — this Publisher's own incoming queue
    // count, for the "Forwarded to Me" tile's badge (same pattern as the
    // Announcement badge above).
    val incomingForwardsFlow = remember(currentPersonId) { publisherForwardViewModel.incomingRequestsFor(currentPersonId) }
    val incomingForwards by incomingForwardsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    if (currentPersonId.isNotBlank()) {
        PublisherForwardNotifier(currentPersonId = currentPersonId, viewModel = publisherForwardViewModel)
        ForwardToCongregationSenderNotifier(currentPersonId = currentPersonId, viewModel = pipelineViewModel)
    }

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
            title = { Text(stringResource(R.string.home_exit_title)) },
            text = { Text(stringResource(R.string.home_exit_message)) },
            confirmButton = { TextButton(onClick = { activity?.finish() }) { Text(stringResource(R.string.home_exit_confirm)) } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text(stringResource(R.string.home_exit_cancel)) } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            com.emfitsolutions.gopreach.ui.components.AlarmRingingBanner()
            PublisherWelcomeHeader(
                greetingName = session.person?.firstName?.takeIf { it.isNotBlank() } ?: "there",
                fullName = session.person?.fullName ?: "—",
                profileImageUrl = session.person?.profileImageUrl,
                congregationName = congregationName,
                categoryLabel = category?.displayLabel(),
                isOnline = isOnline,
                pendingSyncCount = pendingSyncCount,
                notificationItems = notificationItems,
                notificationUnseenCount = notificationUnseenCount,
                onOpenNotifications = { notificationCenterViewModel.markAllSeen(currentPersonId) },
                onNotificationClick = onNavigate,
                onDismissNotification = { notificationCenterViewModel.dismiss(it, currentPersonId) },
                onClearAllNotifications = { notificationCenterViewModel.dismissAll(notificationItems, currentPersonId) },
                chatBoxEntries = chatBoxEntries,
                onOpenGroupChat = { chatId -> onNavigate(Destinations.groupChatDetail(chatId)) },
                onViewAllGroupChats = { onNavigate(Destinations.GROUP_CHAT_SETTING) },
                onOpenSettings = { onNavigate(Destinations.SETTINGS) },
                onImagePicked = { uri ->
                    viewModel.updateProfileImage(uri, onImageUploadFailed = {
                        showToast("Profile image failed to upload. Try again.")
                    })
                },
                onSignOut = viewModel::signOut,
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // "Sync to Server" moved to the top of the form (was at the
                // bottom, past the stats/tiles/switch-account content, where
                // it was easy to miss) — now the first thing the Publisher
                // sees below the header.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.home_sync_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(R.string.home_sync_card_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        SyncToServerButton()
                    }
                }

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
                    pendingPublisherForwards = incomingForwards.size,
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
                            Text(stringResource(R.string.home_switch_to_admin), style = MaterialTheme.typography.titleSmall)
                        }
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
    fullName: String,
    profileImageUrl: String?,
    congregationName: String?,
    categoryLabel: String?,
    isOnline: Boolean,
    pendingSyncCount: Int,
    notificationItems: List<com.emfitsolutions.gopreach.ui.screens.notifications.NotificationItem>,
    notificationUnseenCount: Int,
    onOpenNotifications: () -> Unit,
    onNotificationClick: (String) -> Unit,
    onDismissNotification: (com.emfitsolutions.gopreach.ui.screens.notifications.NotificationItem) -> Unit,
    onClearAllNotifications: () -> Unit,
    chatBoxEntries: List<com.emfitsolutions.gopreach.ui.components.ChatBoxEntry>,
    onOpenGroupChat: (String) -> Unit,
    onViewAllGroupChats: () -> Unit,
    onOpenSettings: () -> Unit,
    onImagePicked: (android.net.Uri) -> Unit,
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
                // Unified notification balloon — transfer requests,
                // announcements, calendar schedule; opening it marks
                // everything currently in scope seen (see
                // NotificationCenterViewModel.markAllSeen).
                NotificationBell(
                    items = notificationItems,
                    unseenCount = notificationUnseenCount,
                    onOpen = onOpenNotifications,
                    onItemClick = { onNotificationClick(it.route) },
                    onDismiss = onDismissNotification,
                    onClearAll = onClearAllNotifications,
                )
                com.emfitsolutions.gopreach.ui.components.ChatBoxIcon(
                    entries = chatBoxEntries,
                    onOpenGroupChat = onOpenGroupChat,
                    onViewAll = onViewAllGroupChats,
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                }
                // Folds Sign Out into the profile menu (name + role, View/
                // Update Profile Image, Log Out) — replaces the old
                // standalone logout icon rather than duplicating it.
                ProfileMenuButton(
                    fullName = fullName,
                    roleLabel = categoryLabel ?: stringResource(R.string.role_label_publisher),
                    profileImageUrl = profileImageUrl,
                    onImagePicked = onImagePicked,
                    onSignOut = onSignOut,
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.home_welcome, greetingName),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                val subtitle = listOfNotNull(congregationName, categoryLabel).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                }
                // Preserves the pre-existing behavior exactly (only "Online"
                // ever shows for the online case, no sync suffix) — this is
                // just the same three pieces now sourced from strings.xml.
                val statusCaption = if (isOnline) {
                    stringResource(R.string.home_status_online)
                } else {
                    stringResource(R.string.home_status_offline) + if (pendingSyncCount > 0) {
                        stringResource(R.string.home_status_pending_sync_suffix, pendingSyncCount)
                    } else {
                        stringResource(R.string.home_status_all_synced_suffix)
                    }
                }
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
                // weight(1f) so this column is actually constrained to the
                // space left after the icon, instead of measuring at its own
                // "wanted" text width — without it, a longer title/subtitle
                // (e.g. "Forwarded to Me", "Share My Location") could overflow
                // past the icon into the Card's rounded edge and get visually
                // clipped there instead of wrapping/ellipsizing in place.
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    pendingPublisherForwards: Int,
    onNavigate: (String) -> Unit,
) {
    data class Tile(val title: String, val subtitle: String, val icon: ImageVector, val route: String, val badge: Int = 0)

    val tiles = buildList {
        add(Tile(stringResource(R.string.home_tile_monthly_report_title), stringResource(R.string.home_tile_monthly_report_subtitle), Icons.Rounded.Assignment, Destinations.MONTHLY_REPORT))
        // "Allow the publisher to see all his submitted Report record" —
        // its own tile since MONTHLY_REPORT's form only ever shows the
        // current/previous month, not the full history.
        add(Tile(stringResource(R.string.home_tile_my_reports_title), stringResource(R.string.home_tile_my_reports_subtitle), Icons.AutoMirrored.Rounded.ListAlt, Destinations.MY_SUBMITTED_REPORTS))
        add(Tile(stringResource(R.string.home_tile_searching_title), stringResource(R.string.home_tile_searching_subtitle), Icons.Rounded.PersonSearch, Destinations.SEARCHING))
        add(Tile(stringResource(R.string.home_tile_return_visit_title), stringResource(R.string.home_tile_return_visit_subtitle), Icons.Rounded.PeopleAlt, Destinations.RETURN_VISIT))
        add(Tile(stringResource(R.string.home_tile_bible_study_title), stringResource(R.string.home_tile_bible_study_subtitle), Icons.AutoMirrored.Rounded.MenuBook, Destinations.BIBLE_STUDY))
        // "FORWARD TO OTHER PUBLISHER" — this Publisher's own incoming queue.
        add(Tile(stringResource(R.string.home_tile_forwarded_to_me_title), stringResource(R.string.home_tile_forwarded_to_me_subtitle), Icons.AutoMirrored.Rounded.Forward, Destinations.PUBLISHER_FORWARD_REQUESTS, pendingPublisherForwards))
        if (isPioneer) {
            add(Tile(stringResource(R.string.home_tile_my_total_hours_title), stringResource(R.string.home_tile_my_total_hours_subtitle), Icons.Rounded.Timer, Destinations.PREACHING_TIME_RECORD))
        }
        // "My Bible Text Record" module — every Publisher, not just Pioneers.
        // A distinct icon from "Bible Study" (also MenuBook) — this is a
        // personal saved-reference collection, not the ministry module.
        add(Tile(stringResource(R.string.home_tile_my_bible_text_record_title), stringResource(R.string.home_tile_my_bible_text_record_subtitle), Icons.Rounded.Bookmarks, Destinations.MY_BIBLE_TEXT_RECORD))
        add(Tile(stringResource(R.string.home_tile_my_calendar_title), stringResource(R.string.home_tile_my_calendar_subtitle), Icons.Rounded.CalendarMonth, Destinations.CALENDAR))
        add(Tile(stringResource(R.string.home_tile_share_my_location_title), stringResource(R.string.home_tile_share_my_location_subtitle), Icons.Rounded.LocationOn, Destinations.SHARE_LOCATION))
        add(Tile(stringResource(R.string.home_tile_find_location_title), stringResource(R.string.home_tile_find_location_subtitle), Icons.Rounded.Navigation, Destinations.FIND_LOCATION))
        // "Add the Territory Module in Publisher. The publisher can see all
        // the location but cannot edit or delete, view only" — the screen
        // itself has no edit/delete actions for anyone anymore (see
        // TerritoryMapScreen), so reaching it here is already read-only by
        // construction; scoped to the Publisher's own congregation (see
        // GoPreachNavGraph's MANAGE_TERRITORIES composable).
        add(Tile(stringResource(R.string.home_tile_territory_map_title), stringResource(R.string.home_tile_territory_map_subtitle), Icons.Rounded.Map, Destinations.MANAGE_TERRITORIES_BASE))
        // "The record will be seen in the publishers module... called
        // 'Meeting Assignments.' The publisher will see only meeting
        // assignments under their congregation" — read-only, see
        // GoPreachNavGraph's PUBLISHER_MEETING_ASSIGNMENTS composable.
        add(Tile(stringResource(R.string.home_tile_meeting_cart_assignment_title), stringResource(R.string.home_tile_meeting_cart_assignment_subtitle), Icons.Rounded.Event, Destinations.PUBLISHER_MEETING_ASSIGNMENTS))
        // "Add a Button under Meeting [and Cart] Assignment[:] 'My
        // Assignments'... the publisher can see all the assignments under
        // his name" — a cross-cut of every Midweek/Public Talk/Cart
        // Assignment record naming this publisher, not just the module's
        // own currently-selected week/date (see GoPreachNavGraph's
        // MY_ASSIGNMENTS composable / MeetingAssignmentsViewModel
        // .myAssignmentsFor).
        add(Tile(stringResource(R.string.home_tile_my_assignments_title), stringResource(R.string.home_tile_my_assignments_subtitle), Icons.Rounded.Assignment, Destinations.MY_ASSIGNMENTS))
        add(Tile(stringResource(R.string.home_tile_announcement_title), stringResource(R.string.home_tile_announcement_subtitle), Icons.Rounded.Campaign, Destinations.PUBLISHER_ANNOUNCEMENTS, unseenAnnouncements))
        // "Group Chat Setting" module — also reachable from the persistent
        // Chat Box icon in the header (see PublisherWelcomeHeader), this
        // tile is just a second, more discoverable entry point to the same
        // GROUP_CHAT_SETTING list.
        add(Tile(stringResource(R.string.home_tile_group_chat_title), stringResource(R.string.home_tile_group_chat_subtitle), Icons.AutoMirrored.Rounded.Chat, Destinations.GROUP_CHAT_SETTING))
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
        NavTab(stringResource(R.string.home_nav_home), Icons.Rounded.Home, Destinations.PUBLISHER_HOME),
        NavTab(stringResource(R.string.home_nav_reports), Icons.Rounded.Assignment, Destinations.MONTHLY_REPORT),
        NavTab(stringResource(R.string.home_nav_calendar), Icons.Rounded.CalendarMonth, Destinations.CALENDAR),
        NavTab(stringResource(R.string.home_tile_bible_study_title), Icons.AutoMirrored.Rounded.MenuBook, Destinations.BIBLE_STUDY),
        NavTab(stringResource(R.string.home_nav_profile), Icons.Rounded.Person, Destinations.SETTINGS),
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
        // "My Return Visit" is shown to every Publisher now, not just
        // Pioneers (see below) — so this listener needs to start
        // unconditionally too, or a Regular/Unbaptized Publisher's card
        // would just show a stuck 0. Also a pre-existing bug fix:
        // startVisitSync() returns a cold Flow — must be collected, or the
        // underlying Firestore listener never actually registers, so a
        // Return Visit logged on another device would never show up here.
        viewModel.startVisitSync(publisherPersonId).collect {}
    }
    val statsFlow = remember(publisherPersonId) { viewModel.statsFor(publisherPersonId) }
    val stats by statsFlow.collectAsStateWithLifecycle()
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.home_dashboard_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            DateRangeFilterBar(range = dateRange, onRangeChange = viewModel::setDateRange)
        }
    }

    // "My Bible Study" / "My Return Visit" / "Preaching Hours" — small,
    // colorless round icon buttons (only the icon is tinted) with the label
    // below the circle; each live count/value is shown right next to its
    // label, per request, rather than inside the circle. Each button gets
    // weight(1f) so its label is actually width-constrained to its own share
    // of the row — without it, a longer label (e.g. "Attended Preaching")
    // has no width to wrap/ellipsize against and just pushes the row wider
    // than the screen.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundIconActionButton(
            label = stringResource(R.string.home_stat_my_bible_study),
            value = stats.bibleStudiesCount.toString(),
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            onClick = { onNavigate(Destinations.BIBLE_STUDY) },
            modifier = Modifier.weight(1f),
        )
        // "My Return Visit" — shown to every Publisher, not just Pioneers
        // (a Return Visit record isn't Pioneer-exclusive; any Publisher can
        // have one). This used to be gated behind `isPioneer`, which is why
        // a Regular/Unbaptized Publisher never saw it at all.
        RoundIconActionButton(
            label = stringResource(R.string.home_stat_my_return_visit),
            value = stats.returnVisitsCount.toString(),
            icon = Icons.Rounded.PeopleAlt,
            onClick = { onNavigate(Destinations.RETURN_VISIT) },
            modifier = Modifier.weight(1f),
        )
        if (isPioneer) {
            RoundIconActionButton(
                label = stringResource(R.string.home_stat_preaching_hours),
                value = "%.1f".format(stats.preachingHours),
                icon = Icons.Rounded.Timer,
                onClick = { onNavigate(Destinations.PREACHING_TIME_RECORD) },
                modifier = Modifier.weight(1f),
            )
        } else {
            RoundIconActionButton(
                label = stringResource(R.string.home_stat_attended_preaching),
                value = if (stats.attendedPreaching) stringResource(R.string.home_yes) else stringResource(R.string.home_no),
                icon = Icons.Rounded.Assignment,
                onClick = { onNavigate(Destinations.MONTHLY_REPORT) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "FORWARD TO OTHER PUBLISHER" spec flow — fires one local notification per
 * app session per new pending count increase for records forwarded *to* this
 * Publisher, and one per outcome (Accept/Decline) landing on a request this
 * Publisher *sent* — same "one notification per session per new count"
 * pattern as AdminHomeScreen's ForwardRequestNotifier for the cross-
 * congregation flow. */
@Composable
private fun PublisherForwardNotifier(
    currentPersonId: String,
    viewModel: com.emfitsolutions.gopreach.ui.screens.pipeline.PublisherForwardRequestsViewModel,
) {
    val context = LocalContext.current

    val incomingFlow = remember(currentPersonId) { viewModel.incomingRequestsFor(currentPersonId) }
    val incoming by incomingFlow.collectAsStateWithLifecycle(initialValue = null)
    var lastIncomingCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(incoming) {
        val current = incoming ?: return@LaunchedEffect
        val previous = lastIncomingCount
        if (previous != null && current.size > previous) {
            com.emfitsolutions.gopreach.notifications.NotificationHelper.notify(
                context,
                id = 9200,
                title = "Record Forwarded to You",
                text = "Another publisher forwarded a Bible Study/Return Visit record to you.",
                category = com.emfitsolutions.gopreach.data.repository.NotificationCategory.TRANSFER_REQUEST,
            )
        }
        lastIncomingCount = current.size
    }

    val outgoingFlow = remember(currentPersonId) { viewModel.outgoingRequestsFor(currentPersonId) }
    val outgoing by outgoingFlow.collectAsStateWithLifecycle(initialValue = null)
    var lastOutgoingStatuses by remember { mutableStateOf<Map<String, com.emfitsolutions.gopreach.data.model.ForwardRequestStatus>?>(null) }
    LaunchedEffect(outgoing) {
        val current = outgoing ?: return@LaunchedEffect
        val previousStatuses = lastOutgoingStatuses
        if (previousStatuses != null) {
            current.forEach { request ->
                val wasPending = previousStatuses[request.id] == com.emfitsolutions.gopreach.data.model.ForwardRequestStatus.PENDING
                // Cancelled is excluded here — that's the sending publisher's
                // own action, on their own device, so there's nothing for
                // this "someone else acted on your request" notifier to tell
                // them; "was cancelled by [the recipient]" would also just be
                // wrong, since the recipient never touched it.
                if (wasPending &&
                    request.status != com.emfitsolutions.gopreach.data.model.ForwardRequestStatus.PENDING &&
                    request.status != com.emfitsolutions.gopreach.data.model.ForwardRequestStatus.CANCELLED
                ) {
                    com.emfitsolutions.gopreach.notifications.NotificationHelper.notify(
                        context,
                        id = 9200 + request.id.hashCode(),
                        title = "Forward Request Update",
                        text = "${request.personNameSnapshot} was ${request.status.name.lowercase()} by ${request.toPublisherNameSnapshot}.",
                        category = com.emfitsolutions.gopreach.data.repository.NotificationCategory.TRANSFER_REQUEST,
                    )
                }
            }
        }
        lastOutgoingStatuses = current.associate { it.id to it.status }
    }
}

/** "Forward to Other Congregation" spec flow (Return Visit's own wording:
 * "there will be a notification for the Service Overseer and the Publisher
 * for the status of the request") — notifies the *sending* publisher once
 * one of their own cross-congregation forwards is Accepted/Declined; the
 * Service Overseer side of this is already covered by AdminHomeScreen's
 * ForwardRequestNotifier. */
@Composable
private fun ForwardToCongregationSenderNotifier(
    currentPersonId: String,
    viewModel: com.emfitsolutions.gopreach.ui.screens.pipeline.PipelineViewModel,
) {
    val context = LocalContext.current
    val outgoingFlow = remember(currentPersonId) { viewModel.outgoingForwardRequestsFor(currentPersonId) }
    val outgoing by outgoingFlow.collectAsStateWithLifecycle(initialValue = null)
    var lastStatuses by remember { mutableStateOf<Map<String, com.emfitsolutions.gopreach.data.model.ForwardRequestStatus>?>(null) }
    LaunchedEffect(outgoing) {
        val current = outgoing ?: return@LaunchedEffect
        val previousStatuses = lastStatuses
        if (previousStatuses != null) {
            current.forEach { request ->
                val wasPending = previousStatuses[request.id] == com.emfitsolutions.gopreach.data.model.ForwardRequestStatus.PENDING
                // Cancelled excluded — see the same-congregation notifier's
                // matching comment above.
                if (wasPending &&
                    request.status != com.emfitsolutions.gopreach.data.model.ForwardRequestStatus.PENDING &&
                    request.status != com.emfitsolutions.gopreach.data.model.ForwardRequestStatus.CANCELLED
                ) {
                    com.emfitsolutions.gopreach.notifications.NotificationHelper.notify(
                        context,
                        id = 9300 + request.id.hashCode(),
                        title = "Forward Request Update",
                        text = "${request.personNameSnapshot} was ${request.status.name.lowercase()} by ${request.toCongregationNameSnapshot}.",
                        category = com.emfitsolutions.gopreach.data.repository.NotificationCategory.TRANSFER_REQUEST,
                    )
                }
            }
        }
        lastStatuses = current.associate { it.id to it.status }
    }
}
