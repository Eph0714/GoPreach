package com.emfitsolutions.gopreach.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.domain.PermissionChecker
import com.emfitsolutions.gopreach.domain.SessionContext
import com.emfitsolutions.gopreach.ui.screens.about.AboutScreen
import com.emfitsolutions.gopreach.ui.screens.account.AccountSettingsScreen
import com.emfitsolutions.gopreach.ui.screens.auth.ForcedPasswordChangeScreen
import com.emfitsolutions.gopreach.ui.screens.auth.ForgotPasswordScreen
import com.emfitsolutions.gopreach.ui.screens.admins.ManageAdminsScreen
import com.emfitsolutions.gopreach.ui.screens.backup.BackupRestoreScreen
import com.emfitsolutions.gopreach.ui.screens.biblestudy.BibleStudyScreen
import com.emfitsolutions.gopreach.ui.screens.calendar.CalendarScope
import com.emfitsolutions.gopreach.ui.screens.calendar.CalendarScreen
import com.emfitsolutions.gopreach.ui.screens.congregations.ManageCongregationsScreen
import com.emfitsolutions.gopreach.ui.screens.controlpanel.ControlPanelScreen
import com.emfitsolutions.gopreach.ui.screens.dashboard.DashboardReportsScreen
import com.emfitsolutions.gopreach.ui.screens.elders.ManageCoordinatorEldersScreen
import com.emfitsolutions.gopreach.ui.screens.elders.ManageRegularEldersScreen
import com.emfitsolutions.gopreach.ui.screens.enrollment.AdminEnrollmentScreen
import com.emfitsolutions.gopreach.ui.screens.enrollment.CongregationEnrollmentScreen
import com.emfitsolutions.gopreach.ui.screens.enrollment.CoordinatorElderEnrollmentScreen
import com.emfitsolutions.gopreach.ui.screens.enrollment.PublisherEnrollmentScreen
import com.emfitsolutions.gopreach.ui.screens.enrollment.RegularElderEnrollmentScreen
import com.emfitsolutions.gopreach.ui.screens.groups.ManageGroupsScreen
import com.emfitsolutions.gopreach.ui.screens.home.AdminHomeScreen
import com.emfitsolutions.gopreach.ui.screens.home.PublisherHomeScreen
import com.emfitsolutions.gopreach.ui.screens.interestedpeople.InterestedPeopleScreen
import com.emfitsolutions.gopreach.ui.screens.monthlyreport.MonthlyReportScreen
import com.emfitsolutions.gopreach.ui.screens.login.LoginScreen
import com.emfitsolutions.gopreach.ui.screens.publishers.ManagePublishersScreen
import com.emfitsolutions.gopreach.ui.screens.reports.ReportsScreen
import com.emfitsolutions.gopreach.ui.screens.schedules.ManageChatSchedulesScreen
import com.emfitsolutions.gopreach.ui.screens.settings.SettingsScreen
import com.emfitsolutions.gopreach.ui.screens.sharelocation.ShareLocationScreen
import com.emfitsolutions.gopreach.ui.screens.territories.ManageTerritoriesScreen
import com.emfitsolutions.gopreach.ui.screens.userlogs.UserLogsScreen
import com.emfitsolutions.gopreach.ui.screens.users.AddEditUserScreen
import com.emfitsolutions.gopreach.ui.screens.users.ManageUsersScreen
import com.emfitsolutions.gopreach.data.model.Permission
import com.emfitsolutions.gopreach.data.model.ScopeType

/**
 * Root navigation graph. Routing between Login / forced-password-change / the
 * Admin or Publisher home is reactive, driven off [SessionViewModel] (backed by
 * [com.emfitsolutions.gopreach.domain.UserSession]) rather than one-off nav calls,
 * so a session change from *any* source — sign-in, sign-out, a password change
 * completing, or Firebase restoring a persisted login on app start — always lands
 * on the right screen. Manually switching between Admin/Publisher context for a
 * person who holds both (spec §3, §5) is a plain nav call instead, since that
 * doesn't change the underlying session.
 */
