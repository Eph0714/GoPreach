package com.emfitsolutions.gopreach.ui.screens.territories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.model.isCurrentlyFresh
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.SharedLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    val updatedAt: Long,
    /** "Currently sharing" vs. "last known location" — see
     * [com.emfitsolutions.gopreach.data.model.isCurrentlyFresh]'s own doc
     * comment for the shared freshness rule this reads (also used by Share
     * Location's own "who's sharing" list, so the two screens can never
     * disagree about whether a given publisher still counts as live). */
    val isCurrentlySharing: Boolean,
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
    private val groupRepository: GroupRepository,
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
    // "Publisher – All Congregation and Nearest Publisher" — a Bible Study/
    // Return Visit record never reaches here at all (this only ever reads
    // SharedLocation docs, which only a Publisher-track RoleAssignment can
    // ever produce — see ShareLocationViewModel.toggleSharing's own
    // callers), but the Publisher category itself is checked too: a
    // Coordinator Elder who also happens to hold a Publisher RoleAssignment
    // in [REMOVED_PUBLISHER] status (blocked from signing in at all — see
    // that enum's own doc comment) shouldn't be able to show up here even
    // if a stale doc somehow existed.
    //
    // Bug fix ("I cannot see shared location of Publisher in Territory
    // map"): this used to require the *exact* category REGULAR_PUBLISHER,
    // which silently excluded every Pioneer (Regular or Auxiliary) — most
    // of a real congregation's actively-preaching publishers — even though
    // they're genuinely enrolled, active Publishers sharing their real
    // location. Any active Publisher category counts now; only a removed
    // Publisher is excluded.
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
                    if (category == null || category == PublisherCategory.REMOVED_PUBLISHER) return@mapNotNull null
                    val congregationName = congregations.firstOrNull { it.id == location.congregationId }?.name ?: "—"
                    TerritoryPublisherRow(person, location.lat, location.lng, category, congregationName, location.updatedAt, location.isCurrentlyFresh())
                }
        }

    /** "Add the user current location in the map view" — same
     * [LocationTracker] every other GPS-fix spot in the app already uses
     * (Share Location, GPS-coordinate capture forms); the caller checks
     * [hasLocationPermission] first and requests it if false, same pattern
     * as [com.emfitsolutions.gopreach.ui.screens.sharelocation.ShareLocationScreen]. */
    suspend fun currentLocation(): LatLng? = locationTracker.getCurrentLocation()

    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()

    // ---------------------------------------------------------------------
    // "Add a filter in Territory Map" — Search By / Sub Filter / Inner Sub
    // Filter. [congregationIds] is the same `null` = Super-Admin (every
    // congregation) convention every flow above already uses.

    /** Search By "Congregation" dropdown — Super-Admin only (see
     * [TerritoryMapScreen]'s own gating; a scoped role never sees this
     * dropdown at all since they have exactly one congregation already). */
    fun congregationsFor(congregationIds: Set<String>?): Flow<List<Congregation>> =
        congregationRepository.observeAll().map { list ->
            list.filter { it.status == RecordStatus.ACTIVE && (congregationIds == null || it.id in congregationIds) }.sortedBy { it.name }
        }

    /** Search By "Field Service Group" dropdown. */
    fun groupsFor(congregationIds: Set<String>?): Flow<List<Group>> =
        groupRepository.observeAll().map { list ->
            list.filter { it.status == RecordStatus.ACTIVE && (congregationIds == null || it.congregationId in congregationIds) }.sortedBy { it.name }
        }

    /** Search By "Publisher" dropdown — every active Publisher in scope,
     * regardless of category (a Regular/Auxiliary Pioneer's own Bible
     * Studies/Return Visits are just as filterable here as a Regular
     * Publisher's). */
    fun publishersFor(congregationIds: Set<String>?): Flow<List<Person>> =
        combine(roleAssignmentRepository.observeAll(), personRepository.observeAll()) { assignments, people ->
            assignments
                .filter { assignment ->
                    assignment.status == RoleAssignmentStatus.ACTIVE &&
                        (congregationIds == null || assignment.congregationId in congregationIds) &&
                        (assignment.resolvedRoleTypeOrNull() as? RoleType.Publisher)?.let { it.category != PublisherCategory.REMOVED_PUBLISHER } == true
                }
                .mapNotNull { assignment -> people.firstOrNull { it.id == assignment.personId } }
                .distinctBy { it.id }
                .sortedBy { it.fullName }
        }

    /** "Province/City automatic base on their congregation enrollment" — the
     * one congregation a non-Super-Admin role is fixed to; that congregation's
     * own [Congregation.province] anchors their Sub Filter's top level. */
    fun congregationById(congregationId: String): Flow<Congregation?> =
        congregationRepository.observeAll().map { list -> list.firstOrNull { it.id == congregationId } }

    /** Every active Publisher's personId currently assigned to [groupId] —
     * see [applyTerritoryFilter]'s own doc comment for why the Search By
     * "Field Service Group" filter needs this instead of a direct field on
     * [InterestedPerson]. */
    fun groupMemberPublisherIds(groupId: String): Flow<Set<String>> =
        roleAssignmentRepository.observeAll().map { assignments ->
            assignments
                .filter { it.status == RoleAssignmentStatus.ACTIVE && it.groupId == groupId && it.resolvedRoleTypeOrNull() is RoleType.Publisher }
                .map { it.personId }
                .toSet()
        }
}

