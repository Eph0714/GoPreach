package com.emfitsolutions.gopreach.ui.screens.dashboard

import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.MonthlyReport
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType

/** One named person behind a KPI card's headline number — spec: tapping "Total
 * Elders" shows e.g. "Henry Canales (Solano Tagalog Congregation)" for each
 * elder that number counts, not just the number itself. [statLabels] is every
 * [DashboardStatsUiState]-facing card label this one person counts toward
 * (e.g. a Regular Pioneer counts toward both "Total Publishers" and "Regular
 * Pioneers") — computed once from the same de-duplicated-by-person data
 * [CongregationStats.compute] uses, so the list a card's dialog shows always
 * matches the number on the card exactly (same source, same dedup, no risk of
 * the two silently drifting apart). */
data class StatMember(
    val fullName: String,
    val congregationId: String,
    val congregationName: String,
    val statLabels: Set<String>,
)

/** Companion to [CongregationStats.compute] — same scoped/deduplicated
 * ACTIVE-assignment data, but resolved into named people instead of counts. */
fun computeStatMembers(
    congregations: List<Congregation>,
    assignments: List<RoleAssignment>,
    people: List<Person>,
): List<StatMember> {
    val peopleById = people.associateBy { it.id }
    val congregationsById = congregations.associateBy { it.id }
    return assignments
        .filter { it.status == RoleAssignmentStatus.ACTIVE && it.congregationId in congregationsById }
        .distinctBy { it.personId to it.congregationId }
        .mapNotNull { assignment ->
            val person = peopleById[assignment.personId] ?: return@mapNotNull null
            val congregation = congregationsById[assignment.congregationId] ?: return@mapNotNull null
            val labels: Set<String> = when (val role = assignment.resolvedRoleType()) {
                is RoleType.Admin -> when (role.role) {
                    // "Total Elders" counts Regular Elders only (per explicit
                    // request) — Coordinator Elder is an administrative role,
                    // not counted here even though it's also an Elder title.
                    AdminRole.REGULAR_ELDER -> setOf("Total Elders")
                    else -> emptySet()
                }
                is RoleType.Publisher -> buildSet {
                    if (role.category != PublisherCategory.REMOVED_PUBLISHER) add("Total Publishers")
                    when (role.category) {
                        PublisherCategory.REGULAR_PIONEER -> add("Regular Pioneers")
                        PublisherCategory.AUXILIARY_PIONEER -> add("Auxiliary Pioneers")
                        PublisherCategory.UNBAPTIZED_PUBLISHER -> add("Unbaptized Publishers")
                        PublisherCategory.INACTIVE_PUBLISHER -> add("Inactive Publishers")
                        PublisherCategory.REMOVED_PUBLISHER -> add("Removed Publishers")
                        PublisherCategory.REGULAR_PUBLISHER -> Unit
                    }
                }
            }
            if (labels.isEmpty()) return@mapNotNull null
            StatMember(
                fullName = person.fullName,
                congregationId = congregation.id,
                congregationName = congregation.name,
                statLabels = labels,
            )
        }
}

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
    /** Regular Elders only (per explicit request) — Coordinator Elder and
     * Admin Per Congregation are both administrative roles, not counted as
     * an "Elder" here even though Coordinator Elder is also an Elder title. */
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
            // distinctBy personId — a RoleAssignment is one document per role,
            // not one per person, so if the same person ever ends up with more
            // than one ACTIVE assignment matching the same bucket (a duplicate
            // enrollment, or a promotion flow that added a new assignment
            // instead of converting the old one), counting assignments directly
            // over-counts real, distinct people. This was a real, confirmed bug
            // (reported: "3 elders shown, only 2 actually enrolled").
            val publisherAssignments = active.filter { it.resolvedRoleType() is RoleType.Publisher }
                .distinctBy { it.personId }
            fun countOf(category: PublisherCategory) = publisherAssignments.count {
                (it.resolvedRoleType() as RoleType.Publisher).category == category
            }
            // Regular Elders only — Coordinator Elder is deliberately excluded
            // from "Total Elders" (per explicit request), even though it's
            // also an Elder title; it's an administrative role here.
            val elderCount = active.filter {
                (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER
            }.distinctBy { it.personId }.size
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

        /** The "All Congregations" KPI row (spec §3) for a Super-Admin viewing
         * everything at once — recomputed from the raw, global (already
         * congregation-scoped by the caller) [assignments]/[reports] lists, via
         * the exact same personId-dedup [compute] uses, rather than by summing
         * the already-computed per-congregation [CongregationStats.totalElders]
         * /publisher-category numbers. Summing was a real, confirmed bug: each
         * per-congregation count is already correctly deduped *within* that one
         * congregation, but a person holding an ACTIVE elder/publisher
         * assignment in **two different** congregations at once (still an edge
         * case worth fixing, not the common case) was then counted once per
         * congregation when those per-congregation totals were added together —
         * reported as "3 elders shown, only 2 enrolled" persisting even after
         * the per-congregation fix. Recomputing from scratch, globally, is the
         * only way to dedupe across congregation boundaries too. */
        fun total(
            congregations: List<Congregation>,
            assignments: List<RoleAssignment>,
            reports: List<MonthlyReport>,
        ): CongregationStats {
            val congregationIds = congregations.map { it.id }.toSet()
            val active = assignments.filter { it.status == RoleAssignmentStatus.ACTIVE && it.congregationId in congregationIds }
            val publisherAssignments = active.filter { it.resolvedRoleType() is RoleType.Publisher }
                .distinctBy { it.personId }
            fun countOf(category: PublisherCategory) = publisherAssignments.count {
                (it.resolvedRoleType() as RoleType.Publisher).category == category
            }
            // Regular Elders only — Coordinator Elder is deliberately excluded
            // from "Total Elders" (per explicit request), even though it's
            // also an Elder title; it's an administrative role here.
            val elderCount = active.filter {
                (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER
            }.distinctBy { it.personId }.size
            val scopedReports = reports.filter { it.congregationId in congregationIds }
            return CongregationStats(
                congregationId = "",
                congregationName = "All Congregations",
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
                totalBibleStudies = scopedReports.sumOf { it.bibleStudiesCount },
                regularPioneerHours = scopedReports.filter { it.category == PublisherCategory.REGULAR_PIONEER }.sumOf { it.hoursRendered ?: 0.0 },
                auxiliaryPioneerHours = scopedReports.filter { it.category == PublisherCategory.AUXILIARY_PIONEER }.sumOf { it.hoursRendered ?: 0.0 },
            )
        }
    }
}
