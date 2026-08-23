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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.domain.PermissionChecker
import com.emfitsolutions.gopreach.ui.components.DashboardHero
import com.emfitsolutions.gopreach.ui.components.DashboardSection
import com.emfitsolutions.gopreach.ui.components.DashboardTile
import com.emfitsolutions.gopreach.ui.components.DynamicAppLogo
import com.emfitsolutions.gopreach.ui.components.GoPreachSidePanelContent
import com.emfitsolutions.gopreach.ui.components.QuickAction
import com.emfitsolutions.gopreach.ui.components.SyncStatusButton
import com.emfitsolutions.gopreach.ui.components.UpdateAvailableBanner
import com.emfitsolutions.gopreach.ui.navigation.Destinations
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
    val canEnrollCoordinatorElder = role == AdminRole.SUPER_ADMIN || role == AdminRole.ADMIN_PER_CONGREGATION
    val canEnrollRegularElderOrPublisher = canEnrollCoordinatorElder || role == AdminRole.COORDINATOR_ELDER
    // Control Panel: full access for Super-Admin, own-congregation for Admin (spec §3 permission matrix).
    val canAccessControlPanel = role == AdminRole.SUPER_ADMIN || role == AdminRole.ADMIN_PER_CONGREGATION
    // User logs: Super-Admin (all) and Admin/Coordinator Elder (own congregation); Regular Elder has no access.
    val canViewUserLogs = role == AdminRole.SUPER_ADMIN || role == AdminRole.ADMIN_PER_CONGREGATION || role == AdminRole.COORDINATOR_ELDER
    // Publishers/Groups: Super-Admin/Admin/Coordinator Elder only (spec §3 permission matrix — Regular Elder ❌).
    val canManagePublishersAndGroups = canViewUserLogs
    // Per explicit request: Super-Admin and Admin now navigate entirely through
    // the Side Panel — the main dashboard body (tile grid, hero quick actions,
    // and the old "Sign Out" tile) is hidden for them; everything it used to
    // offer is still reachable from the drawer instead (see GoPreachSidePanelContent).
    val hideMainFormButtons = isSuperAdmin || role == AdminRole.ADMIN_PER_CONGREGATION

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            GoPreachSidePanelContent(
                activeRoute = Destinations.ADMIN_HOME,
                canManageCongregationsAndAdmins = isSuperAdmin,
                canEnrollCoordinatorElder = canEnrollCoordinatorElder,
                canEnrollRegularElderOrPublisher = canEnrollRegularElderOrPublisher,
                canManagePublishersAndGroups = canManagePublishersAndGroups,
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
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DashboardHero(
                greetingName = session.person?.firstName?.takeIf { it.isNotBlank() } ?: "there",
                roleLabel = role?.name?.replace('_', ' ') ?: "GoPreach Admin",
                isOnline = isOnline,
                pendingSyncCount = pendingSyncCount,
                logoContent = { DynamicAppLogo() },
                topEndAction = {
                    Row {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                        SyncStatusButton()
                        IconButton(onClick = { onNavigate(Destinations.SETTINGS) }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                        }
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
                UpdateAvailableBanner()

                // Super-Admin/Admin: everything below lives in the Side Panel
                // instead (hamburger icon, top-left) — including Sign Out.
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
