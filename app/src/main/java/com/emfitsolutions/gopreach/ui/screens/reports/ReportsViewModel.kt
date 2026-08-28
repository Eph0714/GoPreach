package com.emfitsolutions.gopreach.ui.screens.reports

import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.domain.DateRangeStore
import com.emfitsolutions.gopreach.ui.components.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class PublisherReportRow(
    val person: Person,
    val category: PublisherCategory,
    val totalBibleStudies: Int,
    val totalHours: Double,
    val totalInterestedPeople: Int,
    /** [com.emfitsolutions.gopreach.data.model.MonthlyReport
     * .participatedInPreaching] — null for Pioneer categories, since that
     * field is never used for them (see [MonthlyReport]'s own table); the
     * screen renders that as "N/A", true/false as "Yes"/"No". When more than
     * one report falls in the selected range, true wins over false (any
     * month attended counts as "attended this period"), and false wins over
     * a report that left the field unset. */
    val attendedPreaching: Boolean?,
)

/** One Group's worth of the Publisher Report — "Group the record by Group"
 * (example: "Group: 5 / Group Overseer: ... / Group Servant: ... / Group
 * Assistant: ...", one publisher table, then a per-group summary total).
 * [groupId] null is the "Unassigned" bucket for a Publisher with no Group
 * set — everyone still shows up somewhere rather than silently vanishing
 * from the report. */
data class GroupReportSection(
    val groupId: String?,
    val groupName: String,
    val overseerName: String?,
    val servantName: String?,
    val assistantName: String?,
    val rows: List<PublisherReportRow>,
) {
    /** "SUMMARY TOTAL FOR THE MONTH OF ..." — a publisher-count per category
     * (a map, not one field per category, so every [PublisherCategory] this
     * group actually holds is covered without hard-coding the four the
     * example happens to show). */
    val categoryCounts: Map<PublisherCategory, Int> get() = rows.groupingBy { it.category }.eachCount()

    /** "TOTAL HOURS FOR REGULAR PIONEER / AUXILIARY PIONEER" — only Pioneer
     * categories carry hours at all (see [MonthlyReport]'s own table); a
     * category with no rows or all-zero hours is simply absent from this
     * map rather than shown as a spurious 0. */
    val categoryHours: Map<PublisherCategory, Double> get() = rows
        .filter { it.category == PublisherCategory.REGULAR_PIONEER || it.category == PublisherCategory.AUXILIARY_PIONEER }
        .groupBy { it.category }
        .mapValues { (_, groupRows) -> groupRows.sumOf { it.totalHours } }
}

