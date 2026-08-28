package com.emfitsolutions.gopreach.ui.screens.monthlyreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.MonthlyReport
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.ReportStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** Midnight on the 1st of whichever month [monthsAgo] months before the
 * current one — 0 = this month, 1 = last month. Shared by every "which
 * period does this belong to" computation in this file so they can never
 * drift out of sync with each other. */
private fun monthStart(monthsAgo: Int): Long = Calendar.getInstance().apply {
    add(Calendar.MONTH, -monthsAgo)
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun currentMonthStart(): Long = monthStart(0)
private fun previousMonthStart(): Long = monthStart(1)

private fun daysUntilMonthEnd(): Int {
    val cal = Calendar.getInstance()
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)
}

data class MonthlyReportUiState(
    val category: PublisherCategory? = null,
    val congregationId: String? = null,
    val existingReport: MonthlyReport? = null,
    val selectedPeriodMonth: Long = currentMonthStart(),
    val bibleStudiesCount: String = "0",
    val hoursRendered: String = "0",
    val participatedInPreaching: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
) {
    /** Spec §5.2: editable/deletable by the submitter only until submitted; after
     * that only a Coordinator/Regular Elder can edit — [MonthlyReportScreen]
     * passes `allowEditWhenLocked = true` for that elder-edit entry point. */
    val isLocked: Boolean get() = existingReport?.status == ReportStatus.SUBMITTED

    /** "Submission of report is done each month... available 2 days before
     * the end of each month" spec — this restriction only makes sense for
     * the *current*, still-in-progress month (spec: "refer to the
     * restriction of sending a report for the current month"). "Select
     * Month to Report" — the publisher can also report for the recent
     * (previous, already fully elapsed) month; nothing is left to wait for
     * there, so it's always open. Checked against the device clock, not
     * [existingReport], so it stays correct across the month-boundary reset
     * with no extra state to track. */
    val canSubmitWindow: Boolean get() = selectedPeriodMonth != currentMonthStart() || daysUntilMonthEnd() <= 2
}

/** Spec §5.2 — monthly ministry report, required fields vary by [PublisherCategory].
 *
 * "Select Month to Report" — a publisher may submit for the current month
 * (subject to [MonthlyReportUiState.canSubmitWindow]'s last-2-days rule) or
 * the one just before it, and nothing earlier or later than that: [availableMonths]
 * is exactly those two periods, so the dropdown itself is the enforcement —
 * there's no way to even construct a request for a month outside that range.
 */
@HiltViewModel
class MonthlyReportViewModel @Inject constructor(
    private val monthlyReportRepository: MonthlyReportRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyReportUiState())
    val uiState: StateFlow<MonthlyReportUiState> = _uiState

    private val _selectedPeriodMonth = MutableStateFlow(currentMonthStart())

    /** "Recent month, but not earlier than [and never later than] the
     * current month" — the previous month first (oldest to newest), the
     * current month last. */
    val availableMonths: List<Long> = listOf(previousMonthStart(), currentMonthStart())

    fun load(publisherPersonId: String) {
        viewModelScope.launch {
            combine(
                roleAssignmentRepository.observeForPerson(publisherPersonId),
                monthlyReportRepository.observeAll(),
                _selectedPeriodMonth,
            ) { assignments, reports, selectedPeriodMonth ->
                val publisherAssignment = assignments.firstOrNull { it.resolvedRoleType() is RoleType.Publisher }
                val category = (publisherAssignment?.resolvedRoleType() as? RoleType.Publisher)?.category
                val existing = reports.firstOrNull {
                    it.publisherPersonId == publisherPersonId && it.periodMonth == selectedPeriodMonth
                }
                MonthlyReportUiState(
                    category = category,
                    congregationId = publisherAssignment?.congregationId,
                    existingReport = existing,
                    selectedPeriodMonth = selectedPeriodMonth,
                    bibleStudiesCount = (existing?.bibleStudiesCount ?: 0).toString(),
                    hoursRendered = (existing?.hoursRendered ?: 0.0).toString(),
                    participatedInPreaching = existing?.participatedInPreaching ?: false,
                )
            }.collect { _uiState.value = it }
        }
    }

    /** Switching the selected month re-populates the form from whatever
     * report (if any) already exists for that period — same as opening the
     * screen fresh for it, so a publisher who already submitted last
     * month's report sees it (locked) instead of a blank form. */
    fun onMonthSelected(periodMonth: Long) {
        _selectedPeriodMonth.value = periodMonth
    }

    fun onBibleStudiesChange(value: String) = update { it.copy(bibleStudiesCount = value.filter { c -> c.isDigit() }) }
    fun onHoursChange(value: String) = update { it.copy(hoursRendered = value.filter { c -> c.isDigit() || c == '.' }) }
    fun onParticipatedChange(value: Boolean) = update { it.copy(participatedInPreaching = value) }

    private fun update(block: (MonthlyReportUiState) -> MonthlyReportUiState) {
        _uiState.value = block(_uiState.value)
    }

    /** The caller (screen) is responsible for only invoking this when editing is
     * actually allowed — either the report isn't locked yet, or the signed-in
     * user is a Coordinator/Regular Elder editing a submitted report (spec §5.2). */
    fun submit(publisherPersonId: String) {
        val state = _uiState.value
        _uiState.value = state.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val report = MonthlyReport(
                id = state.existingReport?.id ?: "",
                publisherPersonId = publisherPersonId,
                congregationId = state.congregationId ?: "",
                category = state.category ?: PublisherCategory.REGULAR_PUBLISHER,
                periodMonth = state.selectedPeriodMonth,
                bibleStudiesCount = state.bibleStudiesCount.toIntOrNull() ?: 0,
                hoursRendered = if (isPioneer(state.category)) state.hoursRendered.toDoubleOrNull() ?: 0.0 else null,
                participatedInPreaching = if (!isPioneer(state.category)) state.participatedInPreaching else null,
                status = ReportStatus.SUBMITTED,
                submittedAt = System.currentTimeMillis(),
            )
            monthlyReportRepository.save(report)
            _uiState.value = _uiState.value.copy(isSaving = false, saved = true, existingReport = report)
        }
    }

    private fun isPioneer(category: PublisherCategory?) =
        category == PublisherCategory.REGULAR_PIONEER || category == PublisherCategory.AUXILIARY_PIONEER
}
