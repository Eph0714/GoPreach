package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Person
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

/** New Service Overseer role — "CRUD Service Overseer," reachable by
 * Super-Admin/Admin/Coordinator Elder. Reuses [ElderRow] (from
 * ManageCoordinatorEldersViewModel) since the shape is identical; scoped by
 * congregation the same way Coordinator Elders already are. */
@HiltViewModel
class ManageServiceOverseersViewModel @Inject constructor(
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
            .filter { (it.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role == AdminRole.SERVICE_OVERSEER }
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
                action = "CHANGE_SERVICE_OVERSEER_STATUS",
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

    /** Super-Admin/Admin-only permanent delete (see BUILD_PLAN.md scoping). */
    fun permanentlyDelete(row: ElderRow, actorPersonId: String) {
        viewModelScope.launch {
            roleAssignmentRepository.delete(row.assignment.id)
            val remaining = roleAssignmentRepository.observeAll().first().count { it.personId == row.person.id }
            if (remaining == 0) personRepository.delete(row.person.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_SERVICE_OVERSEER",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = row.assignment.congregationId,
            )
        }
    }
}
