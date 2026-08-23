package com.emfitsolutions.gopreach.ui.screens.dashboard

import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.MonthlyReport
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType

/**
 * One congregation's worth of the "Role-Based Dashboard... Graphical Reports"
 * spec's KPI list — computed live from current [RoleAssignment]/[MonthlyReport]
 * records (spec §15: "Calculate dashboard statistics directly from current
 * database records... update whenever records change"), never a cached/stale
 * summary field. Since every source Flow this is built from
 * (Congregation/RoleAssignment/MonthlyReport repositories) is already backed
 * by a live Firestore listener into the offline Room cache, recomputing this
 * on every emission is what "update whenever records change" means in
 * practice here — there's no separate write-time aggregation step to keep in
 * sync.
 */
data class CongregationStats(
    val congregationId: String,
    val congregationName: String,
    /** All active, non-removed publisher-track people (every [PublisherCategory]
     * except [PublisherCategory.REMOVED_PUBLISHER]) — the dashboard's own
     * "Total Publishers" KPI, distinct from [regularPublishers] below (spec §7
     * lists both "Total Publishers" and each category separately). */
    val totalPublishers: Int,
    /** Coordinator Elders + Regular Elders — Admin Per Congregation is an
     * administrative role, not counted as an "Elder" here. */
    val totalElders: Int,
    val regularPioneers: Int,
    val auxiliaryPioneers: Int,
    val regularPublishers: Int,
    val unbaptizedPublishers: Int,
    val inactivePublishers: Int,
    val removedPublishers: Int,
    val totalBibleStudies: Int,
    val regularPioneerHours: Double,
    val auxiliaryPioneerHours: Double,
) {
    companion object {
        /** One [CongregationStats] per [congregations] entry, computed from the
         * full (unfiltered) [assignments]/[reports] lists — callers scope down
         * to "my congregation only" by filtering the *input* [congregations]
         * list first (see [DashboardStatsViewModel]), which is also exactly
         * where the role/scope security boundary belongs (spec §6: "prevent
         * cross-congregation access"). */
        fun compute(
            congregations: List<Congregation>,
            assignments: List<RoleAssignment>,
            reports: List<MonthlyReport>,
        ): List<CongregationStats> = congregations.map { congregation ->
            val active = assignments.filter { it.status == RoleAssignmentStatus.ACTIVE && it.congregationId == congregation.id }
            val publisherAssignments = active.filter { it.resolvedRoleType() is RoleType.Publisher }
            fun countOf(category: PublisherCategory) = publisherAssignments.count {
                (it.resolvedRoleType() as RoleType.Publisher).category == category
            }
            val elderCount = active.count {
                val role = (it.resolvedRoleType() as? RoleType.Admin)?.role
                role == AdminRole.COORDINATOR_ELDER || role == AdminRole.REGULAR_ELDER
            }
            val congregationReports = reports.filter { it.congregationId == congregation.id }
            CongregationStats(
                congregationId = congregation.id,
                congregationName = congregation.name,
                totalPublishers = publisherAssignments.count {
                    (it.resolvedRoleType() as RoleType.Publisher).category != PublisherCategory.REMOVED_PUBLISHER
                },
                totalElders = elderCount,
                regularPioneers = countOf(PublisherCategory.REGULAR_PIONEER),
                auxiliaryPioneers = countOf(PublisherCategory.AUXILIARY_PIONEER),
                regularPublishers = countOf(PublisherCategory.REGULAR_PUBLISHER),
                unbaptizedPublishers = countOf(PublisherCategory.UNBAPTIZED_PUBLISHER),
                inactivePublishers = countOf(PublisherCategory.INACTIVE_PUBLISHER),
                removedPublishers = countOf(PublisherCategory.REMOVED_PUBLISHER),
                totalBibleStudies = congregationReports.sumOf { it.bibleStudiesCount },
                regularPioneerHours = congregationReports.filter { it.category == PublisherCategory.REGULAR_PIONEER }.sumOf { it.hoursRendered ?: 0.0 },
                auxiliaryPioneerHours = congregationReports.filter { it.category == PublisherCategory.AUXILIARY_PIONEER }.sumOf { it.hoursRendered ?: 0.0 },
            )
        }

        /** Sums every field across [all] — the "global totals" KPI row (spec §3)
         * for a Super-Admin viewing every congregation at once. */
        fun total(all: List<CongregationStats>): CongregationStats = CongregationStats(
            congregationId = "",
            congregationName = "All Congregations",
            totalPublishers = all.sumOf { it.totalPublishers },
            totalElders = all.sumOf { it.totalElders },
            regularPioneers = all.sumOf { it.regularPioneers },
            auxiliaryPioneers = all.sumOf { it.auxiliaryPioneers },
            regularPublishers = all.sumOf { it.regularPublishers },
            unbaptizedPublishers = all.sumOf { it.unbaptizedPublishers },
            inactivePublishers = all.sumOf { it.inactivePublishers },
            removedPublishers = all.sumOf { it.removedPublishers },
            totalBibleStudies = all.sumOf { it.totalBibleStudies },
            regularPioneerHours = all.sumOf { it.regularPioneerHours },
            auxiliaryPioneerHours = all.sumOf { it.auxiliaryPioneerHours },
        )
    }
}
