package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    /** For the Edit dialog's Congregation dropdown — Super-Admin only (an
     * Admin/Coordinator Elder editing is already fixed to their own
     * congregation, same as everywhere else in this app). */
    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The additional, simultaneous "Select Role" assignments this Service
     * Overseer holds on top of their primary role — see
     * ServiceOverseerEnrollmentViewModel's doc comment for why these are
     * separate RoleAssignment docs rather than fields on the primary one.
     * [Boolean] is Group Overseer; [PublisherCategory]? is the publisher
     * category, if any. */
    fun additionalRolesFor(personId: String): Flow<Pair<Boolean, PublisherCategory?>> =
        roleAssignmentRepository.observeForPerson(personId).map { assignments ->
            val isGroupOverseer = assignments.any {
                it.status == RoleAssignmentStatus.ACTIVE &&
                    (it.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER &&
                    it.regularElderRole == RegularElderRole.GROUP_OVERSEER
            }
            val publisherCategory = assignments
                .firstOrNull { it.status == RoleAssignmentStatus.ACTIVE && it.resolvedRoleTypeOrNull() is RoleType.Publisher }
                ?.let { (it.resolvedRoleTypeOrNull() as RoleType.Publisher).category }
            isGroupOverseer to publisherCategory
        }

    /** Saves the Person fields, an optional congregation reassignment on the
     * primary Service Overseer role, and reconciles the two additional
     * "Select Role" assignments (Group Overseer / publisher category)
     * against whatever's currently checked — creating, updating, or
     * removing each one as needed, the same "extra simultaneous
     * RoleAssignment" shape enrollment already uses. */
    fun updateRolesAndPerson(
        row: ElderRow,
        updatedPerson: Person,
        newCongregationId: String,
        isGroupOverseer: Boolean,
        publisherCategory: PublisherCategory?,
        actorPersonId: String,
    ) {
        viewModelScope.launch {
            personRepository.save(updatedPerson)

            val now = System.currentTimeMillis()
            if (row.assignment.congregationId != newCongregationId) {
                roleAssignmentRepository.save(
                    row.assignment.copy(congregationId = newCongregationId, lastEditedByPersonId = actorPersonId, lastEditedAt = now),
                )
            }

            val existingAssignments = roleAssignmentRepository.observeForPerson(row.person.id).first()

            val existingGroupOverseer = existingAssignments.firstOrNull {
                it.status == RoleAssignmentStatus.ACTIVE &&
                    (it.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role == AdminRole.REGULAR_ELDER &&
                    it.regularElderRole == RegularElderRole.GROUP_OVERSEER
            }
            when {
                isGroupOverseer && existingGroupOverseer == null -> roleAssignmentRepository.save(
                    RoleAssignment(
                        personId = row.person.id,
                        roleType = RoleType.serialize(RoleType.Admin(AdminRole.REGULAR_ELDER)),
                        congregationId = newCongregationId,
                        regularElderRole = RegularElderRole.GROUP_OVERSEER,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = now,
                        assignedByPersonId = actorPersonId,
                    ),
                )
                isGroupOverseer && existingGroupOverseer != null -> roleAssignmentRepository.save(
                    existingGroupOverseer.copy(congregationId = newCongregationId, lastEditedByPersonId = actorPersonId, lastEditedAt = now),
                )
                !isGroupOverseer && existingGroupOverseer != null -> roleAssignmentRepository.delete(existingGroupOverseer.id)
            }

            val existingPublisher = existingAssignments.firstOrNull {
                it.status == RoleAssignmentStatus.ACTIVE && it.resolvedRoleTypeOrNull() is RoleType.Publisher
            }
            when {
                publisherCategory != null && existingPublisher == null -> roleAssignmentRepository.save(
                    RoleAssignment(
                        personId = row.person.id,
                        roleType = RoleType.serialize(RoleType.Publisher(publisherCategory)),
                        congregationId = newCongregationId,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = now,
                        assignedByPersonId = actorPersonId,
                    ),
                )
                publisherCategory != null && existingPublisher != null -> roleAssignmentRepository.save(
                    existingPublisher.copy(
                        roleType = RoleType.serialize(RoleType.Publisher(publisherCategory)),
                        congregationId = newCongregationId,
                        lastEditedByPersonId = actorPersonId,
                        lastEditedAt = now,
                    ),
                )
                publisherCategory == null && existingPublisher != null -> roleAssignmentRepository.delete(existingPublisher.id)
            }

            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "EDIT_SERVICE_OVERSEER",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = newCongregationId,
                details = "groupOverseer: $isGroupOverseer, publisherCategory: $publisherCategory",
            )
        }
    }

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
