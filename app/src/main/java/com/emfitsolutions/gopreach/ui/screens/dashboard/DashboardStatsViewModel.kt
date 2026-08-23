package com.emfitsolutions.gopreach.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.ui.components.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DashboardStatsUiState(
    val all: List<CongregationStats> = emptyList(),
    /** The "All Congregations" row — recomputed globally (see
     * [CongregationStats.total]'s doc comment), **not** derived by summing
     * [all]'s already-per-congregation-deduped numbers, since that silently
     * double-counted anyone holding an active elder/publisher assignment in
     * more than one congregation at once. */
    val overallTotal: CongregationStats? = null,
    val members: List<StatMember> = emptyList(),
    val selectedCongregationId: String? = null,
    /** "Main Form Date Range Filtering" spec §2-§9 — scopes [MonthlyReport]-
     * derived figures (Bible Studies, Preaching Hours) to the selected
     * period; defaults to This Month on first load (spec §4), calculated
     * live from the current date, never hard-coded. Publisher/Elder *counts*
     * are current-membership snapshots with no historical "as of" dimension
     * anywhere in this app's data model (a [RoleAssignment] only ever
     * reflects "assigned now", not "assigned during period X") — applying a
     * date range to those specific numbers would be fabricating precision
     * this app doesn't actually track, so they intentionally stay
     * always-current regardless of the selected range (disclosed in the
     * screen's own caption, not silently glossed over). */
    val dateRange: DateRange = DateRange.thisMonth(),
    val isLoading: Boolean = true,
)

/**
 * Feeds [DashboardReportsScreen] for every role that can see it (spec §3-§5).
 * The security boundary lives here, not in the screen: [visibleCongregationIds]
 * — resolved once, up front, from the signed-in session's own role/scope by
 * the caller (see GoPreachNavGraph) — filters which [Congregation] rows this
 * ViewModel will ever compute stats for at all. A null value means "every
 * congregation" (Super-Admin only); passing a restricted list for anyone else
 * is what actually prevents "cross-congregation access by changing IDs or
 * parameters" (spec §6) — there's no congregationId parameter anywhere in this
 * class's public API for a caller to tamper with in the first place.
 */
/** Bundles the two independent "which slice of the data am I looking at"
 * choices into one [MutableStateFlow] so [combine] below stays within
 * Kotlin's 5-flow direct overload — the alternative (a 6th separate flow)
 * needs the array-based `combine(vararg)` overload instead, which is a
 * meaningfully worse call-site for two fields this small and this related. */
private data class DashboardFilters(
    val selectedCongregationId: String? = null,
    val dateRange: DateRange = DateRange.thisMonth(),
)

@HiltViewModel
class DashboardStatsViewModel @Inject constructor(
    congregationRepository: CongregationRepository,
    roleAssignmentRepository: RoleAssignmentRepository,
    monthlyReportRepository: MonthlyReportRepository,
    personRepository: PersonRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(DashboardFilters())

    private var scopeFilter: Set<String>? = null

    /** Called once, from the nav graph, with the session's own authorized
     * congregation id(s) — see the class doc. Never exposed as a settable
     * per-request parameter on any read function below. */
    fun restrictTo(visibleCongregationIds: Set<String>?) {
        scopeFilter = visibleCongregationIds
    }

    val uiState: StateFlow<DashboardStatsUiState> = combine(
        congregationRepository.observeAll(),
        roleAssignmentRepository.observeAll(),
        monthlyReportRepository.observeAll(),
        personRepository.observeAll(),
        _filters,
    ) { congregations, assignments, reports, people, filters ->
        val scoped = scopeFilter?.let { allowed -> congregations.filter { it.id in allowed } } ?: congregations
        // Reports are the only source here with an actual point-in-time
        // dimension (MonthlyReport.periodMonth) — see DashboardStatsUiState
        // .dateRange's doc comment for why elder/publisher *counts* are
        // deliberately left out of this filter.
        val reportsInRange = reports.filter { filters.dateRange.overlapsMonth(it.periodMonth) }
        DashboardStatsUiState(
            all = CongregationStats.compute(scoped, assignments, reportsInRange).sortedBy { it.congregationName },
            overallTotal = if (scoped.size > 1) CongregationStats.total(scoped, assignments, reportsInRange) else null,
            members = computeStatMembers(scoped, assignments, people),
            selectedCongregationId = filters.selectedCongregationId,
            dateRange = filters.dateRange,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStatsUiState())

    fun selectCongregation(congregationId: String?) = _filters.update { it.copy(selectedCongregationId = congregationId) }
    fun setDateRange(range: DateRange) = _filters.update { it.copy(dateRange = range) }
}
