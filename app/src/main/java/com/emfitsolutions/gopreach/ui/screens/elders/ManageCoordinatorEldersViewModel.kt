package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RegularElderRole
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ElderRow(
    val person: Person,
    val assignment: RoleAssignment,
    val scopeName: String,
    val isActive: Boolean,
    /** Only meaningful for Regular Elder rows — their Group Overseer/Servant/
     * Assistant assignment (null for Coordinator Elder rows, or a Regular
     * Elder enrolled before this field existed and not yet placed in a Group role). */
    val regularElderRole: RegularElderRole? = null,
)

/** Spec §3 — "CRUD Coordinator Elder", Super-Admin/Admin. Scoped by congregation
 * the same way Publishers/Groups already are (`visibleCongregationId == null` for
 * Super-Admin's "all congregations" view). */
@HiltViewModel
class ManageCoordinatorEldersViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val congregationRepository: CongregationRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    fun rowsFor(visibleCongregationId: String?): Flow<List<ElderRow>> = combine(
        personRepository.observeAll(),
        roleAssignmentRepository.observeAll(),
        congregationRepository.observeAll(),
    ) { people, assignments, congregations ->
        assignments
            .filter { (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.COORDINATOR_ELDER }
            .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
            .mapNotNull { assignment ->
                val person = people.firstOrNull { it.id == assignment.personId } ?: return@mapNotNull null
                val scopeName = congregations.firstOrNull { it.id == assignment.congregationId }?.name ?: "Unassigned"
                ElderRow(person, assignment, scopeName, assignment.status == RoleAssignmentStatus.ACTIVE)
            }
            .sortedBy { it.person.fullName }
    }

    fun setActive(assignment: RoleAssignment, active: Boolean, actorPersonId: String) {
        viewModelScope.launch {
            val previous = assignment.status
            val newStatus = if (active) RoleAssignmentStatus.ACTIVE else RoleAssignmentStatus.INACTIVE
            roleAssignmentRepository.save(assignment.copy(status = newStatus))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "CHANGE_COORDINATOR_ELDER_STATUS",
                targetType = "Person",
                targetId = assignment.personId,
                congregationId = assignment.congregationId,
                details = "status: $previous -> $newStatus",
            )
        }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch { personRepository.save(person) }
    }

    /** Super-Admin/Admin-only permanent delete (see BUILD_PLAN.md scoping).
     * Deletes the Coordinator Elder RoleAssignment; deletes the Person doc too
     * only if no other RoleAssignment remains for them. */
    fun permanentlyDelete(row: ElderRow, actorPersonId: String) {
        viewModelScope.launch {
            roleAssignmentRepository.delete(row.assignment.id)
            val remaining = roleAssignmentRepository.observeAll().first().count { it.personId == row.person.id }
            if (remaining == 0) personRepository.delete(row.person.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_COORDINATOR_ELDER",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = row.assignment.congregationId,
            )
        }
    }
}
