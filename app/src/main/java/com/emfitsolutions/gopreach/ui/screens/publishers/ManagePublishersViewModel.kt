package com.emfitsolutions.gopreach.ui.screens.publishers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PublisherRow(
    val person: Person,
    val assignment: RoleAssignment,
    val category: PublisherCategory,
    val groupName: String,
)

/**
 * Spec §3/§5.1 — "CRUD Publishers (all categories)": Super-Admin sees every
 * congregation, Admin/Coordinator Elder see only their own (via
 * [visibleCongregationId]), Regular Elder has no access to this screen at all.
 *
 * "Delete" here means recategorizing to [PublisherCategory.REMOVED_PUBLISHER]
 * rather than erasing the record — spec §2.2 already models Removed/Inactive as
 * publisher categories, so changing category *is* the CRUD-delete/reactivate
 * operation; the Person and their historical reports stay intact.
 */
@HiltViewModel
class ManagePublishersViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val auditLogRepository: AuditLogRepository,
    private val monthlyReportRepository: MonthlyReportRepository,
    private val interestedPersonRepository: InterestedPersonRepository,
    groupRepository: GroupRepository,
) : ViewModel() {

    private val groups: Flow<List<Group>> = groupRepository.observeAll()

    /** For the Edit Publisher dialog's Group dropdown — every Group in this
     * publisher's own congregation (a Publisher can only ever belong to a
     * Group within their own congregation). */
    fun groupsFor(congregationId: String?): Flow<List<Group>> =
        groups.map { list -> list.filter { it.congregationId == congregationId }.sortedBy { it.name } }

    fun rowsFor(visibleCongregationId: String?): Flow<List<PublisherRow>> =
        combine(personRepository.observeAll(), roleAssignmentRepository.observeAll(), groups) { people, assignments, groups ->
            assignments
                .filter { it.resolvedRoleType() is RoleType.Publisher }
                .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
                .mapNotNull { assignment ->
                    val person = people.firstOrNull { it.id == assignment.personId } ?: return@mapNotNull null
                    val category = (assignment.resolvedRoleType() as RoleType.Publisher).category
                    val groupName = groups.firstOrNull { it.id == assignment.groupId }?.name ?: "Unassigned"
                    PublisherRow(person, assignment, category, groupName)
                }
                .sortedBy { it.person.fullName }
        }

    fun changeCategory(row: PublisherRow, newCategory: PublisherCategory, changedByPersonId: String) {
        viewModelScope.launch {
            val updated = row.assignment.copy(
                roleType = RoleType.serialize(RoleType.Publisher(newCategory)),
                lastEditedByPersonId = changedByPersonId,
                lastEditedAt = System.currentTimeMillis(),
            )
            roleAssignmentRepository.save(updated)
            auditLogRepository.log(
                actorPersonId = changedByPersonId,
                action = "CHANGE_PUBLISHER_CATEGORY",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = row.assignment.congregationId,
            )
        }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch { personRepository.save(person) }
    }

    /** "Enable the user to edit all entities like the Groups" — moves this
     * Publisher's RoleAssignment to a different Group within their own
     * congregation (or clears it back to Unassigned with `null`), the same
     * kind of write [changeCategory] already makes to the same document. */
    fun changeGroup(row: PublisherRow, newGroupId: String?, changedByPersonId: String) {
        viewModelScope.launch {
            val updated = row.assignment.copy(
                groupId = newGroupId,
                lastEditedByPersonId = changedByPersonId,
                lastEditedAt = System.currentTimeMillis(),
            )
            roleAssignmentRepository.save(updated)
            auditLogRepository.log(
                actorPersonId = changedByPersonId,
                action = "CHANGE_PUBLISHER_GROUP",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = row.assignment.congregationId,
            )
        }
    }

    /** Returns a human-readable reason permanent deletion is blocked, or null if
     * safe (spec §4: Publisher → Monthly Reports / Interested Persons / Bible
     * Study Records must not be silently destroyed). */
    suspend fun permanentDeleteBlockReason(publisherPersonId: String): String? {
        val reportCount = monthlyReportRepository.observeAll().first().count { it.publisherPersonId == publisherPersonId }
        // Covers every pipeline stage — Searching/Return Visit/Bible Study are
        // all InterestedPerson records now (see PipelineStage), not a
        // separate Bible Study collection.
        val interestedCount = interestedPersonRepository.observeAll().first().count { it.publisherPersonId == publisherPersonId }
        return when {
            reportCount > 0 -> "This publisher has $reportCount monthly report(s) on file. Use Move to Inactive instead to keep their history."
            interestedCount > 0 -> "This publisher has $interestedCount interested person / Bible study record(s) on file. Use Move to Inactive instead."
            else -> null
        }
    }

    /** Only reachable when [permanentDeleteBlockReason] returned null — deletes
     * the RoleAssignment, and the Person doc too only if they have no other
     * RoleAssignment left (e.g. also an Elder/Admin elsewhere). */
    fun permanentlyDelete(row: PublisherRow, actorPersonId: String) {
        viewModelScope.launch {
            roleAssignmentRepository.delete(row.assignment.id)
            val remaining = roleAssignmentRepository.observeAll().first().count { it.personId == row.person.id }
            if (remaining == 0) personRepository.delete(row.person.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_PUBLISHER",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = row.assignment.congregationId,
            )
        }
    }
}
