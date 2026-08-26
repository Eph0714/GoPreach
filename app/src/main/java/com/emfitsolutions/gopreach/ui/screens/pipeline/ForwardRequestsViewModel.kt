package com.emfitsolutions.gopreach.ui.screens.pipeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.ForwardRequest
import com.emfitsolutions.gopreach.data.model.ForwardRequestStatus
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.ForwardRequestRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Service Overseer's (also Coordinator Elder/Admin/Super-Admin, per
 * the same "who can enroll a Service Overseer" access set) incoming "Forward
 * to Other Congregation" review queue.
 */
@HiltViewModel
class ForwardRequestsViewModel @Inject constructor(
    private val forwardRequestRepository: ForwardRequestRepository,
    private val interestedPersonRepository: InterestedPersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val personRepository: PersonRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    /** Pending requests addressed to any of [congregationIds] — `null` means
     * every congregation (Super-Admin). */
    fun pendingRequestsFor(congregationIds: Set<String>?): Flow<List<ForwardRequest>> =
        forwardRequestRepository.observeAll().map { list ->
            list.filter { it.status == ForwardRequestStatus.PENDING && (congregationIds == null || it.toCongregationId in congregationIds) }
                .sortedByDescending { it.requestedAt }
        }

    /** Active, non-removed publishers of [congregationId] — the "ASSIGN TO"
     * dropdown's candidate list (spec: "Do not include (Removed) status"). */
    fun assignablePublishers(congregationId: String): Flow<List<Person>> = combine(
        roleAssignmentRepository.observeAll(),
        personRepository.observeAll(),
    ) { assignments, people ->
        assignments
            .filter { it.status == RoleAssignmentStatus.ACTIVE && it.congregationId == congregationId }
            .mapNotNull { (it.resolvedRoleTypeOrNull() as? RoleType.Publisher)?.let { p -> it to p } }
            .filter { (_, publisher) -> publisher.category != PublisherCategory.REMOVED_PUBLISHER }
            .mapNotNull { (assignment, _) -> people.firstOrNull { it.id == assignment.personId } }
            .distinctBy { it.id }
            .sortedBy { it.fullName }
    }

    fun accept(request: ForwardRequest, assignedTo: Person, actorPersonId: String) {
        viewModelScope.launch {
            val person = interestedPersonRepository.observeAll().first().firstOrNull { it.id == request.interestedPersonId } ?: return@launch
            interestedPersonRepository.save(
                person.copy(congregationId = request.toCongregationId, publisherPersonId = assignedTo.id)
            )
            val now = System.currentTimeMillis()
            forwardRequestRepository.save(
                request.copy(
                    status = ForwardRequestStatus.ACCEPTED,
                    respondedAt = now,
                    respondedByPersonId = actorPersonId,
                    assignedToPublisherPersonId = assignedTo.id,
                    assignedToPublisherNameSnapshot = assignedTo.fullName,
                )
            )
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "ACCEPT_FORWARD_REQUEST",
                targetType = "ForwardRequest",
                targetId = request.id,
                details = "${request.personNameSnapshot} -> ${request.toCongregationNameSnapshot} / ${assignedTo.fullName}",
            )
        }
    }

    fun decline(request: ForwardRequest, actorPersonId: String) {
        viewModelScope.launch {
            forwardRequestRepository.save(
                request.copy(status = ForwardRequestStatus.DECLINED, respondedAt = System.currentTimeMillis(), respondedByPersonId = actorPersonId)
            )
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "DECLINE_FORWARD_REQUEST",
                targetType = "ForwardRequest",
                targetId = request.id,
                details = request.personNameSnapshot,
            )
        }
    }
}
