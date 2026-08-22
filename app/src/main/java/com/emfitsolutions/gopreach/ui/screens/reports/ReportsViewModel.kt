package com.emfitsolutions.gopreach.ui.screens.reports

import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class PublisherReportRow(
    val person: Person,
    val totalBibleStudies: Int,
    val totalHours: Double,
    val totalInterestedPeople: Int,
)

/**
 * Spec §5.1 — "View publisher records/reports": Bible studies, interested
 * people, and preaching hours, per publisher / all publishers / per
 * congregation. This assembles the per-publisher rows; the screen sums them for
 * the "all publishers" / "per congregation" totals rather than duplicating the
 * aggregation two ways.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val monthlyReportRepository: MonthlyReportRepository,
    private val interestedPersonRepository: InterestedPersonRepository,
) : ViewModel() {

    /** [visibleGroupId] narrows further for a Regular Elder (own group only, spec
     * §3 permission matrix); leave null for congregation-wide or all-congregations roles. */
    fun rowsFor(visibleCongregationId: String?, visibleGroupId: String? = null): Flow<List<PublisherReportRow>> =
        combine(
            personRepository.observeAll(),
            roleAssignmentRepository.observeAll(),
            monthlyReportRepository.observeAll(),
            interestedPersonRepository.observeAll(),
        ) { people, assignments, reports, interestedPeople ->
            assignments
                .filter { it.resolvedRoleType() is RoleType.Publisher }
                .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
                .filter { visibleGroupId == null || it.groupId == visibleGroupId }
                .mapNotNull { assignment ->
                    val person = people.firstOrNull { it.id == assignment.personId } ?: return@mapNotNull null
                    val personReports = reports.filter { it.publisherPersonId == person.id }
                    PublisherReportRow(
                        person = person,
                        totalBibleStudies = personReports.sumOf { it.bibleStudiesCount },
                        totalHours = personReports.sumOf { it.hoursRendered ?: 0.0 },
                        totalInterestedPeople = interestedPeople.count { it.publisherPersonId == person.id },
                    )
                }
                .sortedBy { it.person.fullName }
        }
}
