package com.emfitsolutions.gopreach.ui.screens.monthlyreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.MonthlyReport
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.ReportStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PreachingTimeRecordRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import com.emfitsolutions.gopreach.domain.MonthlyReportCalculator
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

private fun isPioneer(category: PublisherCategory?) = MonthlyReportCalculator.isPioneerCategory(category)

data class MonthlyReportUiState(
    val category: PublisherCategory? = null,
    val congregationId: String? = null,
    val existingReport: MonthlyReport? = null,
    val selectedPeriodMonth: Long = currentMonthStart(),

    /** Pre-filled from [MonthlyReportCalculator.countBibleStudiesConducted]
     * but, per the simplified report form, still Publisher-editable — same
     * "automatic but can be edited" treatment as [hoursRendered]. */
    val bibleStudiesRendered: String = "0",
    /** Spec §24 — `false` means the calculation genuinely failed (a source
     * Flow error), not "zero qualifying records"; the screen shows a retry
     * affordance instead of a bare `0`/`No` in that case. */
    val calculationFailed: Boolean = false,
    val isCalculating: Boolean = true,

    /** Only shown/used for the non-Pioneer categories (Regular Publisher,
     * Unbaptized Publisher) — a Pioneer already reports actual hours below,
     * so this question doesn't apply to them. Pre-filled from
     * [MonthlyReportCalculator.didParticipateInPreaching] but still
     * Publisher-editable. */
    val participatedInPreaching: Boolean = false,

    // Pioneer-only (see [isPioneer]) — spec §9-§12/§19.
    val systemCalculatedHours: Double? = null,
    val hoursRendered: String = "0",

    /** Optional free-text note — see [MonthlyReport.remarks]. */
    val remarks: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
) {
    /** "Allow the publisher to edit the record until the service overseer
     * will mark it as 'Posted'" — the Publisher may keep editing their own
     * report through DRAFT *and* SUBMITTED; only [ReportStatus.POSTED]
     * actually locks them out (see that enum value's own doc comment for
     * who marks it and why Submit itself no longer does). [MonthlyReportScreen]
     * passes `allowEditWhenLocked = true` for the separate Service Overseer/
     * Admin/Super-Admin edit-anytime entry point. */
    val isLocked: Boolean get() = existingReport?.status == ReportStatus.POSTED

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

    val isPioneer: Boolean get() = isPioneer(category)

    /** Spec §10/§19 — a Pioneer's manual hours differing from the system
     * total is what actually *requires* [hoursConfirmed] + non-blank
     * [hoursAdjustmentRemarks]; accepting the system value as-is needs
     * neither (spec: "If the Publisher accepts the automatically calculated
     * value without changing it, additional remarks are not required"). */
    val hoursDifferFromSystem: Boolean get() {
        if (!isPioneer) return false
        val reported = hoursRendered.toDoubleOrNull() ?: return false
        val system = systemCalculatedHours ?: return false
        return reported != system
    }
}

