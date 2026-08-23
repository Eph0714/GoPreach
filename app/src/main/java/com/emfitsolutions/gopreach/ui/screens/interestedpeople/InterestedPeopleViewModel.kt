package com.emfitsolutions.gopreach.ui.screens.interestedpeople

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Spec §6.3 — Interested People Records, publisher-managed, each with multiple visits. */
@HiltViewModel
class InterestedPeopleViewModel @Inject constructor(
    private val interestedPersonRepository: InterestedPersonRepository,
    private val visitRepository: VisitRepository,
    private val auditLogRepository: AuditLogRepository,
    private val locationTracker: LocationTracker,
    private val personRepository: PersonRepository,
) : ViewModel() {

    /** "Created By" (spec §2/§6) display — resolves a personId to a full
     * name for the detail screen; `null` while unresolved or if the person
     * can no longer be found (e.g. deleted), rather than crashing on a
     * missing lookup. */
    fun personName(personId: String): Flow<String?> =
        personRepository.observeAll().map { people -> people.firstOrNull { it.id == personId }?.fullName }

    /** "Interested Person GPS Capture" spec §5 — request permission first;
     * the UI checks this before ever calling [captureCurrentLocation]. */
    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()

    /** Reads the device's current GPS fix, if permission is already granted
     * and a fix is obtainable — works fully offline (spec §10: "do not
     * require Internet access simply to capture GPS coordinates"), since
     * GPS/Play Services location doesn't need connectivity, only the sync of
     * the *result* does, and that already rides the existing offline-first
     * write path (see [saveGpsLocation]). */
    suspend fun captureCurrentLocation(): LatLng? = locationTracker.getCurrentLocation()

    fun peopleFor(publisherPersonId: String): Flow<List<InterestedPerson>> =
        interestedPersonRepository.observeAll().map { list -> list.filter { it.publisherPersonId == publisherPersonId } }

    fun save(person: InterestedPerson) {
        viewModelScope.launch { interestedPersonRepository.save(person) }
    }

    /** "Move to Inactive" / reactivate — the record and all its visits are kept,
     * untouched; this only hides it from the normal active list. */
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

    /** Only Super-Admin ever sees this (see BUILD_PLAN.md scoping). Cascades to
     * every child Visit document first — the old [delete] never did this,
     * silently orphaning them despite UI copy claiming otherwise; fixed here. */
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

    /** "Interested Person GPS Capture" spec §5/§6/§11 — capture (first time)
     * and edit (replace) share this one path: both are "save these
     * coordinates as the current location," so there's no separate
     * "is this the first capture" branch to get wrong. Persisted immediately
     * through the same offline-first [InterestedPersonRepository] every other
     * field uses — no Internet required (spec §10): the write lands in the
     * local cache and sync queue right away, and goes to the server the next
     * time the user syncs, exactly like any other field on this record.
     * [capturedByPersonId] is the signed-in session doing the capture, not
     * necessarily [InterestedPerson.publisherPersonId] (an Elder could
     * capture on a Publisher's behalf). */
    fun saveGpsLocation(person: InterestedPerson, lat: Double, lng: Double, accuracyMeters: Float?, capturedByPersonId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            interestedPersonRepository.save(
                person.copy(
                    gpsLat = lat,
                    gpsLng = lng,
                    gpsAccuracy = accuracyMeters,
                    gpsCapturedAt = now,
                    gpsCapturedBy = capturedByPersonId,
                    gpsUpdatedAt = now,
                )
            )
            auditLogRepository.log(
                actorPersonId = capturedByPersonId,
                action = "CAPTURE_INTERESTED_PERSON_GPS",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = person.name,
            )
        }
    }

    /** Spec §7 — removes latitude/longitude and every related GPS field, not
     * just latitude/longitude alone, so nothing stale (a leftover accuracy or
     * capture timestamp with no coordinates to go with it) survives a clear. */
    fun clearGpsLocation(person: InterestedPerson, actorPersonId: String) {
        viewModelScope.launch {
            interestedPersonRepository.save(
                person.copy(
                    gpsLat = null,
                    gpsLng = null,
                    gpsAccuracy = null,
                    gpsCapturedAt = null,
                    gpsCapturedBy = null,
                    gpsUpdatedAt = null,
                )
            )
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "CLEAR_INTERESTED_PERSON_GPS",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = person.name,
            )
        }
    }

    fun visitsFor(interestedPersonId: String): Flow<List<Visit>> =
        visitRepository.observeForInterestedPerson(interestedPersonId)

    fun startVisitSync(interestedPersonId: String): Flow<Unit> = visitRepository.startRemoteSync(interestedPersonId)

    fun saveVisit(visit: Visit) {
        viewModelScope.launch { visitRepository.save(visit) }
    }

    fun deleteVisit(interestedPersonId: String, visitId: String) {
        viewModelScope.launch { visitRepository.delete(interestedPersonId, visitId) }
    }
}
