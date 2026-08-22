package com.emfitsolutions.gopreach.ui.screens.admins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    /** "Delete" here means deactivate, same pattern as Publishers' recategorize —
     * the RoleAssignment record (and its audit trail) is kept, not erased. */
    fun setActive(assignment: RoleAssignment, active: Boolean) {
        viewModelScope.launch {
            roleAssignmentRepository.save(
                assignment.copy(status = if (active) RoleAssignmentStatus.ACTIVE else RoleAssignmentStatus.INACTIVE),
            )
        }
    }
}
