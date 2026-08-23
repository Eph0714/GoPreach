package com.emfitsolutions.gopreach.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
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
    val members: List<StatMember> = emptyList(),
    val selectedCongregationId: String? = null,
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
@HiltViewModel
class DashboardStatsViewModel @Inject constructor(
    congregationRepository: CongregationRepository,
    roleAssignmentRepository: RoleAssignmentRepository,
    monthlyReportRepository: MonthlyReportRepository,
    personRepository: PersonRepository,
) : ViewModel() {

    private val _selectedCongregationId = MutableStateFlow<String?>(null)

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
        _selectedCongregationId,
    ) { congregations, assignments, reports, people, selected ->
        val scoped = scopeFilter?.let { allowed -> congregations.filter { it.id in allowed } } ?: congregations
        DashboardStatsUiState(
            all = CongregationStats.compute(scoped, assignments, reports).sortedBy { it.congregationName },
            members = computeStatMembers(scoped, assignments, people),
            selectedCongregationId = selected,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStatsUiState())

    fun selectCongregation(congregationId: String?) = _selectedCongregationId.update { congregationId }
}
