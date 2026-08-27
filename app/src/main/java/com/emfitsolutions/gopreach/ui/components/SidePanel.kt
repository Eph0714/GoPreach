package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.ui.navigation.Destinations

/** One leaf item in the Side Panel's treeview (spec §2). */
private data class SideItem(val label: String, val icon: ImageVector, val route: String)

/** One collapsible group (spec §2: "Side Panel Treeview" — Enrollment / Control
 * Panel / Other modules), built from whichever [items] the caller already
 * decided this session is authorized to see — this composable does no
 * permission checks of its own, matching the rest of the app's "gating
 * decisions live with the caller, not the widget" convention. */
private data class SideSection(val title: String, val items: List<SideItem>)

/**
 * The Side Panel content (spec §1-§2) — a role-filtered, collapsible-section
 * navigation drawer. [activeRoute] highlights the current destination (spec
 * §1: "Highlight the active menu"); every list here is pre-filtered by the
 * caller (see AdminHomeScreen) using the exact same booleans that already
 * gate the dashboard's own tile grid, so the drawer can never offer a route
 * the grid itself wouldn't — one source of truth for "what can this session
 * navigate to," not two that could drift apart.
 */
@Composable
fun GoPreachSidePanelContent(
    activeRoute: String?,
    canManageCongregationsAndAdmins: Boolean,
    canEnrollCoordinatorElder: Boolean,
    canEnrollServiceOverseer: Boolean,
    canEnrollMinisterialServant: Boolean,
    canViewConsolidatedReport: Boolean,
    canViewForwardRequests: Boolean,
    canEnrollRegularElderOrPublisher: Boolean,
    canManagePublishersAndGroups: Boolean,
    /** "CREATING GROUPS" spec — Coordinator Elder *or* Service Overseer can
     * create a Group under their own congregation, in addition to everyone
     * [canManagePublishersAndGroups] already covers (Super-Admin/Admin/
     * Coordinator Elder). Service Overseer gets Groups specifically, not the
     * wider Publisher-management access. */
    canManageGroups: Boolean,
    canManageTerritories: Boolean,
    canAccessControlPanel: Boolean,
    isSuperAdmin: Boolean,
    canViewUserLogs: Boolean,
    canManageUsers: Boolean,
    /** Non-null only for a session holding both an Admin-track role and a
     * Publisher category (spec's pre-existing "switch context" affordance) —
     * relocated here now that Super-Admin/Admin's main dashboard body is
     * hidden, so it isn't silently lost for anyone who had it. */
    onSwitchToPublisher: (() -> Unit)?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val sections = buildList {
        val enrollmentItems = buildList {
            if (canManageCongregationsAndAdmins) add(SideItem("Congregations", Icons.Rounded.AccountBalance, Destinations.MANAGE_CONGREGATIONS))
            if (canManageCongregationsAndAdmins) add(SideItem("Admins", Icons.Rounded.AdminPanelSettings, Destinations.MANAGE_ADMINS))
            if (canEnrollCoordinatorElder) add(SideItem("Coordinator Elder", Icons.Rounded.PersonAdd, Destinations.MANAGE_COORDINATOR_ELDERS))
            if (canEnrollServiceOverseer) add(SideItem("Service Overseer", Icons.Rounded.PersonAdd, Destinations.MANAGE_SERVICE_OVERSEERS))
            if (canEnrollMinisterialServant) add(SideItem("Ministerial Servant", Icons.Rounded.PersonAdd, Destinations.MANAGE_MINISTERIAL_SERVANTS))
            if (canManageGroups) add(SideItem("Groups", Icons.Rounded.Groups, Destinations.MANAGE_GROUPS))
            if (canEnrollRegularElderOrPublisher) add(SideItem("Regular Elder", Icons.Rounded.PersonAdd, Destinations.MANAGE_REGULAR_ELDERS))
            // Routes to the Manage Publishers *list* screen (which has its own
            // onAddNew FAB into ENROLL_PUBLISHER), matching every other entry
            // in this section (Congregations/Admins/Coordinator Elder/Regular
            // Elder all go to their list screen too) — this used to jump
            // straight to enrollment instead, which meant Admin/Coordinator
            // Elder had no way to reach the Publisher list/edit screen at all
            // once the old tile grid (their only other route to it) was
            // hidden for them.
            if (canEnrollRegularElderOrPublisher) add(SideItem("Publisher", Icons.Rounded.People, Destinations.MANAGE_PUBLISHERS))
            if (canManageTerritories) add(SideItem("Territories", Icons.Rounded.Map, Destinations.MANAGE_TERRITORIES))
        }
        if (enrollmentItems.isNotEmpty()) add(SideSection("Enrollment", enrollmentItems))

        val controlPanelItems = buildList {
            if (isSuperAdmin) add(SideItem("Backup & Restore", Icons.Rounded.Backup, Destinations.BACKUP_RESTORE))
            if (canAccessControlPanel) add(SideItem("Appearance & App Logo", Icons.Rounded.Tune, Destinations.CONTROL_PANEL))
        }
        if (controlPanelItems.isNotEmpty()) add(SideSection("Control Panel", controlPanelItems))

        val otherItems = buildList {
            // "Elder Dashboard Consistent with Admin/Super-Admin Dashboard"
            // spec — these two used to be reachable only via the old tile-grid
            // Main Form body, which is now hidden for every admin-track role
            // (Super-Admin/Admin already, Coordinator/Regular Elder as of this
            // change too, see AdminHomeScreen). Every session reaching this
            // drawer already has an admin-track role, so no extra gating
            // boolean is needed here — this restores the same reach the tile
            // grid used to give everyone, rather than stranding whoever's
            // tile grid gets hidden next.
            add(SideItem("Dashboard", Icons.Rounded.BarChart, Destinations.DASHBOARD_REPORTS))
            add(SideItem("Chat Schedule", Icons.AutoMirrored.Rounded.Chat, Destinations.MANAGE_CHAT_SCHEDULES))
            add(SideItem("Reports Summary", Icons.Rounded.Assessment, Destinations.REPORTS))
            if (canViewConsolidatedReport) {
                add(SideItem("Consolidated Report", Icons.Rounded.Assessment, Destinations.CONSOLIDATED_REPORT))
            }
            if (canViewForwardRequests) {
                add(SideItem("Forward Requests", Icons.Rounded.SwapHoriz, Destinations.FORWARD_REQUESTS))
            }
            add(SideItem("Calendar", Icons.Rounded.CalendarMonth, Destinations.CALENDAR))
            add(SideItem("Share Location Settings", Icons.Rounded.LocationOn, Destinations.SHARE_LOCATION))
            if (canViewUserLogs) add(SideItem("User Logs", Icons.Rounded.History, Destinations.USER_LOGS))
            if (canManageUsers) add(SideItem("User Management", Icons.Rounded.ManageAccounts, Destinations.MANAGE_USERS))
        }
        add(SideSection("Other", otherItems))
    }

    ModalDrawerSheet {
        Text(
            "GoPreach",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        LazyColumn {
            items(sections) { section -> SidePanelSection(section, activeRoute, onNavigate) }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Account Settings") },
                    icon = { Icon(Icons.Rounded.Password, contentDescription = null) },
                    selected = activeRoute == Destinations.ACCOUNT_SETTINGS,
                    onClick = { onNavigate(Destinations.ACCOUNT_SETTINGS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                if (onSwitchToPublisher != null) {
                    NavigationDrawerItem(
                        label = { Text("Ministry Report App") },
                        icon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                        selected = false,
                        onClick = onSwitchToPublisher,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
                NavigationDrawerItem(
                    label = { Text("Sign Out") },
                    icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                    selected = false,
                    onClick = onSignOut,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
    }
}

@Composable
private fun SidePanelSection(section: SideSection, activeRoute: String?, onNavigate: (String) -> Unit) {
    var expanded by remember(section.title) { mutableStateOf(true) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
        }
        if (expanded) {
            section.items.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    selected = activeRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
    }
}
