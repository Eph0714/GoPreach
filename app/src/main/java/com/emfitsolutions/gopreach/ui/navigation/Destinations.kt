package com.emfitsolutions.gopreach.ui.navigation

/**
 * All navigation routes in the app. Grouped by the phase that introduces them
 * (see BUILD_PLAN.md). Auth/role-based routing (Phase 2) will decide which of
 * the Admin-context or Publisher-context graphs a session lands on after login.
 */
object Destinations {
    // Phase 0
    const val LOGIN = "login"
    const val ABOUT = "about"

    // Phase 2
    const val FORCED_PASSWORD_CHANGE = "forced_password_change"
    const val FORGOT_PASSWORD = "forgot_password"

    // Role-based landing points (real dashboards land in Phase 3-5; placeholders for now)
    const val ADMIN_HOME = "admin_home"
    const val PUBLISHER_HOME = "publisher_home"

    // Phase 2 — enrollment (spec §4)
    const val ENROLL_CONGREGATION = "enroll_congregation"
    const val ENROLL_ADMIN = "enroll_admin"
    const val ENROLL_COORDINATOR_ELDER = "enroll_coordinator_elder"
    const val ENROLL_REGULAR_ELDER = "enroll_regular_elder"
    const val ENROLL_PUBLISHER = "enroll_publisher"
    const val MANAGE_COORDINATOR_ELDERS = "manage_coordinator_elders"
    const val MANAGE_REGULAR_ELDERS = "manage_regular_elders"

    // Phase 3
    const val CONTROL_PANEL = "control_panel"
    const val MANAGE_CONGREGATIONS = "manage_congregations"
    const val MANAGE_ADMINS = "manage_admins"
    const val BACKUP_RESTORE = "backup_restore"
    const val USER_LOGS = "user_logs"

    // Phase 4
    const val MANAGE_PUBLISHERS = "manage_publishers"
    const val MANAGE_GROUPS = "manage_groups"
    const val MANAGE_TERRITORIES = "manage_territories"
    const val MANAGE_CHAT_SCHEDULES = "manage_chat_schedules"
    const val REPORTS = "reports"

    // Phase 5 — Ministry Report App (Publisher context)
    const val BIBLE_STUDY_RECORD = "bible_study_record"
    const val INTERESTED_PEOPLE = "interested_people"
    const val MONTHLY_REPORT = "monthly_report"
    const val EDIT_MONTHLY_REPORT = "edit_monthly_report/{targetPersonId}"
    fun editMonthlyReport(targetPersonId: String) = "edit_monthly_report/$targetPersonId"

    // Phase 6
    const val SHARE_LOCATION = "share_location"
    const val CALENDAR = "calendar"

    const val SETTINGS = "settings"

    // User Access Management (Super-Admin account editing + Circuit Overseer/custom users)
    const val ACCOUNT_SETTINGS = "account_settings"
    const val MANAGE_USERS = "manage_users"
    const val ADD_USER = "add_user"
    const val EDIT_USER = "edit_user/{targetPersonId}"
    fun editUser(targetPersonId: String) = "edit_user/$targetPersonId"

    // Role-Based Dashboard, Side Panel & Graphical Reports
    const val DASHBOARD_REPORTS = "dashboard_reports"
}