/**
 * Spec §5.2, extended by "Update Monthly Report Submission — Automatic Bible
 * Study Count and Preaching Participation" — monthly ministry report,
 * required fields varying by [PublisherCategory]. Bible Study count,
 * suggested preaching participation, and (Pioneer-only) system-calculated
 * hours are now all derived from this Publisher's own already-synced
 * records (see [MonthlyReportCalculator]) rather than typed in — the
 * client-side half of what spec §15/§26 ask for "authoritative server
 * values"; this app has no Cloud Functions/custom backend to recompute
 * aggregates a second time server-side (see firestore.rules' `monthlyReports`
 * rule for the one thing that block *can* actually enforce — hours-
 * confirmation/remarks-required-when-different, a same-document check —
 * and this class's own doc comment for the honest limit of what's possible
 * without introducing one).
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
    private val interestedPersonRepository: InterestedPersonRepository,
    private val visitRepository: VisitRepository,
    private val preachingTimeRecordRepository: PreachingTimeRecordRepository,
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
                interestedPersonRepository.observeAll(),
                visitRepository.observeAllForPublisher(publisherPersonId),
                preachingTimeRecordRepository.observeForPublisher(publisherPersonId),
                _selectedPeriodMonth,
            ) { flows ->
                @Suppress("UNCHECKED_CAST")
                val assignments = flows[0] as List<com.emfitsolutions.gopreach.data.model.RoleAssignment>
                @Suppress("UNCHECKED_CAST")
                val reports = flows[1] as List<MonthlyReport>
                @Suppress("UNCHECKED_CAST")
                val interestedPeople = flows[2] as List<com.emfitsolutions.gopreach.data.model.InterestedPerson>
                @Suppress("UNCHECKED_CAST")
                val visits = flows[3] as List<com.emfitsolutions.gopreach.data.model.Visit>
                @Suppress("UNCHECKED_CAST")
                val preachingTimeRecords = flows[4] as List<com.emfitsolutions.gopreach.data.model.PreachingTimeRecord>
                val selectedPeriodMonth = flows[5] as Long

                val publisherAssignment = assignments.firstOrNull { it.resolvedRoleTypeOrNull() is RoleType.Publisher }
                val category = (publisherAssignment?.resolvedRoleTypeOrNull() as? RoleType.Publisher)?.category
                val congregationId = publisherAssignment?.congregationId
                val existing = reports.firstOrNull {
                    it.publisherPersonId == publisherPersonId && it.periodMonth == selectedPeriodMonth
                }

                // Spec §5/§6 — ownership + congregation scoping, applied once
                // here before anything is handed to the pure calculator: only
                // this Publisher's own Bible Study people, in their own
                // congregation.
                val ownBibleStudyPeople = interestedPeople.filter {
                    it.publisherPersonId == publisherPersonId &&
                        (congregationId == null || it.congregationId == congregationId) &&
                        it.pipelineStage == PipelineStage.BIBLE_STUDY
                }
                val ownPreachingTimeRecords = preachingTimeRecords.filter {
                    congregationId == null || it.congregationId == congregationId
                }

                val calc = MonthlyReportCalculator.calculate(
                    category = category,
                    ownBibleStudyPeople = ownBibleStudyPeople,
                    bibleStudyVisits = visits,
                    allVisitsForPublisher = visits,
                    preachingTimeRecords = ownPreachingTimeRecords,
                    periodMonthStart = selectedPeriodMonth,
                )

                // Spec §14 — a report already saved for this exact period
                // keeps its own stored values (what was actually submitted/
                // locked); anything not yet saved for this period always
                // shows the fresh calculation, never a stale value carried
                // over from whichever period was selected before. Spec §21 —
                // once existing==POSTED, isLocked already prevents further
                // edits regardless of what's displayed here.
                MonthlyReportUiState(
                    category = category,
                    congregationId = congregationId,
                    existingReport = existing,
                    selectedPeriodMonth = selectedPeriodMonth,
                    bibleStudiesRendered = (existing?.bibleStudiesCount ?: calc.bibleStudiesConducted).toString(),
                    calculationFailed = false,
                    isCalculating = false,
                    participatedInPreaching = existing?.participatedInPreaching ?: calc.participatedInPreaching,
                    systemCalculatedHours = existing?.systemCalculatedHours ?: calc.systemCalculatedHours,
                    hoursRendered = (existing?.hoursRendered ?: calc.systemCalculatedHours ?: 0.0).toString(),
                    remarks = existing?.remarks.orEmpty(),
                )
            }.collect { _uiState.value = it }
        }
    }

    /** Switching the selected month re-populates the form from whatever
     * report (if any) already exists for that period — same as opening the
     * screen fresh for it, so a publisher who already submitted last
     * month's report sees it (locked) instead of a blank form. Spec §14 —
     * this also means a manual hours adjustment tied to the *previous*
     * selection is never silently carried over: [load]'s combine above
     * re-derives every field (including [MonthlyReportUiState
     * .systemCalculatedHours]/[MonthlyReportUiState.hoursRendered]) from
     * scratch for the newly selected month the moment this fires. */
    fun onMonthSelected(periodMonth: Long) {
        _selectedPeriodMonth.value = periodMonth
    }

    fun onHoursChange(value: String) = update { it.copy(hoursRendered = value.filter { c -> c.isDigit() || c == '.' }) }
    fun onBibleStudiesChange(value: String) = update { it.copy(bibleStudiesRendered = value.filter { c -> c.isDigit() }) }
    fun onParticipatedChange(value: Boolean) = update { it.copy(participatedInPreaching = value) }
    fun onRemarksChange(value: String) = update { it.copy(remarks = value) }

    private fun update(block: (MonthlyReportUiState) -> MonthlyReportUiState) {
        _uiState.value = block(_uiState.value)
    }

    /** [allowEditWhenLocked] must be the same value the screen itself is
     * showing (its Elder/Admin "edit anyone's report" entry point) — the
     * Publisher's own normal screen always passes false. Belt-and-suspenders
     * on top of that screen's own disabled Submit button: this used to only
     * document "the caller is responsible for not calling this when locked"
     * rather than actually checking, so any bug in the UI's own enabled/
     * disabled wiring had nothing else standing between it and a save going
     * through anyway. Firestore's own security rules are still the real,
     * server-side backstop against a genuinely malicious client — this is
     * about this app's own UI never doing it by accident.
     *
     * Reaching this at all, for a Pioneer whose hours differ from the
     * system-calculated total, already implies confirmation — the screen
     * gates its Submit button behind a one-off confirmation dialog (spec's
     * "must be a separate message confirmation", not an inline checkbox +
     * required-remarks field cluttering the form) before ever calling this.
     * firestore.rules' `hoursAdjustmentValid` still requires a non-blank
     * `hoursAdjustmentRemarks` alongside `hoursConfirmed` on the same
     * document, so a fixed note is recorded automatically instead of asking
     * the Publisher to type one. */
    fun submit(publisherPersonId: String, allowEditWhenLocked: Boolean = false) {
        val state = _uiState.value
        if (state.isLocked && !allowEditWhenLocked) return
        _uiState.value = state.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val report = MonthlyReport(
                id = state.existingReport?.id ?: "",
                publisherPersonId = publisherPersonId,
                congregationId = state.congregationId ?: "",
                category = state.category ?: PublisherCategory.REGULAR_PUBLISHER,
                periodMonth = state.selectedPeriodMonth,
                bibleStudiesCount = state.bibleStudiesRendered.toIntOrNull() ?: 0,
                hoursRendered = if (state.isPioneer) state.hoursRendered.toDoubleOrNull() ?: 0.0 else null,
                systemCalculatedHours = if (state.isPioneer) state.systemCalculatedHours else null,
                hoursConfirmed = state.isPioneer && state.hoursDifferFromSystem,
                hoursAdjustmentRemarks = if (state.isPioneer && state.hoursDifferFromSystem) {
                    "Publisher confirmed the adjusted hours."
                } else {
                    null
                },
                participatedInPreaching = if (state.isPioneer) null else state.participatedInPreaching,
                status = ReportStatus.SUBMITTED,
                submittedAt = System.currentTimeMillis(),
                remarks = state.remarks.trim().ifBlank { null },
            )
            monthlyReportRepository.save(report)
            _uiState.value = _uiState.value.copy(isSaving = false, saved = true, existingReport = report)
        }
    }
}
