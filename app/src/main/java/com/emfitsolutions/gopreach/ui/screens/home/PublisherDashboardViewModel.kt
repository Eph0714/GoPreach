package com.emfitsolutions.gopreach.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.MonthlyReport
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.ReportStatus
import com.emfitsolutions.gopreach.data.repository.BibleStudyRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PreachingTimeRecordRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import com.emfitsolutions.gopreach.domain.DateRangeStore
import com.emfitsolutions.gopreach.ui.components.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

fun isPioneerCategory(category: PublisherCategory?): Boolean =
    category == PublisherCategory.REGULAR_PIONEER || category == PublisherCategory.AUXILIARY_PIONEER

/** "Complete Publisher Dashboard" spec §1/§21 — everything the square stat
 * cards need, computed the same way (unique-person counts, summed hours,
 * date-range-scoped) regardless of which cards a given [PublisherCategory]
 * actually shows. */
data class PublisherDashboardStats(
    /** Spec §7/§18 — COUNT DISTINCT Bible Study person, not visit rows. Every
     * [com.emfitsolutions.gopreach.data.model.BibleStudyRecord] already *is*
     * one distinct person (there's no separate per-visit log for Bible
     * Studies in this app's data model, unlike Return Visits below), so this
     * is simply how many such records fall in the selected range — already
     * correct by construction, not something to de-duplicate further. */
    val bibleStudiesCount: Int = 0,
    /** Spec §8/§10 — COUNT DISTINCT Return Visit person: every
     * [com.emfitsolutions.gopreach.data.model.Visit] in range, grouped by
     * its owning Interested Person, counted once each no matter how many
     * separate visits that person had. */
    val returnVisitsCount: Int = 0,
    /** Spec §11/§13 — SUM(Hour Consumed) from
     * [com.emfitsolutions.gopreach.data.model.PreachingTimeRecord] in range. */
    val preachingHours: Double = 0.0,
    /** Spec §19 — true if at least one SUBMITTED MonthlyReport overlapping
     * the range has `participatedInPreaching == true`; never hard-coded. */
    val attendedPreaching: Boolean = false,
)

/**
 * Backs the role-based square stat cards on [PublisherHomeScreen] (spec
 * §1-§32). Shares [DateRangeStore] with the rest of the app (spec §9: "the
 * existing GoPreach date-range system") so picking a range here and on the
 * Admin/Coordinator Dashboard don't silently disagree for a dual-role
 * account.
 */
@HiltViewModel
class PublisherDashboardViewModel @Inject constructor(
    private val bibleStudyRepository: BibleStudyRepository,
    private val visitRepository: VisitRepository,
    private val preachingTimeRecordRepository: PreachingTimeRecordRepository,
    private val monthlyReportRepository: MonthlyReportRepository,
    private val dateRangeStore: DateRangeStore,
) : ViewModel() {

    val dateRange: StateFlow<DateRange> = dateRangeStore.range
    fun setDateRange(range: DateRange) = dateRangeStore.set(range)

    /** The collection-group listener for this Publisher's own Return Visits
     * (see [VisitRepository.startRemoteSyncForPublisher]) — started once the
     * screen knows its own personId, same on-demand pattern as
     * [com.emfitsolutions.gopreach.ui.screens.interestedpeople
     * .InterestedPeopleViewModel.startVisitSync]. */
    fun startVisitSync(publisherPersonId: String): Flow<Unit> = visitRepository.startRemoteSyncForPublisher(publisherPersonId)

    fun statsFor(publisherPersonId: String): StateFlow<PublisherDashboardStats> = combine(
        bibleStudyRepository.observeForPublisher(publisherPersonId),
        visitRepository.observeAllForPublisher(publisherPersonId),
        preachingTimeRecordRepository.observeForPublisher(publisherPersonId),
        monthlyReportRepository.observeAll(),
        dateRangeStore.range,
    ) { bibleStudies, visits, preachingRecords, allReports, range ->
        val visitsInRange = visits.filter { range.contains(it.visitDate) }
        val reportsInRange = allReports.filter {
            it.publisherPersonId == publisherPersonId && it.status == ReportStatus.SUBMITTED && range.overlapsMonth(it.periodMonth)
        }
        PublisherDashboardStats(
            bibleStudiesCount = bibleStudies.count { range.contains(it.createdAt) },
            returnVisitsCount = visitsInRange.distinctBy { it.interestedPersonId }.size,
            preachingHours = preachingRecords
                .filter { it.status == com.emfitsolutions.gopreach.data.model.RecordStatus.ACTIVE && range.contains(it.date) }
                .sumOf { it.hoursConsumed },
            attendedPreaching = reportsInRange.any { it.participatedInPreaching == true },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PublisherDashboardStats())
}
