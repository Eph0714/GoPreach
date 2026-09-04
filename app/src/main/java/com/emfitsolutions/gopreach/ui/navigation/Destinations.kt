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
    // New Service Overseer role.
    const val ENROLL_SERVICE_OVERSEER = "enroll_service_overseer"
    const val MANAGE_SERVICE_OVERSEERS = "manage_service_overseers"
    const val CONSOLIDATED_REPORT = "consolidated_report"
    // "MINISTERIAL ACCOUNT" — multiple per congregation allowed.
    const val ENROLL_MINISTERIAL_SERVANT = "enroll_ministerial_servant"
    const val MANAGE_MINISTERIAL_SERVANTS = "manage_ministerial_servants"
    // "Manage Publisher Report" module.
    const val MANAGE_PUBLISHER_REPORTS = "manage_publisher_reports"
    // "Announcement Module".
    const val MANAGE_ANNOUNCEMENTS = "manage_announcements"
    const val PUBLISHER_ANNOUNCEMENTS = "publisher_announcements"
    // "Meeting Assignments" module — Midweek Meeting Schedule + Public Talk
    // and Watchtower Study Schedule. One screen/route for both the admin-
    // track enrollment side and the Publisher's own read-only view (see
    // `readOnly` at the call site), same convention as Announcements above.
    const val MEETING_ASSIGNMENTS = "meeting_assignments"
    const val PUBLISHER_MEETING_ASSIGNMENTS = "publisher_meeting_assignments"

    // Phase 3
    const val CONTROL_PANEL = "control_panel"
    const val MANAGE_CONGREGATIONS = "manage_congregations"
    const val MANAGE_ADMINS = "manage_admins"
    const val BACKUP_RESTORE = "backup_restore"
    const val USER_LOGS = "user_logs"

    // Phase 4
    const val MANAGE_PUBLISHERS = "manage_publishers"
    const val MANAGE_GROUPS = "manage_groups"
    // "Clicking coordinates should open the Territory Map centered on the
    // Publisher's latest location" — [MANAGE_TERRITORIES] (with the literal
    // `{focusLat}`-style placeholders) is the route *pattern*, used only to
    // register the composable; every plain "open Territory Map" entry point
    // (side panel, dashboard tiles) navigates to [MANAGE_TERRITORIES_BASE]
    // instead — the three query args are optional, so the base path alone
    // still matches the pattern and simply omits them. Only Share
    // Location's own "open in Territory Map" action supplies real values,
    // via [territoryMapFocusedOn].
    const val MANAGE_TERRITORIES_BASE = "manage_territories"
    const val MANAGE_TERRITORIES = "$MANAGE_TERRITORIES_BASE?focusLat={focusLat}&focusLng={focusLng}&focusName={focusName}"
    fun territoryMapFocusedOn(lat: Double, lng: Double, name: String): String {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        return "$MANAGE_TERRITORIES_BASE?focusLat=$lat&focusLng=$lng&focusName=$encodedName"
    }
    const val MANAGE_CHAT_SCHEDULES = "manage_chat_schedules"
    const val REPORTS = "reports"

    // Phase 5 — Ministry Report App (Publisher context)
    // "Preaching Time Record Module" spec §12 — Pioneer-only.
    const val PREACHING_TIME_RECORD = "preaching_time_record"
    // "My Bible Text Record" module — every Publisher's personal Bible-
    // reference organizer.
    const val MY_BIBLE_TEXT_RECORD = "my_bible_text_record"
    const val MONTHLY_REPORT = "monthly_report"
    const val EDIT_MONTHLY_REPORT = "edit_monthly_report/{targetPersonId}"
    fun editMonthlyReport(targetPersonId: String) = "edit_monthly_report/$targetPersonId"
    // "Allow the publisher to see all his submitted Report record" — a
    // read-only history, separate from MONTHLY_REPORT's current/previous-
    // month editing form.
    const val MY_SUBMITTED_REPORTS = "my_submitted_reports"

    // "Redesign the Publisher Dashboard" — Searching → Return Visit → Bible
    // Study pipeline (see PipelineStage). Replaces the old separate
    // INTERESTED_PEOPLE/BIBLE_STUDY_RECORD destinations: all three stages are
    // now one PipelineScreen parameterized by which stage it shows.
    const val SEARCHING = "searching"
    const val RETURN_VISIT = "return_visit"
    const val BIBLE_STUDY = "bible_study"
    // Service Overseer's incoming "Forward to Other Congregation" review queue.
    const val FORWARD_REQUESTS = "forward_requests"
    // A Publisher's own incoming "FORWARD TO OTHER PUBLISHER" review queue.
    const val PUBLISHER_FORWARD_REQUESTS = "publisher_forward_requests"

    // Phase 6
    const val SHARE_LOCATION = "share_location"
    const val CALENDAR = "calendar"
    // "Find Location" — manual-GPS-entry route finder (walking/driving/
    // bicycling/transit), Publisher-context.
    const val FIND_LOCATION = "find_location"

    const val SETTINGS = "settings"

    // User Access Management (Super-Admin account editing + Circuit Overseer/custom users)
    const val ACCOUNT_SETTINGS = "account_settings"
    const val MANAGE_USERS = "manage_users"
    const val ADD_USER = "add_user"
    const val EDIT_USER = "edit_user/{targetPersonId}"
    fun editUser(targetPersonId: String) = "edit_user/$targetPersonId"

    // Role-Based Dashboard, Side Panel & Graphical Reports
    const val DASHBOARD_REPORTS = "dashboard_reports"

    // "Contact Record" module — consolidated Publisher/Interested People
    // (Searching/Return Visit/Bible Study)/Coordinator Elder/Service
    // Overseer/Ministerial Servant directory. Super-Admin/Coordinator
    // Elder/Regular Elder only.
    const val CONTACT_RECORD = "contact_record"
}
