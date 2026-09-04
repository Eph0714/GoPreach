package com.emfitsolutions.gopreach.ui.screens.territories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.SharedLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

/** One row on the Territory Map — every Searching/Return Visit/Bible Study
 * record that has a saved GPS location, regardless of which of the three
 * stages it's currently at. [resolvedLocation] is the on-device reverse-
 * geocoded address for [InterestedPerson.gpsLat]/[gpsLng] (spec's own
 * example: "Location: F5M2+57Q, 1, Bayombong, Nueva Vizcaya" — a real
 * street/Plus-Code address, not the raw coordinate pair) — null while it's
 * still resolving, empty once resolved with nothing found (falls back to
 * the raw coordinates in that case; see [TerritoryMapScreen]). */
data class TerritoryMapRow(
    val person: InterestedPerson,
    val congregationName: String,
    val resolvedLocation: String?,
)

/** One publisher currently sharing their live location ("Share Location
 * while Preaching") plotted on the Territory Map alongside the pipeline
 * records — same underlying [com.emfitsolutions.gopreach.data.model.SharedLocation]
 * doc [com.emfitsolutions.gopreach.ui.screens.sharelocation.ShareLocationViewModel.rowsFor]
 * already reads, filtered by [publisherRowsFor]'s own congregation scope
 * rather than duplicated. */
data class TerritoryPublisherRow(
    val person: Person,
    val lat: Double,
    val lng: Double,
    val category: PublisherCategory?,
    val congregationName: String,
)

/**
 * "The Territory Module will be a map of location of every Search,
 * Interested, Return Visit, Bible Study [record]" — replaces the old
 * Territory Master File CRUD (create/edit/delete a Territory entity) with a
 * read-only directory of every InterestedPerson record (any of the three
 * pipeline stages) that has a saved GPS location, searchable by name or
 * location.
 */
@HiltViewModel
class TerritoryMapViewModel @Inject constructor(
    private val interestedPersonRepository: InterestedPersonRepository,
    private val congregationRepository: CongregationRepository,
    private val locationTracker: LocationTracker,
    private val sharedLocationRepository: SharedLocationRepository,
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
) : ViewModel() {

    private val _resolvedAddresses = MutableStateFlow<Map<String, String>>(emptyMap())

    // Guards against re-launching a reverse-geocode lookup for the same
    // person on every recomposition of the combine below (a plain "is it in
    // the resolved map yet" check alone would re-fire once per person for
    // every recombination while the very first lookup is still in flight,
    // since none of those in-flight lookups have written back yet) — a
    // synchronized Set since this is read/written from whichever dispatcher
    // the combine happens to run on plus every launched lookup coroutine.
    private val requestedIds = Collections.synchronizedSet(mutableSetOf<String>())

    /** [congregationId] null means every congregation (Super-Admin). Only
     * [RecordStatus.ACTIVE] records with a saved location are shown — same
     * "active records only" convention [FindLocationViewModel.recordsFor]
     * already uses for its own record picker. */
    fun rowsFor(congregationId: String?): Flow<List<TerritoryMapRow>> =
        combine(
            interestedPersonRepository.observeAll(),
            congregationRepository.observeAll(),
            _resolvedAddresses,
        ) { people, congregations, resolved ->
            val withLocation = people
                .filter { it.status == RecordStatus.ACTIVE && it.hasGpsLocation }
                .filter { congregationId == null || it.congregationId == congregationId }

            withLocation.forEach { person ->
                if (resolved[person.id] == null && requestedIds.add(person.id)) {
                    viewModelScope.launch {
                        val address = runCatching { locationTracker.reverseGeocode(person.gpsLat!!, person.gpsLng!!) }.getOrNull()
                        _resolvedAddresses.update { it + (person.id to address.orEmpty()) }
                    }
                }
            }

            withLocation
                .map { person ->
                    TerritoryMapRow(
                        person = person,
                        congregationName = congregations.firstOrNull { it.id == person.congregationId }?.name ?: "—",
                        resolvedLocation = resolved[person.id]?.ifBlank { null },
                    )
                }
                .sortedBy { it.person.name }
        }

    /** "For publisher account they can see other publishers that share their
     * location in the map. For admin, Coordinator Elder, Service overseer
     * can do so. However the super admin can see all congregation" — same
     * `isSharing` [com.emfitsolutions.gopreach.data.model.SharedLocation]
     * docs and congregation scoping [com.emfitsolutions.gopreach.ui.screens
     * .sharelocation.ShareLocationViewModel.rowsFor] already uses (see that
     * function's own doc comment); [congregationId] null means every
     * congregation (Super-Admin), same convention as [rowsFor]. Whether the
     * *caller* is even allowed to ask for this at all is a navigation-level
     * concern (see [com.emfitsolutions.gopreach.ui.navigation.GoPreachNavGraph]'s
     * `canSeePublisherLocations`), not enforced here. */
    fun publisherRowsFor(congregationId: String?, excludePersonId: String): Flow<List<TerritoryPublisherRow>> =
        combine(
            sharedLocationRepository.observeAll(),
            personRepository.observeAll(),
            roleAssignmentRepository.observeAll(),
            congregationRepository.observeAll(),
        ) { locations, people, assignments, congregations ->
            locations
                .filter { it.isSharing && it.publisherPersonId != excludePersonId }
                .filter { congregationId == null || it.congregationId == congregationId }
                .mapNotNull { location ->
                    val person = people.firstOrNull { it.id == location.publisherPersonId } ?: return@mapNotNull null
                    val category = assignments.firstOrNull {
                        it.personId == person.id && it.congregationId == location.congregationId && it.resolvedRoleTypeOrNull() is RoleType.Publisher
                    }?.let { (it.resolvedRoleTypeOrNull() as RoleType.Publisher).category }
                    val congregationName = congregations.firstOrNull { it.id == location.congregationId }?.name ?: "—"
                    TerritoryPublisherRow(person, location.lat, location.lng, category, congregationName)
                }
        }

    /** "Add the user current location in the map view" — same
     * [LocationTracker] every other GPS-fix spot in the app already uses
     * (Share Location, GPS-coordinate capture forms); the caller checks
     * [hasLocationPermission] first and requests it if false, same pattern
     * as [com.emfitsolutions.gopreach.ui.screens.sharelocation.ShareLocationScreen]. */
    suspend fun currentLocation(): LatLng? = locationTracker.getCurrentLocation()

    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()
}
