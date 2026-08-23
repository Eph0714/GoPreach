package com.emfitsolutions.gopreach.ui.screens.admins

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminRow(
    val person: Person,
    val assignment: RoleAssignment,
    val congregationName: String,
    val isActive: Boolean,
)

/**
 * Spec §3 — "CRUD Admins", Super-Admin only. Firestore has no cross-collection
 * join, so this assembles Person + their Admin RoleAssignment + Congregation name
 * client-side for display.
 */
@HiltViewModel
class ManageAdminsViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val auditLogRepository: AuditLogRepository,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    val admins: StateFlow<List<AdminRow>> = combine(
        personRepository.observeAll(),
        roleAssignmentRepository.observeAll(),
        congregationRepository.observeAll(),
    ) { people, assignments, congregations ->
        assignments
            .filter { (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.ADMIN_PER_CONGREGATION }
            .mapNotNull { assignment ->
                val person = people.firstOrNull { it.id == assignment.personId } ?: return@mapNotNull null
                val congregationName = congregations.firstOrNull { it.id == assignment.congregationId }?.name ?: "Unassigned"
                AdminRow(person, assignment, congregationName, assignment.status == RoleAssignmentStatus.ACTIVE)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updatePerson(person: Person) {
        viewModelScope.launch { personRepository.save(person) }
    }

    /** "Move to Inactive" / reactivate — the RoleAssignment record (and its
     * audit trail) is kept, not erased. */
    fun setActive(assignment: RoleAssignment, active: Boolean, actorPersonId: String) {
        viewModelScope.launch {
            val previous = assignment.status
            val newStatus = if (active) RoleAssignmentStatus.ACTIVE else RoleAssignmentStatus.INACTIVE
            roleAssignmentRepository.save(assignment.copy(status = newStatus))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "CHANGE_ADMIN_STATUS",
                targetType = "Person",
                targetId = assignment.personId,
                congregationId = assignment.congregationId,
                details = "status: $previous -> $newStatus",
            )
        }
    }

    /** Only Super-Admin ever sees this (per BUILD_PLAN.md's permanent-delete
     * scoping). Deletes the Admin RoleAssignment; deletes the Person doc too
     * only if they have no other RoleAssignment left (e.g. also enrolled as an
     * Elder or Publisher elsewhere) — never a duplicate, never orphaning a
     * still-referenced Person. */
    fun permanentlyDelete(row: AdminRow, actorPersonId: String) {
        viewModelScope.launch {
            roleAssignmentRepository.delete(row.assignment.id)
            val remaining = roleAssignmentRepository.observeAll().first().count { it.personId == row.person.id }
            if (remaining == 0) personRepository.delete(row.person.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_ADMIN",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = row.assignment.congregationId,
            )
        }
    }
}