/** "Search by: All, Congregation, Field Service Group, Publisher" —
 * [CONGREGATION] is offered to Super-Admin only (see [TerritoryMapScreen]'s
 * own role gating); a scoped role's own single congregation is already
 * implicit in every other mode, so it would be a redundant, always-one-
 * option dropdown for them. */
enum class TerritorySearchBy { ALL, CONGREGATION, FIELD_SERVICE_GROUP, PUBLISHER }

/** "Inner Sub Filter: All, Bible Study, Return Visit, Searched Interested" —
 * a direct, named alternative to picking [PipelineStage] by hand; kept as
 * its own enum (rather than reusing [PipelineStage] plus a nullable "ALL")
 * so the UI's exact four labels stay in one place. */
enum class TerritoryInnerFilter(val stage: PipelineStage?) {
    ALL(null),
    BIBLE_STUDY(PipelineStage.BIBLE_STUDY),
    RETURN_VISIT(PipelineStage.RETURN_VISIT),
    SEARCHED_INTERESTED(PipelineStage.SEARCHING),
}

/** The full "Add a filter in Territory Map" state — Search By, then Sub
 * Filter (Province/City -> Municipality -> Barangay, same cascade as
 * [com.emfitsolutions.gopreach.ui.components.PhilippineAddressPicker], just
 * read from data already on each [InterestedPerson] rather than the PSGC
 * lookup table itself), then Inner Sub Filter. Every field left at its
 * default (ALL / null) is a no-op — the unfiltered view is just this default
 * state, never a special case elsewhere. */
data class TerritoryFilterState(
    val searchBy: TerritorySearchBy = TerritorySearchBy.ALL,
    val congregationId: String? = null,
    val groupId: String? = null,
    val publisherPersonId: String? = null,
    val province: String? = null,
    val cityMunicipality: String? = null,
    val barangay: String? = null,
    val innerFilter: TerritoryInnerFilter = TerritoryInnerFilter.ALL,
) {
    val isActive: Boolean get() = this != TerritoryFilterState()
}

/** Applies [filter] to [rows] — the one place Search By/Sub Filter/Inner Sub
 * Filter actually narrow the map, shared by List View and Map View alike so
 * neither can ever show a different result for the same filter. [groupMemberPublisherIds]
 * is precomputed by the caller (every active Publisher currently assigned to
 * [TerritoryFilterState.groupId], via their own RoleAssignment — a Field
 * Service Group has no direct link to an InterestedPerson, only to the
 * Publisher who owns it) since resolving it needs RoleAssignment data this
 * plain function deliberately doesn't take a dependency on. */
fun applyTerritoryFilter(rows: List<TerritoryMapRow>, filter: TerritoryFilterState, groupMemberPublisherIds: Set<String>): List<TerritoryMapRow> {
    var result = rows
    result = when (filter.searchBy) {
        TerritorySearchBy.ALL -> result
        TerritorySearchBy.CONGREGATION -> if (filter.congregationId != null) result.filter { it.person.congregationId == filter.congregationId } else result
        TerritorySearchBy.FIELD_SERVICE_GROUP -> if (filter.groupId != null) result.filter { it.person.publisherPersonId in groupMemberPublisherIds } else result
        TerritorySearchBy.PUBLISHER -> if (filter.publisherPersonId != null) result.filter { it.person.publisherPersonId == filter.publisherPersonId } else result
    }
    if (filter.province != null) result = result.filter { it.person.province == filter.province }
    if (filter.cityMunicipality != null) result = result.filter { it.person.cityMunicipality == filter.cityMunicipality }
    if (filter.barangay != null) result = result.filter { it.person.barangay == filter.barangay }
    val stage = filter.innerFilter.stage
    if (stage != null) result = result.filter { it.person.pipelineStage == stage }
    return result
}
