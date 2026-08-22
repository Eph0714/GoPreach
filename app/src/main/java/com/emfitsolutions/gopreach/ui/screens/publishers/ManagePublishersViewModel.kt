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
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    groupRepository: GroupRepository,
) : ViewModel() {

    private val groups: Flow<List<Group>> = groupRepository.observeAll()

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
}
