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

data class MonthlyReportUiState(
    val category: PublisherCategory? = null,
    val congregationId: String? = null,
    val existingReport: MonthlyReport? = null,
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
     * the end of each month" spec — true only inside that window. Checked
     * against the device clock, not [existingReport], so it stays correct
     * across the month-boundary reset described by the same spec item
     * ("available again in the following month using the same logic") with
     * no extra state to track: a new month means a new [periodMonth] anyway,
     * so `existingReport` for *this* period simply won't exist yet. */
    val canSubmitWindow: Boolean get() = daysUntilMonthEnd() <= 2
}

private fun daysUntilMonthEnd(): Int {
    val cal = Calendar.getInstance()
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)
}

/** Spec §5.2 — monthly ministry report, required fields vary by [PublisherCategory]. */
@HiltViewModel
class MonthlyReportViewModel @Inject constructor(
    private val monthlyReportRepository: MonthlyReportRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyReportUiState())
    val uiState: StateFlow<MonthlyReportUiState> = _uiState

    private val periodMonth: Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun load(publisherPersonId: String) {
        viewModelScope.launch {
            combine(
                roleAssignmentRepository.observeForPerson(publisherPersonId),
                monthlyReportRepository.observeAll(),
            ) { assignments, reports ->
                val publisherAssignment = assignments.firstOrNull { it.resolvedRoleType() is RoleType.Publisher }
                val category = (publisherAssignment?.resolvedRoleType() as? RoleType.Publisher)?.category
                val existing = reports.firstOrNull {
                    it.publisherPersonId == publisherPersonId && it.periodMonth == periodMonth
                }
                Triple(category, publisherAssignment?.congregationId, existing)
            }.collect { (category, congregationId, existing) ->
                _uiState.value = MonthlyReportUiState(
                    category = category,
                    congregationId = congregationId,
                    existingReport = existing,
                    bibleStudiesCount = (existing?.bibleStudiesCount ?: 0).toString(),
                    hoursRendered = (existing?.hoursRendered ?: 0.0).toString(),
                    participatedInPreaching = existing?.participatedInPreaching ?: false,
                )
            }
        }
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
                periodMonth = periodMonth,
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
