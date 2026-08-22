package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Spec §3 — "CRUD Regular Elder", scoped by congregation (Super-Admin/Admin/
 * Coordinator Elder) — shown grouped by their assigned Group, since a Regular
 * Elder's own scope is "own group" everywhere else in the app. */
@HiltViewModel
class ManageRegularEldersViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    fun rowsFor(visibleCongregationId: String?): Flow<List<ElderRow>> = combine(
        personRepository.observeAll(),
        roleAssignmentRepository.observeAll(),
        groupRepository.observeAll(),
    ) { people, assignments, groups ->
        assignments
            .filter { (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER }
            .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
            .mapNotNull { assignment ->
                val person = people.firstOrNull { it.id == assignment.personId } ?: return@mapNotNull null
                val scopeName = groups.firstOrNull { it.id == assignment.groupId }?.name ?: "Unassigned group"
                ElderRow(person, assignment, scopeName, assignment.status == RoleAssignmentStatus.ACTIVE)
            }
            .sortedBy { it.person.fullName }
    }

    fun setActive(assignment: RoleAssignment, active: Boolean) {
        viewModelScope.launch {
            roleAssignmentRepository.save(
                assignment.copy(status = if (active) RoleAssignmentStatus.ACTIVE else RoleAssignmentStatus.INACTIVE),
            )
        }
    }
}
