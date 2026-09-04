package com.emfitsolutions.gopreach.ui.screens.pipeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.ForwardRequest
import com.emfitsolutions.gopreach.data.model.ForwardRequestStatus
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.PublisherForwardRequest
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.ForwardRequestRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.PublisherForwardRequestRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [PipelineScreen] for all three of its stages (see [PipelineStage]) —
 * one ViewModel, not three, since a Searching/Return Visit/Bible Study record
 * is the exact same [InterestedPerson] entity throughout its life; only the
 * screen's own filtering and which action buttons it shows differ by stage.
 */
@HiltViewModel
class PipelineViewModel @Inject constructor(
    private val interestedPersonRepository: InterestedPersonRepository,
    private val visitRepository: VisitRepository,
    private val auditLogRepository: AuditLogRepository,
    private val locationTracker: LocationTracker,
    private val personRepository: PersonRepository,
    private val forwardRequestRepository: ForwardRequestRepository,
    private val publisherForwardRequestRepository: PublisherForwardRequestRepository,
    private val congregationRepository: CongregationRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
) : ViewModel() {

    /** Bug fix: [save] used to let any exception from the repository/Room/
     * Gson layer propagate out of its `viewModelScope.launch` uncaught —
     * exactly the pattern documented in [com.emfitsolutions.gopreach.data
     * .sync.mirrorFirestoreCollection]'s crash fix, which kills the whole
     * process rather than just failing the one save. A record with a large
     * supporting photo attached is the case most likely to trip a layer like
     * that, which is what made this reproduce as "the app closes when I
     * click Create or Save" for records with a photo. Now caught and
     * reported here instead. */
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    fun personName(personId: String): Flow<String?> =
        personRepository.observeAll().map { people -> people.firstOrNull { it.id == personId }?.fullName }

    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()
    suspend fun captureCurrentLocation(): LatLng? = locationTracker.getCurrentLocation()

    /** Every record this publisher owns at [stage], active-only unless
     * [includeInactive] (same "Show Inactive" convention as every other list
     * screen in this app). */
    fun peopleFor(publisherPersonId: String, stage: PipelineStage): Flow<List<InterestedPerson>> =
        interestedPersonRepository.observeAll()
            .map { list -> list.filter { it.publisherPersonId == publisherPersonId && it.pipelineStage == stage } }

    fun save(person: InterestedPerson) {
        viewModelScope.launch {
            runCatching { interestedPersonRepository.save(person) }
                .onFailure { e ->
                    Log.e("PipelineViewModel", "Failed to save Interested Person record", e)
                    _errorEvents.emit("Could not save the record: ${e.message ?: "unknown error"}")
                }
        }
    }

    fun setStatus(person: InterestedPerson, status: RecordStatus, actorPersonId: String) {
        viewModelScope.launch {
            val previous = person.status
            interestedPersonRepository.save(person.copy(status = status))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "CHANGE_INTERESTED_PERSON_STATUS",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = "status: $previous -> $status (${person.name})",
            )
        }
    }

    fun permanentlyDelete(person: InterestedPerson, actorPersonId: String) {
        viewModelScope.launch {
            visitRepository.observeForInterestedPerson(person.id).first()
                .forEach { visit -> visitRepository.delete(person.id, visit.id) }
            interestedPersonRepository.delete(person.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_INTERESTED_PERSON",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = person.name,
            )
        }
    }

    /** "MOVE TO RETURN VISIT MODULE" / "MOVE TO BIBLE STUDY MODULE" — and,
     * per "Add Reverse Status Movement," the same one-step move backward
     * (Bible Study → Return Visit → Searching Interested Person) reuses this
     * exact function; [newStage] was never restricted to "forward" in code,
     * only by which buttons [PipelineScreen] chose to show (see that
     * screen's own `previousStage()`/`nextStage()`). Updates the *same*
     * [InterestedPerson] record in place — no new record is ever created,
     * and every other field (personal info, GPS, notes, assigned Publisher,
     * Visit History, prior audit-log entries) is untouched — only
     * [InterestedPerson.pipelineStage] changes. Bumps
     * [InterestedPerson.stageEnteredAt] so date-range reports (Consolidated
     * Report, Publisher Dashboard) count this transition on the day it
     * actually happened; a move backward re-enters the earlier module the
     * same way a forward move enters the next one, and [peopleFor]'s own
     * `pipelineStage == stage` filter is what makes the record vanish from
     * its old module's list and appear in the new one automatically — no
     * separate "module sync" step exists to reuse or duplicate. */
    fun advanceStage(person: InterestedPerson, newStage: PipelineStage, actorPersonId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            interestedPersonRepository.save(person.copy(pipelineStage = newStage, stageEnteredAt = now))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "ADVANCE_PIPELINE_STAGE",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = "${person.pipelineStage} -> $newStage (${person.name})",
            )
        }
    }

    fun saveGpsLocation(person: InterestedPerson, lat: Double, lng: Double, accuracyMeters: Float?, capturedByPersonId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            interestedPersonRepository.save(
                person.copy(gpsLat = lat, gpsLng = lng, gpsAccuracy = accuracyMeters, gpsCapturedAt = now, gpsCapturedBy = capturedByPersonId, gpsUpdatedAt = now)
            )
            auditLogRepository.log(actorPersonId = capturedByPersonId, action = "CAPTURE_INTERESTED_PERSON_GPS", targetType = "InterestedPerson", targetId = person.id, details = person.name)
        }
    }

    fun clearGpsLocation(person: InterestedPerson, actorPersonId: String) {
        viewModelScope.launch {
            interestedPersonRepository.save(person.copy(gpsLat = null, gpsLng = null, gpsAccuracy = null, gpsCapturedAt = null, gpsCapturedBy = null, gpsUpdatedAt = null))
            auditLogRepository.log(actorPersonId = actorPersonId, action = "CLEAR_INTERESTED_PERSON_GPS", targetType = "InterestedPerson", targetId = person.id, details = person.name)
        }
    }

    fun visitsFor(interestedPersonId: String): Flow<List<Visit>> = visitRepository.observeForInterestedPerson(interestedPersonId)
    fun startVisitSync(interestedPersonId: String): Flow<Unit> = visitRepository.startRemoteSync(interestedPersonId)
    fun saveVisit(visit: Visit) {
        viewModelScope.launch { visitRepository.save(visit) }
    }
    fun deleteVisit(interestedPersonId: String, visitId: String) {
        viewModelScope.launch { visitRepository.delete(interestedPersonId, visitId) }
    }

    /** "FORWARD TO OTHER CONGREGATION" spec flow — every other active
     * congregation, for the search-by-name-or-language picker. */
    fun congregationName(congregationId: String): Flow<String?> =
        congregationRepository.observeAll().map { list -> list.firstOrNull { it.id == congregationId }?.name }

    fun otherCongregations(ownCongregationId: String): Flow<List<Congregation>> =
        congregationRepository.observeAll()
            .map { list -> list.filter { it.status == RecordStatus.ACTIVE && it.id != ownCongregationId }.sortedBy { it.name } }

    /** Live status of [person]'s most recent forward attempt, if any — the
     * sending publisher's own screen reads this to show "Forward status:
     * Pending/Accepted/Declined" without a separate lookup table. */
    fun forwardRequestFor(person: InterestedPerson): Flow<ForwardRequest?> = forwardRequestRepository.observeAll()
        .map { list -> person.pendingForwardRequestId?.let { id -> list.firstOrNull { it.id == id } } }

    /** Every cross-congregation forward [publisherPersonId] has ever sent —
     * "there will be a notification for the Service Overseer and the
     * Publisher for the status of the request" (Return Visit forward spec):
     * used by the sending publisher's own Home screen notifier to catch an
     * Accept/Decline outcome, the same way [PublisherForwardRequestsViewModel
     * .outgoingRequestsFor] does for the same-congregation flow. */
    fun outgoingForwardRequestsFor(publisherPersonId: String): Flow<List<ForwardRequest>> =
        forwardRequestRepository.observeAll().map { list -> list.filter { it.fromPublisherPersonId == publisherPersonId } }

    fun forward(
        person: InterestedPerson,
        toCongregation: Congregation,
        fromCongregationName: String,
        fromPublisherName: String,
        actorPersonId: String,
    ) {
        viewModelScope.launch {
            val request = forwardRequestRepository.save(
                ForwardRequest(
                    interestedPersonId = person.id,
                    personNameSnapshot = person.name,
                    fromCongregationId = person.congregationId,
                    fromCongregationNameSnapshot = fromCongregationName,
                    fromPublisherPersonId = person.publisherPersonId,
                    fromPublisherNameSnapshot = fromPublisherName,
                    toCongregationId = toCongregation.id,
                    toCongregationNameSnapshot = toCongregation.name,
                    status = ForwardRequestStatus.PENDING,
                    requestedAt = System.currentTimeMillis(),
                )
            )
            interestedPersonRepository.save(person.copy(pendingForwardRequestId = request.id))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "FORWARD_INTERESTED_PERSON",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = "${person.name} -> ${toCongregation.name}",
            )
        }
    }

    /** "FORWARD TO OTHER PUBLISHER" spec flow — every other active,
     * non-removed publisher in [congregationId] (same congregation only —
     * this flow never crosses congregations), excluding the sender
     * themselves. */
    fun otherPublishers(congregationId: String, excludePersonId: String): Flow<List<Person>> = combine(
        roleAssignmentRepository.observeAll(),
        personRepository.observeAll(),
    ) { assignments, people ->
        assignments
            .filter { it.status == RoleAssignmentStatus.ACTIVE && it.congregationId == congregationId && it.personId != excludePersonId }
            .mapNotNull { (it.resolvedRoleTypeOrNull() as? RoleType.Publisher)?.let { p -> it to p } }
            .filter { (_, publisher) -> publisher.category != PublisherCategory.REMOVED_PUBLISHER }
            .mapNotNull { (assignment, _) -> people.firstOrNull { it.id == assignment.personId } }
            .distinctBy { it.id }
            .sortedBy { it.fullName }
    }

    /** Live status of [person]'s most recent same-congregation forward
     * attempt, if any — mirrors [forwardRequestFor] for the "FORWARD TO
     * OTHER PUBLISHER" flow. */
    fun publisherForwardRequestFor(person: InterestedPerson): Flow<PublisherForwardRequest?> = publisherForwardRequestRepository.observeAll()
        .map { list -> person.pendingPublisherForwardRequestId?.let { id -> list.firstOrNull { it.id == id } } }

    fun forwardToPublisher(
        person: InterestedPerson,
        toPublisher: Person,
        fromPublisherName: String,
        actorPersonId: String,
    ) {
        viewModelScope.launch {
            val request = publisherForwardRequestRepository.save(
                PublisherForwardRequest(
                    interestedPersonId = person.id,
                    personNameSnapshot = person.name,
                    congregationId = person.congregationId,
                    fromPublisherPersonId = person.publisherPersonId,
                    fromPublisherNameSnapshot = fromPublisherName,
                    toPublisherPersonId = toPublisher.id,
                    toPublisherNameSnapshot = toPublisher.fullName,
                    status = ForwardRequestStatus.PENDING,
                    requestedAt = System.currentTimeMillis(),
                )
            )
            interestedPersonRepository.save(person.copy(pendingPublisherForwardRequestId = request.id))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "FORWARD_INTERESTED_PERSON_TO_PUBLISHER",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = "${person.name} -> ${toPublisher.fullName}",
            )
        }
    }
}
