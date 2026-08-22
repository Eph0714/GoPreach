package com.emfitsolutions.gopreach.ui.screens.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Schedule
import com.emfitsolutions.gopreach.data.model.ScheduleKind
import com.emfitsolutions.gopreach.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Spec §5.1/§3 — "Manage Chat Schedule". Scoping matches Calendar's (spec §6.2):
 * Super-Admin sees all, Admin/Coordinator Elder their own congregation, Regular
 * Elder just their own group (via [visibleGroupId]).
 */
@HiltViewModel
class ManageChatSchedulesViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    fun schedulesFor(visibleCongregationId: String?, visibleGroupId: String?): Flow<List<Schedule>> =
        scheduleRepository.observeAll().map { all ->
            all.filter { it.kind == ScheduleKind.CHAT_SCHEDULE }
                .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
                .filter { visibleGroupId == null || it.groupId == visibleGroupId }
                .sortedBy { it.startTime }
        }

    fun save(schedule: Schedule) {
        viewModelScope.launch { scheduleRepository.save(schedule) }
    }

    fun delete(scheduleId: String) {
        viewModelScope.launch { scheduleRepository.delete(scheduleId) }
    }
}
