package com.emfitsolutions.gopreach.ui.screens.meetingassignments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.MidweekAssignmentItem
import com.emfitsolutions.gopreach.data.model.MidweekMeetingSchedule
import com.emfitsolutions.gopreach.data.model.MidweekSection
import com.emfitsolutions.gopreach.data.model.PublicTalkScheduleRow
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.MidweekMeetingScheduleRepository
import com.emfitsolutions.gopreach.data.repository.PublicTalkScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** Midnight, this week's Monday — spec's own example, "Week for August
 * 31-September 6," is a Monday-Sunday span, matching
 * [com.emfitsolutions.gopreach.ui.components.DateRangeSelection.DateRange
 * .Companion.thisWeek]'s already-established Monday convention. */
fun mondayOfWeek(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    val currentDow = get(Calendar.DAY_OF_WEEK)
    val daysSinceMonday = ((currentDow - Calendar.MONDAY) + 7) % 7
    add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
}.timeInMillis

/**
 * "Meeting Assignments" module — Coordinator Elder/Regular Elder/Service
 * Overseer/Admin (own congregation)/Super-Admin (every congregation) enroll
 * both the Midweek Meeting Schedule and the Public Talk and Watchtower
 * Study Schedule; every Publisher sees their own congregation's copy,
 * read-only.
 */
@HiltViewModel
class MeetingAssignmentsViewModel @Inject constructor(
    private val midweekRepository: MidweekMeetingScheduleRepository,
    private val publicTalkRepository: PublicTalkScheduleRepository,
    private val congregationRepository: CongregationRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The one schedule doc (if any) for [congregationId]/[weekStartDate] —
     * null means nothing has been enrolled for that week yet, not an error. */
    fun scheduleFor(congregationId: String, weekStartDate: Long): Flow<MidweekMeetingSchedule?> =
        midweekRepository.observeAll().map { list ->
            list.firstOrNull { it.congregationId == congregationId && it.weekStartDate == weekStartDate }
        }

    /** Replaces one sub-category's whole particulars list at once — the
     * caller (an Add/Edit/Delete/Reorder in the section's own dialog) always
     * has the full intended list in hand already, so there's no per-item
     * Firestore write to reconcile. Creates the week's schedule doc on first
     * save for that congregation/week; every later save for the same one
     * edits it in place instead of creating a duplicate. */
    fun saveSectionItems(
        existing: MidweekMeetingSchedule?,
        congregationId: String,
        weekStartDate: Long,
        section: MidweekSection,
        items: List<MidweekAssignmentItem>,
        actorPersonId: String,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val base = existing ?: MidweekMeetingSchedule(
                congregationId = congregationId,
                weekStartDate = weekStartDate,
                createdByPersonId = actorPersonId,
                createdAt = now,
            )
            val updated = base.withItems(section, items).copy(lastEditedByPersonId = actorPersonId, lastEditedAt = now)
            midweekRepository.save(updated)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "SAVE_MIDWEEK_MEETING_SCHEDULE",
                targetType = "MidweekMeetingSchedule",
                targetId = updated.id.ifBlank { "new" },
                congregationId = congregationId,
                details = section.displayLabel,
            )
        }
    }

    fun rowsFor(congregationId: String?): Flow<List<PublicTalkScheduleRow>> =
        publicTalkRepository.observeAll().map { list ->
            list.filter { congregationId == null || it.congregationId == congregationId }
                .sortedBy { it.date }
        }

    /** "Do not duplicate date" — checked here, not just left to the UI,
     * against every other row in the same congregation (excluding the row
     * being edited, if any). Returns an error message to show instead of
     * saving, or null once it's actually saved. */
    suspend fun savePublicTalkRow(
        existingRows: List<PublicTalkScheduleRow>,
        row: PublicTalkScheduleRow,
        actorPersonId: String,
    ): String? {
        val duplicate = existingRows.any { it.id != row.id && it.congregationId == row.congregationId && it.date == row.date }
        if (duplicate) return "A schedule already exists for this date. Edit that one instead, or pick a different date."
        val now = System.currentTimeMillis()
        val isNew = row.id.isBlank()
        val saved = publicTalkRepository.save(
            row.copy(
                createdByPersonId = row.createdByPersonId.ifBlank { actorPersonId },
                createdAt = row.createdAt.takeIf { it > 0L } ?: now,
                lastEditedByPersonId = actorPersonId,
                lastEditedAt = now,
            ),
        )
        auditLogRepository.log(
            actorPersonId = actorPersonId,
            action = if (isNew) "CREATE_PUBLIC_TALK_SCHEDULE" else "EDIT_PUBLIC_TALK_SCHEDULE",
            targetType = "PublicTalkScheduleRow",
            targetId = saved.id,
            congregationId = saved.congregationId,
            details = saved.theme,
        )
        return null
    }

    fun deletePublicTalkRow(row: PublicTalkScheduleRow, actorPersonId: String) {
        viewModelScope.launch {
            publicTalkRepository.delete(row.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "DELETE_PUBLIC_TALK_SCHEDULE",
                targetType = "PublicTalkScheduleRow",
                targetId = row.id,
                congregationId = row.congregationId,
                details = row.theme,
            )
        }
    }
}