/**
 * Spec §5.1 — "View publisher records/reports": Bible studies, interested
 * people, and preaching hours, per publisher / per Group / per congregation.
 * "Group the record by Group" — [groupedRowsFor] is the shape the screen
 * actually renders; the flat, ungrouped [rowsFor] stays for the "All
 * Publishers" summary-total row at the very top of the screen.
 *
 * "Main Form Date Range Filtering" spec §7 — [dateRange] scopes both figures
 * that actually have a point-in-time dimension: [MonthlyReport.periodMonth]
 * (via [DateRange.overlapsMonth]) and [InterestedPerson.createdAt] (a real
 * timestamp, checked directly). `null` means "no filter" (all-time), used by
 * callers that haven't opted into date scoping.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val monthlyReportRepository: MonthlyReportRepository,
    private val interestedPersonRepository: InterestedPersonRepository,
    private val groupRepository: GroupRepository,
    private val dateRangeStore: DateRangeStore,
) : ViewModel() {

    /** Spec §8 — the same selected [DateRange] the Dashboard's Reports screen
     * reads/writes (see [DateRangeStore]), so switching between the two never
     * silently resets back to a default the user didn't choose. */
    val dateRange: StateFlow<DateRange> = dateRangeStore.range
    fun setDateRange(range: DateRange) = dateRangeStore.set(range)

    /** [visibleGroupId] narrows further for a Regular Elder (own group only, spec
     * §3 permission matrix); leave null for congregation-wide or all-congregations roles. */
    fun rowsFor(
        visibleCongregationId: String?,
        visibleGroupId: String? = null,
        dateRange: DateRange? = null,
    ): Flow<List<PublisherReportRow>> =
        combine(
            personRepository.observeAll(),
            roleAssignmentRepository.observeAll(),
            monthlyReportRepository.observeAll(),
            interestedPersonRepository.observeAll(),
        ) { people, assignments, reports, interestedPeople ->
            buildRows(people, assignments, reports, interestedPeople, visibleCongregationId, visibleGroupId, dateRange)
                .sortedBy { it.person.fullName }
        }

    /** "Group the record by Group" — same rows [rowsFor] produces, bucketed
     * by [com.emfitsolutions.gopreach.data.model.RoleAssignment.groupId] and
     * paired with that Group's own Overseer/Servant/Assistant names for the
     * section header the example shows. Sections are sorted by Group name,
     * with "Unassigned" (no Group set) always last. */
    fun groupedRowsFor(
        visibleCongregationId: String?,
        visibleGroupId: String? = null,
        dateRange: DateRange? = null,
    ): Flow<List<GroupReportSection>> =
        combine(
            personRepository.observeAll(),
            roleAssignmentRepository.observeAll(),
            monthlyReportRepository.observeAll(),
            interestedPersonRepository.observeAll(),
            groupRepository.observeAll(),
        ) { people, assignments, reports, interestedPeople, groups ->
            val peopleById = people.associateBy { it.id }
            // Filtered to the Publisher-role assignment specifically — a
            // person can simultaneously hold a non-Publisher RoleAssignment
            // (e.g. Coordinator Elder), and a plain associateBy over every
            // assignment they hold would risk keying off whichever one
            // happened to iterate last, silently grouping this row under the
            // wrong (or no) Group.
            val assignmentByPerson = assignments
                .filter { it.resolvedRoleTypeOrNull() is RoleType.Publisher }
                .associateBy { it.personId }
            val rows = buildRows(people, assignments, reports, interestedPeople, visibleCongregationId, visibleGroupId, dateRange)
            val groupsById = groups.associateBy { it.id }
            val visibleGroups = groups.filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
                .filter { visibleGroupId == null || it.id == visibleGroupId }

            val rowsByGroupId = rows.groupBy { assignmentByPerson[it.person.id]?.groupId }
            val assignedSections = visibleGroups
                .filter { rowsByGroupId.containsKey(it.id) }
                .map { group ->
                    GroupReportSection(
                        groupId = group.id,
                        groupName = group.name,
                        overseerName = group.overseerPersonId?.let { peopleById[it]?.fullName },
                        servantName = group.servantPersonId?.let { peopleById[it]?.fullName },
                        assistantName = group.assistantPersonId?.let { peopleById[it]?.fullName },
                        rows = rowsByGroupId[group.id].orEmpty().sortedBy { it.person.fullName },
                    )
                }
                .sortedBy { it.groupName }

            // A Publisher whose RoleAssignment.groupId points at a Group this
            // caller isn't scoped to see (or that no longer exists) still
            // needs to land somewhere — "Unassigned" is also where a
            // genuinely groupless Publisher (groupId == null) ends up.
            val accountedGroupIds = assignedSections.mapNotNull { it.groupId }.toSet()
            val unassignedRows = rowsByGroupId.entries
                .filter { (groupId, _) -> groupId == null || groupId !in groupsById || groupId !in accountedGroupIds }
                .flatMap { it.value }
                .sortedBy { it.person.fullName }

            if (unassignedRows.isEmpty()) {
                assignedSections
            } else {
                assignedSections + GroupReportSection(
                    groupId = null,
                    groupName = "Unassigned",
                    overseerName = null,
                    servantName = null,
                    assistantName = null,
                    rows = unassignedRows,
                )
            }
        }

    private fun buildRows(
        people: List<Person>,
        assignments: List<com.emfitsolutions.gopreach.data.model.RoleAssignment>,
        reports: List<com.emfitsolutions.gopreach.data.model.MonthlyReport>,
        interestedPeople: List<com.emfitsolutions.gopreach.data.model.InterestedPerson>,
        visibleCongregationId: String?,
        visibleGroupId: String?,
        dateRange: DateRange?,
    ): List<PublisherReportRow> = assignments
        .filter { it.resolvedRoleTypeOrNull() is RoleType.Publisher }
        .filter { (it.resolvedRoleTypeOrNull() as RoleType.Publisher).category != PublisherCategory.REMOVED_PUBLISHER }
        .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
        .filter { visibleGroupId == null || it.groupId == visibleGroupId }
        .mapNotNull { assignment ->
            val person = people.firstOrNull { it.id == assignment.personId } ?: return@mapNotNull null
            val category = (assignment.resolvedRoleTypeOrNull() as RoleType.Publisher).category
            val personReports = reports.filter { it.publisherPersonId == person.id }
                .filter { dateRange == null || dateRange.overlapsMonth(it.periodMonth) }
            val personInterested = interestedPeople.filter { it.publisherPersonId == person.id }
                .filter { dateRange == null || it.createdAt in dateRange }
            val attendedPreaching = personReports.mapNotNull { it.participatedInPreaching }
                .let { flags -> if (flags.isEmpty()) null else flags.any { it } }
            PublisherReportRow(
                person = person,
                category = category,
                totalBibleStudies = personReports.sumOf { it.bibleStudiesCount },
                totalHours = personReports.sumOf { it.hoursRendered ?: 0.0 },
                totalInterestedPeople = personInterested.size,
                attendedPreaching = attendedPreaching,
            )
        }
}
