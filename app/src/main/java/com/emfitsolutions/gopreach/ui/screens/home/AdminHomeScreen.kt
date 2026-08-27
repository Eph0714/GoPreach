package com.emfitsolutions.gopreach.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Permission
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.model.ScopeType
import com.emfitsolutions.gopreach.domain.PermissionChecker
import com.emfitsolutions.gopreach.ui.components.DashboardHero
import com.emfitsolutions.gopreach.ui.components.DashboardSection
import com.emfitsolutions.gopreach.ui.components.DashboardTile
import com.emfitsolutions.gopreach.ui.components.GoPreachSidePanelContent
import com.emfitsolutions.gopreach.ui.components.QuickAction
import com.emfitsolutions.gopreach.ui.components.SyncToServerButton
import com.emfitsolutions.gopreach.ui.navigation.Destinations
import com.emfitsolutions.gopreach.ui.screens.dashboard.DashboardStatsContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Landing point for the Admin context (spec §5.1) — a Side Panel (spec's
 * "Role-Based Dashboard, Side Panel & Graphical Reports" enhancement) plus a
 * hero (greeting + live sync/connectivity status + quick actions) over an
 * icon-grid dashboard, every tile/side-panel entry role-gated per the spec §3
 * permission matrix. The Side Panel and the tile grid below share the exact
 * same gating booleans, computed once here — one source of truth for "what
 * can this session navigate to," not two that could silently drift apart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    onSwitchToPublisher: (() -> Unit)?,
    /** Super-Admin always; an Admin only if explicitly granted MANAGE_USERS
     * (spec §2/§14 — "Admin can manage users only if explicitly authorized"). */
    canManageUsers: Boolean,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val session by viewModel.state.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val role = PermissionChecker.highestAdminRole(session.roleAssignments)

    val isSuperAdmin = role == AdminRole.SUPER_ADMIN
    // "REDESIGN THE CIRCUIT OVERSEER AND OTHER USER DASHBOARD" spec —
    // Circuit Overseer (and any other future grant-based restricted role)
    // carries no built-in access of its own (see AdminRole.CIRCUIT_OVERSEER's
    // doc comment); every capability they have comes from their own
    // UserAccessGrant instead. Checked by permission only, not scope, here —
    // this only decides whether a drawer item is *offered at all*; the
    // screen it opens, and firestore.rules underneath it, still enforce the
    // grant's actual congregation/group scope on every read and write.
    val grantPermissions = session.grant?.resolvedPermissions.orEmpty()
    val canEnrollCoordinatorElder = role == AdminRole.SUPER_ADMIN || role == AdminRole.ADMIN_PER_CONGREGATION
    val canEnrollRegularElderOrPublisher = canEnrollCoordinatorElder || role == AdminRole.COORDINATOR_ELDER
    // "CREATING PUBLISHER" spec — Service Overseer can also create/manage
    // Publishers under their own congregation, on top of everyone
    // [canEnrollRegularElderOrPublisher] already covers. Kept as its own flag
    // so Service Overseer doesn't also gain Regular Elder enrollment access.
    val canEnrollPublisher = canEnrollRegularElderOrPublisher || role == AdminRole.SERVICE_OVERSEER ||
        Permission.MANAGE_PUBLISHERS in grantPermissions
    // New Service Overseer role — unlike Coordinator Elder enrollment, a
    // Coordinator Elder *can* create one (same three-role set as Regular
    // Elder/Publisher enrollment above).
    val canEnrollServiceOverseer = canEnrollRegularElderOrPublisher
    // "MINISTERIAL ACCOUNT" spec — same enroller set as Service Overseer
    // (Super-Admin/Admin-own-congregation/Coordinator Elder), but with no
    // per-congregation cap: multiple Ministerial Servants are allowed.
    // Ministerial Servant is a RegularElderRole-adjacent AdminRole stored in
    // the same `roleAssignments` collection Regular Elder is — firestore.rules
    // gates that whole collection on VIEW_ELDERS/MANAGE_ELDERS regardless of
    // which AdminRole a given document actually holds, so a grant-based
    // Circuit Overseer's MANAGE_ELDERS permission already covers this at the
    // data layer; this just surfaces the matching drawer item too.
    val canEnrollMinisterialServant = canEnrollRegularElderOrPublisher || Permission.MANAGE_ELDERS in grantPermissions
    // "Announcement Module" — Super-Admin (any congregation), Admin/
    // Coordinator Elder (own congregation only). No Service Overseer/
    // Ministerial Servant per the spec's explicit access list.
    val canManageAnnouncements = canEnrollRegularElderOrPublisher
    // "Consolidated Monthly Report" spec — Service Overseer, Coordinator
    // Elder, Admin (own congregation), and Super-Admin (all congregations).
    val canViewConsolidatedReport = canEnrollRegularElderOrPublisher || role == AdminRole.SERVICE_OVERSEER
    // "Forward to Other Congregation" incoming review queue — same viewer set
    // as the Consolidated Report (Service Overseer is who actually acts on
    // these, Coordinator Elder/Admin/Super-Admin can see them too).
    val canViewForwardRequests = canViewConsolidatedReport
    // "Manage Publisher Report" module — Super-Admin (every congregation),
    // Admin/Coordinator Elder/Service Overseer (own congregation only); same
    // access set as the Consolidated Report. A Circuit Overseer with any of
    // the report-view permissions also reaches it, but always read-only —
    // see GoPreachNavGraph's MANAGE_PUBLISHER_REPORTS composable, which
    // computes that separately: firestore.rules blocks every grant holder
    // from writing `monthlyReports` regardless of permission ("A restricted
    // user's report access is view-only in every one of the spec's own
    // worked examples"), so this module can never grant them edit rights,
    // only viewing/printing.
    val canManagePublisherReports = canViewConsolidatedReport || grantPermissions.any {
        it == Permission.VIEW_PUBLISHER_REPORTS || it == Permission.VIEW_GROUP_REPORTS || it == Permission.VIEW_CONGREGATION_REPORTS
    }
    // Control Panel: full access for Super-Admin, own-congregation for Admin (spec §3 permission matrix).
    val canAccessControlPanel = role == AdminRole.SUPER_ADMIN || role == AdminRole.ADMIN_PER_CONGREGATION
    // User logs: Super-Admin (all) and Admin/Coordinator Elder (own congregation); Regular Elder has no access.
    val canViewUserLogs = role == AdminRole.SUPER_ADMIN || role == AdminRole.ADMIN_PER_CONGREGATION || role == AdminRole.COORDINATOR_ELDER
    // Publishers/Groups: Super-Admin/Admin/Coordinator Elder only (spec §3 permission matrix — Regular Elder ❌).
    val canManagePublishersAndGroups = canViewUserLogs
    // "CREATING GROUPS" spec — Service Overseer can also create/manage
    // Groups under their own congregation, same as Coordinator Elder,
    // without gaining the wider Publisher-management access above.
    val canManageGroups = canManagePublishersAndGroups || role == AdminRole.SERVICE_OVERSEER ||
        Permission.MANAGE_GROUPS in grantPermissions
    // Drawer-only widening of the Regular Elder item for a grant-based
    // Circuit Overseer — deliberately *not* folded into
    // [canEnrollRegularElderOrPublisher] itself, since that flag also drives
    // Service Overseer/Ministerial Servant enrollment and the Consolidated
    // Report below, none of which MANAGE_ELDERS implies.
    val canManageRegularEldersForDrawer = canEnrollRegularElderOrPublisher || Permission.MANAGE_ELDERS in grantPermissions
    // "Elder Dashboard Consistent with Admin/Super-Admin Dashboard" spec §1 —
    // every admin-track role (including both Elder roles) now navigates
    // through the same Side Panel-driven Main Form; the old tile-grid body
    // (quick actions, DashboardTile grid, "Sign Out" tile) is hidden for all
    // of them, not just Super-Admin/Admin. This used to leave Coordinator/
    // Regular Elder on the old tile-grid layout — a real, visible
    // inconsistency, not a deliberate role distinction — while
    // Super-Admin/Admin already had the clean drawer+stats Main Form.
    // Elder-appropriate destinations are still reachable, just through the
    // drawer (see GoPreachSidePanelContent, already gated by the same
    // canManagePublishersAndGroups/canEnrollRegularElderOrPublisher/etc.
    // booleans below, which are already Elder-aware).
    // "REDESIGN THE CIRCUIT OVERSEER AND OTHER USER DASHBOARD" spec: "Make the
    // Dashboard and main form the same as the admin user" — Circuit Overseer
    // (and any other future grant-based restricted role) gets the same
    // drawer+stats shell as every built-in Admin-track role, instead of the
    // old tile-grid layout it was still stuck on.
    val hideMainFormButtons = isSuperAdmin || role == AdminRole.ADMIN_PER_CONGREGATION ||
        role == AdminRole.COORDINATOR_ELDER || role == AdminRole.SERVICE_OVERSEER || role == AdminRole.REGULAR_ELDER ||
        role == AdminRole.MINISTERIAL_SERVANT || role == AdminRole.CIRCUIT_OVERSEER
    // Same scoping GoPreachNavGraph's standalone Dashboard Reports route uses
    // (see its `ownCongregationId ?: ownGroupAssignment?.congregationId`) —
    // reproduced here so the graphical Summary embedded below is scoped
    // identically wherever it's shown. This was missing the Regular Elder
    // fallback (their own RoleAssignment has a groupId, not a congregationId
    // set directly), which meant a Regular Elder's embedded Main Form summary
    // silently showed zero congregations' worth of data — a real scope bug,
    // not just a missing UI to reach the (correctly-scoped) standalone route.
    // resolvedRoleTypeOrNull() (never throws), not resolvedRoleType() — this
    // runs unconditionally on every Main Form composition, so one corrupt/
    // unparseable RoleAssignment used to crash the app immediately after a
    // correct login instead of just being skipped like it holds no such role.
    val ownCongregationId = session.roleAssignments.firstOrNull {
        (it.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role in setOf(AdminRole.ADMIN_PER_CONGREGATION, AdminRole.COORDINATOR_ELDER, AdminRole.SERVICE_OVERSEER, AdminRole.MINISTERIAL_SERVANT)
    }?.congregationId
    val ownGroupAssignment = session.roleAssignments.firstOrNull {
        (it.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER
    }
    val ownGroupCongregationId = ownGroupAssignment?.congregationId
    // A Circuit Overseer has no congregationId of their own to fall back to
    // (see AdminRole.CIRCUIT_OVERSEER's doc comment) — without this, the
    // `else` branch below would resolve to an *empty* set for them (not
    // "all"), silently showing zero congregations' worth of data on their
    // own dashboard. Their real scope lives on the grant instead: null (no
    // filter) for ALL_CONGREGATIONS, or the exact congregation list for
    // SELECTED_CONGREGATIONS. A SELECTED_GROUPS grant has no congregation-
    // level scope to derive here, so it stays empty rather than guessing.
    val grantScopeCongregationIds: Set<String>? = session.grant?.let { grant ->
        when (grant.resolvedScopeType) {
            ScopeType.ALL_CONGREGATIONS -> null
            ScopeType.SELECTED_CONGREGATIONS -> grant.scopeCongregationIds.toSet()
            ScopeType.SELECTED_GROUPS -> emptySet()
        }
    }
    val visibleCongregationIds: Set<String>? = when {
        isSuperAdmin -> null
        role == AdminRole.CIRCUIT_OVERSEER -> grantScopeCongregationIds
        else -> setOfNotNull(ownCongregationId ?: ownGroupCongregationId)
    }

    // "Forward to Other Congregation... The congregation 'Service Overseer'
    // in the receiving congregation will see a notification bell" spec —
    // this app has no push backend (see NotificationHelper's doc comment),
    // so this fires a local notification the moment a *new* pending request
    // streams in while this Main Form is composed, rather than a persisted
    // "unseen since last app close" count. The drawer's own live badge (see
    // GoPreachSidePanelContent/ForwardRequestsScreen) is what surfaces
    // requests that arrived before this session opened.
    if (canViewForwardRequests) {
        ForwardRequestNotifier(congregationIds = visibleCongregationIds)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Navigation-Compose restores this composable's saved DrawerState
    // (rememberDrawerState is itself Saveable) when returning here from a
    // side-panel-opened screen — including, occasionally, still mid-"Open"
    // if the animated drawerState.close() below got cancelled by
    // navigating away before its animation finished (this composable's own
    // coroutineScope is torn down the moment Compose Navigation swaps in
    // the destination screen). That left the drawer springing back open on
    // its own the next time the Main Form was shown, which read as "the
    // back button closes the entire side panel" — the drawer visibly
    // slamming open, then shut, instead of a plain Back to the Main Form.
    // Snapping closed unconditionally on every (re)composition removes
    // that race outright rather than depending on the animation's timing.
    LaunchedEffect(Unit) {
        drawerState.snapTo(DrawerValue.Closed)
    }

    // "Back Button and Page Navigation" spec §6/§7 — this is the Main Form
    // (root/home screen): the drawer, if open, must close on Back *before*
    // anything else (never navigate away or exit while it's open), and Back
    // from here otherwise means "exit the app," which needs its own
    // confirmation rather than silently closing GoPreach — Compose Navigation
    // has nothing left to pop to at this destination, so an unguarded Back
    // would finish the Activity immediately with no chance to back out.
    val activity = LocalContext.current as? ComponentActivity
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen) {
        showExitConfirm = true
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

    // Pull-to-refresh here is deliberately decoupled from both app-update
    // checking and from uploading pending changes (spec: "Refresh, Automatic
    // Updates, Offline Sync" — the three must stay completely independent;
    // this used to call both syncScheduler.requestSyncNow() *and*
    // updateViewModel.checkManually(), which was exactly the bug that spec
    // called out). See HomeViewModel.refreshData()'s doc comment for what
    // Refresh actually still does.
    var isRefreshing by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            GoPreachSidePanelContent(
                activeRoute = Destinations.ADMIN_HOME,
                canManageCongregationsAndAdmins = isSuperAdmin,
                canEnrollCoordinatorElder = canEnrollCoordinatorElder,
                canEnrollServiceOverseer = canEnrollServiceOverseer,
                canEnrollMinisterialServant = canEnrollMinisterialServant,
                canManageAnnouncements = canManageAnnouncements,
                canViewConsolidatedReport = canViewConsolidatedReport,
                canManagePublisherReports = canManagePublisherReports,
                canViewForwardRequests = canViewForwardRequests,
                canEnrollRegularElderOrPublisher = canManageRegularEldersForDrawer,
                canEnrollPublisher = canEnrollPublisher,
                canManagePublishersAndGroups = canManagePublishersAndGroups,
                canManageGroups = canManageGroups,
                canManageTerritories = canManagePublishersAndGroups,
                canAccessControlPanel = canAccessControlPanel,
                isSuperAdmin = isSuperAdmin,
                canViewUserLogs = canViewUserLogs,
                canManageUsers = canManageUsers,
                onSwitchToPublisher = onSwitchToPublisher?.let { switchAction ->
                    { coroutineScope.launch { drawerState.close() }; switchAction() }
                },
                onNavigate = { route ->
                    coroutineScope.launch { drawerState.close() }
                    onNavigate(route)
                },
                onSignOut = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.signOut()
                },
            )
        },
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                coroutineScope.launch {
                    viewModel.refreshData()
                    // Every screen already renders off a continuously-live Room
                    // cache (see HomeViewModel.refreshData()) — this brief delay is
                    // purely so the pull gesture gives visible feedback rather than
                    // resolving instantly, not a real network wait.
                    delay(400)
                    isRefreshing = false
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                DashboardHero(
                    greetingName = session.person?.firstName?.takeIf { it.isNotBlank() } ?: "there",
                    roleLabel = role?.name?.replace('_', ' ') ?: "GoPreach Admin",
                    isOnline = isOnline,
                    pendingSyncCount = pendingSyncCount,
                    leadingAction = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    topEndAction = {
                        IconButton(onClick = { onNavigate(Destinations.SETTINGS) }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    },
                    quickActions = if (hideMainFormButtons) {
                        emptyList()
                    } else {
                        buildList {
                            if (role != null) add(QuickAction("Dashboard", Icons.Rounded.BarChart) { onNavigate(Destinations.DASHBOARD_REPORTS) })
                            if (canManagePublishersAndGroups) add(QuickAction("Publishers", Icons.Rounded.People) { onNavigate(Destinations.MANAGE_PUBLISHERS) })
                            if (canManagePublishersAndGroups) add(QuickAction("Groups", Icons.Rounded.Groups) { onNavigate(Destinations.MANAGE_GROUPS) })
                            if (role != null) add(QuickAction("Calendar", Icons.Rounded.CalendarMonth) { onNavigate(Destinations.CALENDAR) })
                        }
                    },
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SyncToServerButton()

                    // "My Group / My Congregation" summary card — an Elder's own
                    // scope (already enforced everywhere via visibleCongregationIds)
                    // labeled with its actual name, so the numbers below aren't the
                    // only way to tell which congregation/group they refer to.
                    if (role == AdminRole.COORDINATOR_ELDER) {
                        MyScopeSummaryCard(congregationId = ownCongregationId, groupId = null)
                    } else if (role == AdminRole.REGULAR_ELDER) {
                        MyScopeSummaryCard(congregationId = ownGroupCongregationId, groupId = ownGroupAssignment?.groupId)
                    }

                    // Super-Admin/Admin: the graphical Summary (KPI cards + charts)
                    // shows directly on the main form instead of a tile grid — every
                    // *navigation* button (Publishers, Groups, Enrollment, Control
                    // Panel, Sign Out, ...) moved to the Side Panel, but the
                    // dashboard's own reporting content stays front and center here.
                    if (hideMainFormButtons) {
                        DashboardStatsContent(visibleCongregationIds = visibleCongregationIds)
                    }

                    if (!hideMainFormButtons) {
                        if (role != null) {
                            DashboardSection("Graphical Reports") {
                                DashboardTile("Reports Dashboard", Icons.Rounded.BarChart, { onNavigate(Destinations.DASHBOARD_REPORTS) })
                            }
                        }

                        if (canManagePublishersAndGroups) {
                            DashboardSection("Management") {
                                DashboardTile("Publishers", Icons.Rounded.People, { onNavigate(Destinations.MANAGE_PUBLISHERS) })
                                DashboardTile("Groups", Icons.Rounded.Groups, { onNavigate(Destinations.MANAGE_GROUPS) })
                                DashboardTile("Territories", Icons.Rounded.Map, { onNavigate(Destinations.MANAGE_TERRITORIES) })
                            }
                        }

                        if (role != null) {
                            DashboardSection("Ministry") {
                                DashboardTile("Chat Schedule", Icons.Rounded.Chat, { onNavigate(Destinations.MANAGE_CHAT_SCHEDULES) })
                                DashboardTile("Reports Summary", Icons.Rounded.Assessment, { onNavigate(Destinations.REPORTS) })
                                DashboardTile("Share Location", Icons.Rounded.LocationOn, { onNavigate(Destinations.SHARE_LOCATION) })
                                DashboardTile("Calendar", Icons.Rounded.CalendarMonth, { onNavigate(Destinations.CALENDAR) })
                            }
                        }

                        if (isSuperAdmin || canEnrollCoordinatorElder || canEnrollRegularElderOrPublisher) {
                            DashboardSection("Enrollment") {
                                if (isSuperAdmin) {
                                    DashboardTile("Congregations", Icons.Rounded.AccountBalance, { onNavigate(Destinations.MANAGE_CONGREGATIONS) })
                                    DashboardTile("Admins", Icons.Rounded.AdminPanelSettings, { onNavigate(Destinations.MANAGE_ADMINS) })
                                }
                                if (canEnrollCoordinatorElder) {
                                    DashboardTile("Coordinator Elder", Icons.Rounded.PersonAdd, { onNavigate(Destinations.MANAGE_COORDINATOR_ELDERS) })
                                }
                                if (canEnrollRegularElderOrPublisher) {
                                    DashboardTile("Regular Elder", Icons.Rounded.PersonAdd, { onNavigate(Destinations.MANAGE_REGULAR_ELDERS) })
                                    DashboardTile("Publisher", Icons.Rounded.PersonAdd, { onNavigate(Destinations.ENROLL_PUBLISHER) })
                                }
                            }
                        }

                        if (canAccessControlPanel || isSuperAdmin || canViewUserLogs || canManageUsers) {
                            DashboardSection("System") {
                                if (canAccessControlPanel) {
                                    DashboardTile("Control Panel", Icons.Rounded.Tune, { onNavigate(Destinations.CONTROL_PANEL) })
                                }
                                if (isSuperAdmin) {
                                    DashboardTile("Backup & Restore", Icons.Rounded.Backup, { onNavigate(Destinations.BACKUP_RESTORE) })
                                }
                                if (canViewUserLogs) {
                                    DashboardTile("User Logs", Icons.Rounded.History, { onNavigate(Destinations.USER_LOGS) })
                                }
                                if (canManageUsers) {
                                    DashboardTile("User Management", Icons.Rounded.ManageAccounts, { onNavigate(Destinations.MANAGE_USERS) })
                                }
                            }
                        }

                        DashboardSection("Account") {
                            DashboardTile("Account Settings", Icons.Rounded.Password, { onNavigate(Destinations.ACCOUNT_SETTINGS) })
                            if (onSwitchToPublisher != null) {
                                DashboardTile("Ministry Report App", Icons.Rounded.SwapHoriz, onSwitchToPublisher)
                            }
                            DashboardTile("Sign Out", Icons.AutoMirrored.Rounded.Logout, viewModel::signOut)
                        }
                    }
                }
            }
        }
    }
}

/** See the call site's doc comment above — fires one local notification per
 * app session per new pending count increase, not on the initial load. */
@Composable
private fun ForwardRequestNotifier(
    congregationIds: Set<String>?,
    viewModel: com.emfitsolutions.gopreach.ui.screens.pipeline.ForwardRequestsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val requestsFlow = remember(congregationIds) { viewModel.pendingRequestsFor(congregationIds) }
    val requests by requestsFlow.collectAsStateWithLifecycle(initialValue = null)
    var lastSeenCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(requests) {
        val current = requests ?: return@LaunchedEffect
        val previous = lastSeenCount
        if (previous != null && current.size > previous) {
            com.emfitsolutions.gopreach.notifications.NotificationHelper.notify(
                context,
                id = 9100,
                title = "New Forward Request",
                text = "A publisher has forwarded a record to your congregation for review.",
            )
        }
        lastSeenCount = current.size
    }
}
