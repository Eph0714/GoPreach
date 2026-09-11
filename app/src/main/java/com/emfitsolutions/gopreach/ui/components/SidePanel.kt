package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.R
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
    canManageAnnouncements: Boolean,
    canViewConsolidatedReport: Boolean,
    canViewFieldServiceGroupReport: Boolean,
    canManagePublisherReports: Boolean,
    canViewForwardRequests: Boolean,
    canEnrollRegularElderOrPublisher: Boolean,
    /** "CREATING PUBLISHER" spec — Service Overseer can also create/manage
     * Publishers under their own congregation, in addition to everyone
     * [canEnrollRegularElderOrPublisher] already covers. */
    canEnrollPublisher: Boolean,
    canManagePublishersAndGroups: Boolean,
    /** "CREATING GROUPS" spec — Coordinator Elder *or* Service Overseer can
     * create a Group under their own congregation, in addition to everyone
     * [canManagePublishersAndGroups] already covers (Super-Admin/Admin/
     * Coordinator Elder). Service Overseer gets Groups specifically, not the
     * wider Publisher-management access. */
    canManageGroups: Boolean,
    canManageTerritories: Boolean,
    canEditMeetingAssignments: Boolean,
    canAccessControlPanel: Boolean,
    isSuperAdmin: Boolean,
    canViewUserLogs: Boolean,
    canManageUsers: Boolean,
    /** "Contact Record" module — Super-Admin, Coordinator Elder, and Regular
     * Elder only (not Admin, not Service Overseer/Ministerial Servant). */
    canViewContactRecord: Boolean,
    /** Spec §15 — "Elders should be able to see Interested Person information
     * according to their existing Congregation/Group access scope": Admin/
     * Coordinator Elder/Service Overseer/Regular Elder, read-only, scoped to
     * their own congregation (or own Group). Distinct from [isSuperAdmin]'s
     * own [Destinations.ALL_INTERESTED_RECORDS] item below, which is full
     * read/write across every congregation. */
    canViewInterestedPeopleScope: Boolean,
    /** Always null since "Multiple Role Login Detection & Role Selection"
     * (spec §7/§11) retired the old mid-session Admin<->Publisher switch in
     * favor of choosing a role once at login — kept as a parameter (instead
     * of deleted outright) only so this drawer item's rendering doesn't need
     * touching if that ever changes; see GoPreachNavGraph's ADMIN_HOME
     * composable for the actual call site. */
    onSwitchToPublisher: (() -> Unit)?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    // "Admin Dashboard Menu Reorganization" spec — the drawer's existing
    // collapsible-section treeview (this composable already had exactly
    // that shape) regrouped into ENROLLMENT / REPORTS / CONTROL PANEL, in
    // that exact order (spec §1/§26-28), instead of the old Enrollment/
    // Control Panel/"Other" split. Every item keeps the *exact same* gating
    // boolean it already had — this only moves *where* an already-authorized
    // item is displayed, never who is authorized to see it (spec §34: "Do
    // not hard-code access based on menu location").
    val sections = buildList {
        // ENROLLMENT — "Everything related to enrolling and managing users,
        // publishers, congregation personnel, congregation records, and
        // organizational enrollment" (spec §2/final requirement). Territory
        // Map/Meeting & Cart Assignment/Announcements moved out to CONTROL
        // PANEL below — spec §4/§9/§36 explicitly list all three there, not
        // here.
        val enrollmentItems = buildList {
            if (canManageCongregationsAndAdmins) add(SideItem(stringResource(R.string.side_congregations_groups), Icons.Rounded.AccountBalance, Destinations.MANAGE_CONGREGATIONS))
            if (canManageCongregationsAndAdmins) add(SideItem(stringResource(R.string.side_admins), Icons.Rounded.AdminPanelSettings, Destinations.MANAGE_ADMINS))
            // "Consolidate Elder, Coordinator Elder, Service Overseer and
            // Secretary Enrollment" — one "Elders" item (spec §1/§43)
            // replaces the separate Coordinator Elder/Service Overseer/
            // Regular Elder items this drawer used to show; gated the same
            // way Coordinator Elder enrollment itself always was
            // ([canEnrollRegularElderOrPublisher] already implies
            // [canEnrollCoordinatorElder]/[canEnrollServiceOverseer] — see
            // AdminHomeScreen's own derivation of all three).
            if (canEnrollRegularElderOrPublisher) add(SideItem(stringResource(R.string.side_elders), Icons.Rounded.PersonAdd, Destinations.MANAGE_ELDERS))
            if (canEnrollMinisterialServant) add(SideItem(stringResource(R.string.side_ministerial_servant), Icons.Rounded.PersonAdd, Destinations.MANAGE_MINISTERIAL_SERVANTS))
            if (canManageGroups) add(SideItem(stringResource(R.string.side_groups), Icons.Rounded.Groups, Destinations.MANAGE_GROUPS))
            // Routes to the Manage Publishers *list* screen (which has its own
            // onAddNew FAB into ENROLL_PUBLISHER), matching every other entry
            // in this section (Congregations/Admins/Coordinator Elder/Regular
            // Elder all go to their list screen too) — this used to jump
            // straight to enrollment instead, which meant Admin/Coordinator
            // Elder had no way to reach the Publisher list/edit screen at all
            // once the old tile grid (their only other route to it) was
            // hidden for them.
            if (canEnrollPublisher) add(SideItem(stringResource(R.string.side_publisher), Icons.Rounded.People, Destinations.MANAGE_PUBLISHERS))
        }
        if (enrollmentItems.isNotEmpty()) add(SideSection(stringResource(R.string.side_section_enrollment), enrollmentItems))

        // REPORTS — "Everything related to reporting, report submission,
        // report management, report history, statistics, preaching records,
        // and report exports" (spec §3/final requirement). House Holder
        // Visit History and Preaching Time Records land here per spec §14/
        // §15/§36 explicitly; Interested Records/Forward Requests join them
        // as the same underlying-record review/approval domain (spec §3's
        // own "Report Approval/Unlocking" example already treats a review
        // queue as a Reports-shaped action, not Enrollment or Control Panel).
        val reportsItems = buildList {
            // "Elder Dashboard Consistent with Admin/Super-Admin Dashboard"
            // spec — these two used to be reachable only via the old tile-grid
            // Main Form body, which is now hidden for every admin-track role
            // (Super-Admin/Admin already, Coordinator/Regular Elder as of this
            // change too, see AdminHomeScreen). Every session reaching this
            // drawer already has an admin-track role, so no extra gating
            // boolean is needed here — this restores the same reach the tile
            // grid used to give everyone, rather than stranding whoever's
            // tile grid gets hidden next.
            add(SideItem(stringResource(R.string.home_dashboard_header), Icons.Rounded.BarChart, Destinations.DASHBOARD_REPORTS))
            add(SideItem(stringResource(R.string.side_reports_summary), Icons.Rounded.Assessment, Destinations.REPORTS))
            if (canViewConsolidatedReport) {
                add(SideItem(stringResource(R.string.side_consolidated_report), Icons.Rounded.Assessment, Destinations.CONSOLIDATED_REPORT))
            }
            if (canViewFieldServiceGroupReport) {
                add(SideItem(stringResource(R.string.side_field_service_group_report), Icons.Rounded.Groups, Destinations.FIELD_SERVICE_GROUP_REPORT))
            }
            // "Manage Publisher Report" module — same access set as the
            // Consolidated Report (Super-Admin/Admin/Coordinator Elder/
            // Service Overseer).
            if (canManagePublisherReports) {
                add(SideItem(stringResource(R.string.side_publisher_reports), Icons.Rounded.Assessment, Destinations.MANAGE_PUBLISHER_REPORTS))
            }
            if (canViewForwardRequests) {
                add(SideItem(stringResource(R.string.side_forward_requests), Icons.Rounded.SwapHoriz, Destinations.FORWARD_REQUESTS))
            }
            // "Forward Request Module" — Super-Admin only: every forward
            // request, both kinds, every status, all-congregations
            // filterable, with edit/delete (see ForwardRequestModuleScreen's
            // own doc comment) — distinct from the Service Overseer-facing
            // Accept/Decline queue right above.
            if (isSuperAdmin) {
                add(SideItem(stringResource(R.string.side_forward_request_module), Icons.Rounded.SwapHoriz, Destinations.FORWARD_REQUEST_MODULE))
            }
            if (canViewInterestedPeopleScope) add(SideItem(stringResource(R.string.side_interested_records_scoped), Icons.Rounded.Groups, Destinations.SCOPED_INTERESTED_RECORDS))
            // "The super admin can see all congregation Search[ing]/Bible
            // Study/Return Visit record[s]... Add, Edit, [and permanently]
            // Delete the record" — Super-Admin only, unlike every other
            // Searching/Return Visit/Bible Study entry point in this app
            // (Publisher context, own records only).
            if (isSuperAdmin) add(SideItem(stringResource(R.string.side_interested_records_all_congregations), Icons.Rounded.Groups, Destinations.ALL_INTERESTED_RECORDS))
            // "House Holder Visit History" — Super-Admin only in this
            // drawer; a Publisher reaches the same screen via their own Main
            // Form tile instead (see PublisherHomeScreen).
            if (isSuperAdmin) add(SideItem(stringResource(R.string.side_householder_visit_history), Icons.AutoMirrored.Rounded.ListAlt, Destinations.HOUSEHOLDER_VISIT_HISTORY))
            // "Preaching Time Records — Super Admin Management Module" —
            // same "Super-Admin only" gating as the item above.
            if (isSuperAdmin) add(SideItem(stringResource(R.string.side_preaching_time_records_all_congregations), Icons.Rounded.Schedule, Destinations.ALL_PREACHING_TIME_RECORDS))
        }
        if (reportsItems.isNotEmpty()) add(SideSection(stringResource(R.string.side_section_reports), reportsItems))

        // CONTROL PANEL — "Everything related to system settings,
        // configuration, assignments, communication controls, logs,
        // administrative controls, and Theme Color Settings" (spec §4/final
        // requirement).
        val controlPanelItems = buildList {
            if (isSuperAdmin) add(SideItem(stringResource(R.string.side_backup_restore), Icons.Rounded.Backup, Destinations.BACKUP_RESTORE))
            if (canAccessControlPanel) add(SideItem(stringResource(R.string.side_appearance_app_logo), Icons.Rounded.Tune, Destinations.CONTROL_PANEL))
            add(SideItem(stringResource(R.string.side_group_chat_setting), Icons.AutoMirrored.Rounded.Chat, Destinations.GROUP_CHAT_SETTING))
            if (canManageAnnouncements) add(SideItem(stringResource(R.string.side_announcements), Icons.Rounded.Campaign, Destinations.MANAGE_ANNOUNCEMENTS))
            add(SideItem(stringResource(R.string.home_nav_calendar), Icons.Rounded.CalendarMonth, Destinations.CALENDAR))
            if (canEditMeetingAssignments) add(SideItem(stringResource(R.string.home_tile_meeting_cart_assignment_title), Icons.Rounded.Event, Destinations.MEETING_ASSIGNMENTS))
            if (canManageTerritories) add(SideItem(stringResource(R.string.side_territory_map), Icons.Rounded.Map, Destinations.MANAGE_TERRITORIES_BASE))
            add(SideItem(stringResource(R.string.side_share_location_settings), Icons.Rounded.LocationOn, Destinations.SHARE_LOCATION))
            if (canViewUserLogs) add(SideItem(stringResource(R.string.side_user_logs), Icons.Rounded.History, Destinations.USER_LOGS))
            if (canViewContactRecord) add(SideItem(stringResource(R.string.side_contact_record), Icons.Rounded.Contacts, Destinations.CONTACT_RECORD))
            // "Theme Color Settings — Simplified User Experience" (spec §16/
            // §24) — a per-device preference every signed-in role already
            // had (via the profile menu's Settings screen); shown here
            // unconditionally, same as Account Settings at the bottom of
            // this drawer, not gated by [canAccessControlPanel] — being
            // listed under Control Panel doesn't narrow who could already
            // reach it (spec §34).
            add(SideItem(stringResource(R.string.side_theme_color_settings), Icons.Rounded.Palette, Destinations.THEME_COLOR_SETTINGS))
            if (canManageUsers) add(SideItem(stringResource(R.string.side_user_management), Icons.Rounded.ManageAccounts, Destinations.MANAGE_USERS))
        }
        if (controlPanelItems.isNotEmpty()) add(SideSection(stringResource(R.string.side_section_control_panel), controlPanelItems))
    }

    ModalDrawerSheet {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        LazyColumn {
            items(sections) { section -> SidePanelSection(section, activeRoute, onNavigate) }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { SideItemLabel(stringResource(R.string.side_account_settings)) },
                    icon = { Icon(Icons.Rounded.Password, contentDescription = null) },
                    selected = activeRoute == Destinations.ACCOUNT_SETTINGS,
                    onClick = { onNavigate(Destinations.ACCOUNT_SETTINGS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                if (onSwitchToPublisher != null) {
                    NavigationDrawerItem(
                        label = { SideItemLabel(stringResource(R.string.side_ministry_report_app)) },
                        icon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                        selected = false,
                        onClick = onSwitchToPublisher,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
                NavigationDrawerItem(
                    label = { SideItemLabel(stringResource(R.string.side_sign_out)) },
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
                    label = { SideItemLabel(item.label) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    selected = activeRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
    }
}

/** Every drawer item's label — one line, ellipsized rather than wrapped, so
 * a longer entry (e.g. "Appearance & App Logo", "Share Location Settings")
 * never wraps into NavigationDrawerItem's fixed-height row and gets its
 * second line silently clipped. */
@Composable
private fun SideItemLabel(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
