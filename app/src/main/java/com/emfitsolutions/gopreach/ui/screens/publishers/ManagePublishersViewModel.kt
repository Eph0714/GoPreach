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
import com.emfitsolutions.gopreach.data.repository.VisitRepository
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
    private val visitRepository: VisitRepository,
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

    /** Non-blocking heads-up for the confirmation dialog — Super-Admin
     * permanent delete now cascades (see [permanentlyDelete]), so this no
     * longer refuses the action; it just tells them what else is about to go
     * with it (spec: "delete the record permanently including associated
     * record inside it"). Null when there's nothing extra to mention. */
    suspend fun permanentDeleteImpactSummary(publisherPersonId: String): String? {
        val reportCount = monthlyReportRepository.observeAll().first().count { it.publisherPersonId == publisherPersonId }
        // Covers every pipeline stage — Searching/Return Visit/Bible Study are
        // all InterestedPerson records now (see PipelineStage), not a
        // separate Bible Study collection.
        val interestedCount = interestedPersonRepository.observeAll().first().count { it.publisherPersonId == publisherPersonId }
        if (reportCount == 0 && interestedCount == 0) return null
        val parts = buildList {
            if (reportCount > 0) add("$reportCount monthly report(s)")
            if (interestedCount > 0) add("$interestedCount interested person / Bible study record(s) (with their visit history)")
        }
        return "This will also permanently delete " + parts.joinToString(" and ") + "."
    }

    /** Super-Admin-only (see [canPermanentlyDelete]) cascading delete: removes
     * every Monthly Report and Interested Person (plus each one's Visit
     * subcollection) tied to this publisher, then the RoleAssignment, and the
     * Person doc too only if they have no other RoleAssignment left (e.g.
     * also an Elder/Admin elsewhere). Nothing here is reachable through
     * "Move to Inactive," which stays the non-destructive default. */
    fun permanentlyDelete(row: PublisherRow, actorPersonId: String) {
        viewModelScope.launch {
            val reports = monthlyReportRepository.observeAll().first().filter { it.publisherPersonId == row.person.id }
            reports.forEach { monthlyReportRepository.delete(it.id) }

            val interestedPeople = interestedPersonRepository.observeAll().first().filter { it.publisherPersonId == row.person.id }
            interestedPeople.forEach { interestedPerson ->
                val visits = visitRepository.observeForInterestedPerson(interestedPerson.id).first()
                visits.forEach { visit -> visitRepository.delete(interestedPerson.id, visit.id) }
                interestedPersonRepository.delete(interestedPerson.id)
            }

            roleAssignmentRepository.delete(row.assignment.id)
            val remaining = roleAssignmentRepository.observeAll().first().count { it.personId == row.person.id }
            if (remaining == 0) personRepository.delete(row.person.id)

            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_PUBLISHER",
                targetType = "Person",
                targetId = row.person.id,
                congregationId = row.assignment.congregationId,
                details = "cascaded: ${reports.size} monthly report(s), ${interestedPeople.size} interested person/Bible study record(s)",
            )
        }
    }
}
