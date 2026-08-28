package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RegularElderRole
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val auditLogRepository: AuditLogRepository,
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
                ElderRow(person, assignment, scopeName, assignment.status == RoleAssignmentStatus.ACTIVE, assignment.regularElderRole)
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
                action = "CHANGE_REGULAR_ELDER_STATUS",
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

    /** The publisher category currently held alongside this Regular Elder's
     * primary role, if any — same "additional, simultaneous RoleAssignment"
     * shape [RegularElderEnrollmentViewModel] sets at enrollment time; see
     * ManageServiceOverseersViewModel.additionalRolesFor's doc comment for
     * why this is a separate doc rather than a field on the primary one. */
    fun publisherCategoryFor(personId: String): Flow<PublisherCategory?> =
        roleAssignmentRepository.observeForPerson(personId).map { assignments ->
            assignments
                .firstOrNull { it.status == RoleAssignmentStatus.ACTIVE && it.resolvedRoleTypeOrNull() is RoleType.Publisher }
                ?.let { (it.resolvedRoleTypeOrNull() as RoleType.Publisher).category }
        }

    /** "Allow the Role to be edited" — [isGroupOverseer] toggles this Regular
     * Elder's own primary RoleAssignment between [RegularElderRole
     * .GROUP_OVERSEER] and unset, exactly like the enrollment screen's own
     * checkbox does (see RegularElderEnrollmentViewModel.save). Deliberately
     * does NOT touch an existing GROUP_SERVANT/GROUP_ASSISTANT value —
     * those are only ever set by placing this person into one of a Group's
     * slots via Manage Groups, and clobbering that here on an unrelated
     * "un-check Group Overseer" edit would silently pull them out of a slot
     * this dialog never showed in the first place. [publisherCategory]
     * reconciles the same way ManageServiceOverseersViewModel does. */
    fun updateRoleAndPerson(
        row: ElderRow,
        updatedPerson: Person,
        isGroupOverseer: Boolean,
        publisherCategory: PublisherCategory?,
        actorPersonId: String,
    ) {
        viewModelScope.launch {
            personRepository.save(updatedPerson)

            val now = System.currentTimeMillis()
            val newRole = when {
                isGroupOverseer -> RegularElderRole.GROUP_OVERSEER
                row.assignment.regularElderRole == RegularElderRole.GROUP_OVERSEER -> null
                else -> row.assignment.regularElderRole
            }
            if (row.assignment.regularElderRole != newRole) {
                roleAssignmentRepository.save(
                    row.assignment.copy(regularElderRole = newRole, lastEditedByPersonId = actorPersonId, lastEditedAt = now),
                )
            }

            val existingAssignments = roleAssignmentRepository.observeForPerson(row.person.id).first()
            val existingPublisher = existingAssignments.firstOrNull {
                it.status == RoleAssignmentStatus.ACTIVE && it.resolvedRoleTypeOrNull() is RoleType.Publisher
            }
            when {
                publisherCategory != null && existingPublisher == null -> roleAssignmentRepository.save(
                    RoleAssignment(
                        personId = row.person.id,
                        roleType = RoleType.serialize(RoleType.Publisher(publisherCategory)),
                        congregationId = row.assignment.congregationId,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = now,
                        assignedByPersonId = actorPersonId,
                    ),
                )
                publisherCategory != null && existingPublisher != null -> roleAssignmentRepository.save(
                    existingPublisher.copy(
                        roleType = RoleType.serialize(RoleType.Publisher(publisherCategory)),
                        lastEditedByPersonId = actorPersonId,
                        lastEditedAt = now,
                    ),
                )
                publisherCategory == null && existingPublisher != null -> roleAssignmentRepository.delete(existingPublisher.id)
            }

            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "EDIT_REGULAR_ELDER",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = row.assignment.congregationId,
                details = "groupOverseer: $isGroupOverseer, publisherCategory: $publisherCategory",
            )
        }
    }

    /** Super-Admin/Admin-only permanent delete (see BUILD_PLAN.md scoping).
     * Clears this Elder out of any Group role slot they still occupy first
     * (spec §4: Elder → Group Assignment), then deletes their RoleAssignment,
     * and deletes the Person doc too only if no other RoleAssignment remains. */
    fun permanentlyDelete(row: ElderRow, actorPersonId: String) {
        viewModelScope.launch {
            val personId = row.person.id
            groupRepository.observeAll().first()
                .filter { it.overseerPersonId == personId || it.servantPersonId == personId || it.assistantPersonId == personId || it.regularElderPersonId == personId }
                .forEach { group ->
                    groupRepository.save(
                        group.copy(
                            overseerPersonId = if (group.overseerPersonId == personId) null else group.overseerPersonId,
                            servantPersonId = if (group.servantPersonId == personId) null else group.servantPersonId,
                            assistantPersonId = if (group.assistantPersonId == personId) null else group.assistantPersonId,
                            regularElderPersonId = if (group.regularElderPersonId == personId) null else group.regularElderPersonId,
                        ),
                    )
                }
            roleAssignmentRepository.delete(row.assignment.id)
            val remaining = roleAssignmentRepository.observeAll().first().count { it.personId == personId }
            if (remaining == 0) personRepository.delete(personId)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_REGULAR_ELDER",
                targetType = "Person",
                targetId = personId,
                congregationId = row.assignment.congregationId,
            )
        }
    }
}
