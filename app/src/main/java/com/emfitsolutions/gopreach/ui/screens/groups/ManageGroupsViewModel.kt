package com.emfitsolutions.gopreach.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupRow(val group: Group, val elderName: String?)

/** Spec §3 — "CRUD Groups + assign 1 Elder". */
@HiltViewModel
class ManageGroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    /** Only needed by a Super-Admin, who isn't scoped to one congregation
     * already — lets them pick which congregation a new group belongs to. */
    val congregations: Flow<List<Congregation>> = congregationRepository.observeAll()

    /** Regular Elders available to assign, scoped to one congregation — a group's
     * elder must belong to the same congregation as the group (spec §4.4: elders
     * are enrolled per-congregation). */
    fun availableEldersFor(congregationId: String): Flow<List<Person>> =
        combine(personRepository.observeAll(), roleAssignmentRepository.observeAll()) { people, assignments ->
            assignments
                .filter { assignment ->
                    assignment.status == RoleAssignmentStatus.ACTIVE &&
                        assignment.congregationId == congregationId &&
                        (assignment.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER
                }
                .mapNotNull { assignment -> people.firstOrNull { it.id == assignment.personId } }
        }

    fun rowsFor(congregationId: String?): Flow<List<GroupRow>> =
        combine(groupRepository.observeAll(), personRepository.observeAll()) { groups, people ->
            groups
                .filter { congregationId == null || it.congregationId == congregationId }
                .map { group ->
                    val elderName = people.firstOrNull { it.id == group.regularElderPersonId }?.fullName
                    GroupRow(group, elderName)
                }
                .sortedBy { it.group.name }
        }

    fun save(group: Group) {
        viewModelScope.launch { groupRepository.save(group) }
    }

    fun delete(groupId: String) {
        viewModelScope.launch { groupRepository.delete(groupId) }
    }
}
