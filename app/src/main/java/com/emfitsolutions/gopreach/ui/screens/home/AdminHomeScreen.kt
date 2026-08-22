package com.emfitsolutions.gopreach.ui.screens.home

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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.domain.PermissionChecker
import com.emfitsolutions.gopreach.ui.components.AppBanner
import com.emfitsolutions.gopreach.ui.components.DashboardSection
import com.emfitsolutions.gopreach.ui.components.DashboardTile
import com.emfitsolutions.gopreach.ui.components.DynamicAppLogo
import com.emfitsolutions.gopreach.ui.navigation.Destinations

/** Landing point for the Admin context (spec §5.1) — an icon-grid dashboard,
 * every tile role-gated per the spec §3 permission matrix. */
@Composable
fun AdminHomeScreen(
    onSwitchToPublisher: (() -> Unit)?,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val session by viewModel.state.collectAsStateWithLifecycle()
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppBanner(
            title = "GoPreach Admin",
            subtitle = session.person?.fullName,
            logoContent = { DynamicAppLogo() },
            topEndAction = {
                IconButton(onClick = { onNavigate(Destinations.SETTINGS) }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Signed in as ${role?.name?.replace('_', ' ') ?: "Unknown role"}",
                style = MaterialTheme.typography.titleMedium,
            )

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
                    DashboardTile("Reports", Icons.Rounded.Assessment, { onNavigate(Destinations.REPORTS) })
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
                        DashboardTile("Coordinator Elder", Icons.Rounded.PersonAdd, { onNavigate(Destinations.ENROLL_COORDINATOR_ELDER) })
                    }
                    if (canEnrollRegularElderOrPublisher) {
                        DashboardTile("Regular Elder", Icons.Rounded.PersonAdd, { onNavigate(Destinations.ENROLL_REGULAR_ELDER) })
                        DashboardTile("Publisher", Icons.Rounded.PersonAdd, { onNavigate(Destinations.ENROLL_PUBLISHER) })
                    }
                }
            }

            if (canAccessControlPanel || isSuperAdmin || canViewUserLogs) {
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
                }
            }

            DashboardSection("Account") {
                if (onSwitchToPublisher != null) {
                    DashboardTile("Ministry Report App", Icons.Rounded.SwapHoriz, onSwitchToPublisher)
                }
                DashboardTile("Sign Out", Icons.AutoMirrored.Rounded.Logout, viewModel::signOut)
            }
        }
    }
}
