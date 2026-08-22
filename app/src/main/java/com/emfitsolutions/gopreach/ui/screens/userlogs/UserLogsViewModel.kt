package com.emfitsolutions.gopreach.ui.screens.userlogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AuditLogEntry
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogRow(val entry: AuditLogEntry, val actorName: String)

/**
 * Spec §3 — "user logs". Scoping (Super-Admin: all; Admin/Coordinator Elder: own
 * congregation only; Regular Elder: no access) is enforced by the screen only
 * showing this ViewModel to roles that pass [com.emfitsolutions.gopreach.domain.PermissionChecker];
 * [visibleCongregationId] further narrows the data itself for the congregation-
 * scoped roles (null means "show everything", i.e. Super-Admin).
 */
@HiltViewModel
class UserLogsViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepository,
    private val personRepository: PersonRepository,
) : ViewModel() {

    fun rowsFor(visibleCongregationId: String?): Flow<List<LogRow>> =
        combine(auditLogRepository.observeAll(), personRepository.observeAll()) { entries, people ->
            entries
                .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
                .sortedByDescending { it.timestamp }
                .map { entry ->
                    val actorName = people.firstOrNull { it.id == entry.actorPersonId }?.fullName ?: entry.actorPersonId
                    LogRow(entry, actorName)
                }
        }

    fun delete(entryId: String) {
        viewModelScope.launch { auditLogRepository.delete(entryId) }
    }
}