@Composable
fun GoPreachNavGraph(
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val currentPersonId = session.person?.id.orEmpty()
    val currentRole = PermissionChecker.highestAdminRole(session.roleAssignments)
    // The congregation an Admin or Coordinator Elder is scoped to (spec §3 permission
    // matrix: "Own congregation" everywhere except Super-Admin's "All").
    val ownCongregationId = session.roleAssignments.firstOrNull {
        (it.resolvedRoleType() as? RoleType.Admin)?.role in setOf(AdminRole.ADMIN_PER_CONGREGATION, AdminRole.COORDINATOR_ELDER)
    }?.congregationId
    // A Regular Elder's own group (spec §3: their CRUD/view scope is "own group", not congregation-wide).
    val ownGroupAssignment = session.roleAssignments.firstOrNull {
        (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER
    }
    // A Publisher's own group/congregation (spec §6.1: publishers share within, and only see, their own group).
    val ownPublisherAssignment = session.roleAssignments.firstOrNull { it.resolvedRoleType() is RoleType.Publisher }

    val targetRoute = when {
        session.isLoading -> null
        !session.isSignedIn -> Destinations.LOGIN
        session.requiresPasswordChange -> Destinations.FORCED_PASSWORD_CHANGE
        session.availableContext == SessionContext.PUBLISHER -> Destinations.PUBLISHER_HOME
        else -> Destinations.ADMIN_HOME // ADMIN, BOTH (default landing), and NONE (shows an empty-state shell)
    }

    LaunchedEffect(targetRoute) {
        if (targetRoute != null && navController.currentDestination?.route != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Spec §9 — a Super-Admin deactivating/suspending someone mid-session must
    // actually end that session, not just block their *next* sign-in attempt.
    LaunchedEffect(session.isAccountBlocked) {
        if (session.isAccountBlocked) sessionViewModel.signOut()
    }

    // Circuit Overseer / custom users (spec §5-§8) carry no built-in scope —
    // everything they may see comes from their UserAccessGrant instead.
    val grant = session.grant
    val canManageUsers = currentRole == AdminRole.SUPER_ADMIN ||
        (grant?.resolvedPermissions?.contains(Permission.MANAGE_USERS) == true)

    NavHost(navController = navController, startDestination = Destinations.LOGIN) {
        composable(Destinations.LOGIN) {
            LoginScreen(
                onForgotPasswordClick = { navController.navigate(Destinations.FORGOT_PASSWORD) },
                onSignedIn = { /* routing handled reactively above */ },
            )
        }
        composable(Destinations.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.FORCED_PASSWORD_CHANGE) {
            ForcedPasswordChangeScreen(onCompleted = { /* routing handled reactively above */ })
        }
        composable(Destinations.ADMIN_HOME) {
            AdminHomeScreen(
                onSwitchToPublisher = if (session.availableContext == SessionContext.BOTH) {
                    { navController.navigate(Destinations.PUBLISHER_HOME) }
                } else null,
                canManageUsers = canManageUsers,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(Destinations.PUBLISHER_HOME) {
            PublisherHomeScreen(
                onSwitchToAdmin = if (session.availableContext == SessionContext.BOTH) {
                    { navController.navigate(Destinations.ADMIN_HOME) }
                } else null,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(Destinations.ENROLL_CONGREGATION) {
            CongregationEnrollmentScreen(
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Destinations.ENROLL_ADMIN) {
            AdminEnrollmentScreen(
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Destinations.ENROLL_COORDINATOR_ELDER) {
            CoordinatorElderEnrollmentScreen(
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Destinations.ENROLL_REGULAR_ELDER) {
            RegularElderEnrollmentScreen(
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Destinations.ENROLL_PUBLISHER) {
            PublisherEnrollmentScreen(
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Destinations.MANAGE_CONGREGATIONS) {
            ManageCongregationsScreen(
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onAddNew = { navController.navigate(Destinations.ENROLL_CONGREGATION) },
            )
        }
        composable(Destinations.MANAGE_ADMINS) {
            ManageAdminsScreen(
                onBack = { navController.popBackStack() },
                onAddNew = { navController.navigate(Destinations.ENROLL_ADMIN) },
            )
        }
        composable(Destinations.MANAGE_COORDINATOR_ELDERS) {
            ManageCoordinatorEldersScreen(
                fixedCongregationId = if (currentRole == AdminRole.SUPER_ADMIN) null else ownCongregationId,
                onBack = { navController.popBackStack() },
                onAddNew = { navController.navigate(Destinations.ENROLL_COORDINATOR_ELDER) },
            )
        }
        composable(Destinations.MANAGE_REGULAR_ELDERS) {
            ManageRegularEldersScreen(
                fixedCongregationId = if (currentRole == AdminRole.SUPER_ADMIN) null else (ownCongregationId ?: ownGroupAssignment?.congregationId),
                onBack = { navController.popBackStack() },
                onAddNew = { navController.navigate(Destinations.ENROLL_REGULAR_ELDER) },
            )
        }
        composable(Destinations.BACKUP_RESTORE) {
            BackupRestoreScreen(
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.USER_LOGS) {
            UserLogsScreen(
                visibleCongregationId = if (currentRole == AdminRole.SUPER_ADMIN) null else ownCongregationId,
                canDelete = currentRole == AdminRole.SUPER_ADMIN,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.MANAGE_PUBLISHERS) {
            ManagePublishersScreen(
                currentPersonId = currentPersonId,
                visibleCongregationId = if (currentRole == AdminRole.SUPER_ADMIN) null else ownCongregationId,
                onBack = { navController.popBackStack() },
                onAddNew = { navController.navigate(Destinations.ENROLL_PUBLISHER) },
            )
        }
        composable(Destinations.MANAGE_GROUPS) {
            ManageGroupsScreen(
                fixedCongregationId = ownCongregationId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.MANAGE_TERRITORIES) {
            ManageTerritoriesScreen(
                fixedCongregationId = ownCongregationId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.MANAGE_CHAT_SCHEDULES) {
            ManageChatSchedulesScreen(
                currentPersonId = currentPersonId,
                visibleCongregationId = if (currentRole == AdminRole.SUPER_ADMIN) null else (ownCongregationId ?: ownGroupAssignment?.congregationId),
                visibleGroupId = ownGroupAssignment?.groupId,
                canEdit = currentRole != null, // any Admin-track role reaching this screen may create/edit within their scope
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.REPORTS) {
            val canEditReports = currentRole == AdminRole.COORDINATOR_ELDER || currentRole == AdminRole.REGULAR_ELDER
            ReportsScreen(
                visibleCongregationId = if (currentRole == AdminRole.SUPER_ADMIN) null else (ownCongregationId ?: ownGroupAssignment?.congregationId),
                visibleGroupId = ownGroupAssignment?.groupId,
                canEditReports = canEditReports,
                onEditPublisher = { personId -> navController.navigate(Destinations.editMonthlyReport(personId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.DASHBOARD_REPORTS) {
            // Spec §6: this is the actual security boundary, not the screen —
            // Super-Admin sees every congregation (null); everyone else gets a
            // fixed set they cannot escape by any parameter this screen exposes.
            val visibleCongregationIds: Set<String>? = when {
                currentRole == AdminRole.SUPER_ADMIN -> null
                currentRole == AdminRole.CIRCUIT_OVERSEER ->
                    if (grant?.resolvedScopeType == ScopeType.ALL_CONGREGATIONS) null else grant?.scopeCongregationIds?.toSet().orEmpty()
                else -> setOfNotNull(ownCongregationId ?: ownGroupAssignment?.congregationId ?: ownPublisherAssignment?.congregationId)
            }
            DashboardReportsScreen(
                visibleCongregationIds = visibleCongregationIds,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Destinations.EDIT_MONTHLY_REPORT,
            arguments = listOf(navArgument("targetPersonId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val targetPersonId = backStackEntry.arguments?.getString("targetPersonId").orEmpty()
            MonthlyReportScreen(
                publisherPersonId = targetPersonId,
                allowEditWhenLocked = true,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.BIBLE_STUDY_RECORD) {
            BibleStudyScreen(
                publisherPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.INTERESTED_PEOPLE) {
            InterestedPeopleScreen(
                publisherPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.MONTHLY_REPORT) {
            MonthlyReportScreen(
                publisherPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SHARE_LOCATION) {
            // Spec §6.1 scoping: Super-Admin sees everyone; Admin/Coordinator Elder their
            // congregation; Regular Elder and Publisher their own group.
            val (visibleCongregationId, visibleGroupId) = when {
                currentRole == AdminRole.SUPER_ADMIN -> null to null
                currentRole == AdminRole.ADMIN_PER_CONGREGATION || currentRole == AdminRole.COORDINATOR_ELDER -> ownCongregationId to null
                currentRole == AdminRole.REGULAR_ELDER -> ownGroupAssignment?.congregationId to ownGroupAssignment?.groupId
                else -> ownPublisherAssignment?.congregationId to ownPublisherAssignment?.groupId
            }
            ShareLocationScreen(
                currentPersonId = currentPersonId,
                visibleCongregationId = visibleCongregationId,
                visibleGroupId = visibleGroupId,
                canShareOwnLocation = ownPublisherAssignment != null,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.CALENDAR) {
            val scope = when (currentRole) {
                AdminRole.SUPER_ADMIN -> CalendarScope.AdminTrack(congregationId = null, canEditAll = true)
                AdminRole.ADMIN_PER_CONGREGATION, AdminRole.COORDINATOR_ELDER ->
                    CalendarScope.AdminTrack(congregationId = ownCongregationId, canEditAll = true)
                AdminRole.REGULAR_ELDER -> CalendarScope.AdminTrack(
                    congregationId = ownGroupAssignment?.congregationId,
                    canEditAll = false,
                    editableGroupId = ownGroupAssignment?.groupId,
                )
                // A Circuit Overseer/custom user is always read-only here — the
                // Calendar screen only supports one-congregation-at-a-time
                // filtering today, so a multi-congregation grant sees every
                // congregation's events rather than an unsupported partial
                // filter (still no edit access either way).
                AdminRole.CIRCUIT_OVERSEER -> CalendarScope.AdminTrack(
                    congregationId = grant?.scopeCongregationIds?.singleOrNull(),
                    canEditAll = false,
                )
                null -> CalendarScope.Publisher(
                    congregationId = ownPublisherAssignment?.congregationId,
                    groupId = ownPublisherAssignment?.groupId,
                )
            }
            CalendarScreen(
                currentPersonId = currentPersonId,
                scope = scope,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.CONTROL_PANEL) {
            ControlPanelScreen(
                currentPersonId = currentPersonId,
                canManageLogo = PermissionChecker.highestAdminRole(session.roleAssignments) == AdminRole.SUPER_ADMIN,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.ACCOUNT_SETTINGS) {
            AccountSettingsScreen(
                onBack = { navController.popBackStack() },
                // A changed password signs the session out (spec §1); routing
                // back to Login happens reactively above, same as everywhere else.
                onSignedOutForPasswordChange = { },
            )
        }
        composable(Destinations.MANAGE_USERS) {
            ManageUsersScreen(
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onAddNew = { navController.navigate(Destinations.ADD_USER) },
                onEdit = { personId -> navController.navigate(Destinations.editUser(personId)) },
            )
        }
        composable(Destinations.ADD_USER) {
            AddEditUserScreen(
                targetPersonId = null,
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = Destinations.EDIT_USER,
            arguments = listOf(navArgument("targetPersonId") { type = NavType.StringType }),
        ) { backStackEntry ->
            AddEditUserScreen(
                targetPersonId = backStackEntry.arguments?.getString("targetPersonId"),
                currentPersonId = currentPersonId,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
    }
}
