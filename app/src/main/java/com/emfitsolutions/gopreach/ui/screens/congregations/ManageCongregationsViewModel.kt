package com.emfitsolutions.gopreach.ui.screens.congregations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Spec §3/§5.1 — "Manage Congregation Master File", Super-Admin only.
 *
 * "Admin Record Deletion and Inactive Status" spec: Delete no longer means
 * immediate destruction — [setStatus] is "Move to Inactive" (record and
 * everything under it stays exactly as it is, just hidden from normal
 * active lists), and [permanentlyDelete] is the harder, referential-
 * integrity-checked path (spec §4: a congregation with any Group or any
 * Admin/Elder/Publisher RoleAssignment still pointing at it — active *or*
 * inactive, since an inactive one is still a real historical record — can
 * never be permanently deleted; only an empty congregation can be). */
@HiltViewModel
class ManageCongregationsViewModel @Inject constructor(
    private val congregationRepository: CongregationRepository,
    private val groupRepository: GroupRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    val congregations: StateFlow<List<Congregation>> = congregationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun update(congregation: Congregation, updatedByPersonId: String) {
        viewModelScope.launch {
            congregationRepository.save(congregation)
            auditLogRepository.log(
                actorPersonId = updatedByPersonId,
                action = "UPDATE_CONGREGATION",
                targetType = "Congregation",
                targetId = congregation.id,
                congregationId = congregation.id,
            )
        }
    }

    /** "Move to Inactive" / reactivate — record and every Group/Admin/Elder/
     * Publisher still under it are completely untouched; this only changes
     * whether the congregation itself shows up in the normal active list. */
    fun setStatus(congregation: Congregation, status: RecordStatus, actorPersonId: String) {
        viewModelScope.launch {
            val previous = congregation.status
            congregationRepository.save(congregation.copy(status = status))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "CHANGE_CONGREGATION_STATUS",
                targetType = "Congregation",
                targetId = congregation.id,
                congregationId = congregation.id,
                details = "status: $previous -> $status",
            )
        }
    }

    /** Returns a human-readable reason the delete is blocked, or null if it's
     * safe to proceed — checked *before* the second confirmation is ever
     * shown, and re-checked here (not just trusted from a stale UI list)
     * since data can change between opening the dialog and confirming it. */
    suspend fun permanentDeleteBlockReason(congregationId: String): String? {
        val groupCount = groupRepository.observeAll().first().count { it.congregationId == congregationId }
        val assignmentCount = roleAssignmentRepository.observeAll().first().count { it.congregationId == congregationId }
        return when {
            groupCount > 0 -> "This congregation still has $groupCount group(s) assigned to it. Move or delete those first, or use Move to Inactive instead."
            assignmentCount > 0 -> "This congregation still has $assignmentCount admin/elder/publisher record(s) assigned to it (including inactive ones). Use Move to Inactive instead."
            else -> null
        }
    }

    fun permanentlyDelete(congregationId: String, actorPersonId: String) {
        viewModelScope.launch {
            congregationRepository.delete(congregationId)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_CONGREGATION",
                targetType = "Congregation",
                targetId = congregationId,
                congregationId = congregationId,
            )
        }
    }
}
