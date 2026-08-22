package com.emfitsolutions.gopreach.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RegularElderRole
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupRow(
    val group: Group,
    val overseerName: String?,
    val servantName: String?,
    val assistantName: String?,
)

/** Spec §3 — "CRUD Groups"; each Group needs exactly one Elder in each of three
 * roles (Overseer/Servant/Assistant) rather than the single Elder this used to
 * allow — see [Group.missingRoles]. */
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

    /** Regular Elders available for [role], scoped to one congregation and — when
     * editing an existing group — excluding whoever already fills a *different*
     * role in that same group (an Elder can't hold two of the three roles at
     * once in one Group). Matches by
     * [com.emfitsolutions.gopreach.data.model.RoleAssignment.regularElderRole]
     * when set; an Elder enrolled before that field existed has none, and is
     * offered as a candidate for *any* role — placing them in a role's dropdown
     * is what assigns them that role (see [save]), which is how an existing
     * single-Elder Group gets migrated onto the three-role structure without
     * losing who was already assigned. */
    fun availableEldersFor(congregationId: String, role: RegularElderRole, excludePersonIds: Set<String> = emptySet()): Flow<List<Person>> =
        combine(personRepository.observeAll(), roleAssignmentRepository.observeAll()) { people, assignments ->
            assignments
                .filter { assignment ->
                    assignment.status == RoleAssignmentStatus.ACTIVE &&
                        assignment.congregationId == congregationId &&
                        (assignment.regularElderRole == role || assignment.regularElderRole == null) &&
                        (assignment.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER &&
                        assignment.personId !in excludePersonIds
                }
                .mapNotNull { assignment -> people.firstOrNull { it.id == assignment.personId } }
        }

    fun rowsFor(congregationId: String?): Flow<List<GroupRow>> =
        combine(groupRepository.observeAll(), personRepository.observeAll()) { groups, people ->
            groups
                .filter { congregationId == null || it.congregationId == congregationId }
                .map { group ->
                    GroupRow(
                        group = group,
                        overseerName = people.firstOrNull { it.id == group.overseerPersonId }?.fullName,
                        servantName = people.firstOrNull { it.id == group.servantPersonId }?.fullName,
                        assistantName = people.firstOrNull { it.id == group.assistantPersonId }?.fullName,
                    )
                }
                .sortedBy { it.group.name }
        }

    /** Saves the Group, then mirrors each role slot onto that Elder's own
     * RoleAssignment.groupId — that mirror (not the Group document itself) is
     * what [com.emfitsolutions.gopreach.domain.PermissionChecker]-driven report/
     * schedule/calendar scoping actually reads for a signed-in Regular Elder.
     * Anyone *removed* from a slot (replaced, or cleared) has their groupId
     * cleared too, so they don't keep seeing a group they're no longer on. */
    fun save(group: Group) {
        viewModelScope.launch {
            val previous = if (group.id.isNotBlank()) groupRepository.observeAll().first().firstOrNull { it.id == group.id } else null
            val saved = groupRepository.save(group)

            suspend fun sync(role: RegularElderRole, oldPersonId: String?, newPersonId: String?) {
                if (oldPersonId == newPersonId) {
                    if (newPersonId != null) setElderGroup(newPersonId, saved.id, role)
                    return
                }
                if (oldPersonId != null) setElderGroup(oldPersonId, null, null)
                if (newPersonId != null) setElderGroup(newPersonId, saved.id, role)
            }

            sync(RegularElderRole.GROUP_OVERSEER, previous?.overseerPersonId, saved.overseerPersonId)
            sync(RegularElderRole.GROUP_SERVANT, previous?.servantPersonId, saved.servantPersonId)
            sync(RegularElderRole.GROUP_ASSISTANT, previous?.assistantPersonId, saved.assistantPersonId)
        }
    }

    /** [role] is only written when non-null (assigning) — clearing an Elder from a
     * slot (role = null alongside groupId = null) leaves their regularElderRole
     * as history rather than wiping it, since they may be re-added to the same
     * role in a different Group later. */
    private suspend fun setElderGroup(personId: String, groupId: String?, role: RegularElderRole?) {
        val assignment = roleAssignmentRepository.observeForPerson(personId).first()
            .firstOrNull { (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER }
            ?: return
        roleAssignmentRepository.save(
            assignment.copy(groupId = groupId, regularElderRole = role ?: assignment.regularElderRole),
        )
    }

    fun delete(groupId: String) {
        viewModelScope.launch {
            // Clear groupId for whoever was on this group so they don't keep
            // seeing a report/calendar scope that no longer exists.
            val group = groupRepository.observeAll().first().firstOrNull { it.id == groupId }
            if (group != null) {
                listOfNotNull(group.overseerPersonId, group.servantPersonId, group.assistantPersonId, group.regularElderPersonId)
                    .distinct()
                    .forEach { setElderGroup(it, null, null) }
            }
            groupRepository.delete(groupId)
        }
    }
}
