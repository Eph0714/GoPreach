package com.emfitsolutions.gopreach.ui.screens.sharelocation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.LocationSharingService
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.LocationSharingSettings
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.model.SharedLocation
import com.emfitsolutions.gopreach.data.model.isCurrentlyFresh
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.LocationSharingSettingsRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.SharedLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedLocationRow(
    val person: Person,
    val location: SharedLocation,
    val category: PublisherCategory?,
    val groupName: String?,
    val congregationName: String,
)

/** "Share Location – Show Current Coordinates" spec §1 — [capturedAt] is
 * when this specific fix was obtained, so the UI can say "captured just
 * now" rather than only showing raw numbers with no sense of freshness. */
data class MyLocationState(
    val fix: LatLng,
    val capturedAt: Long,
)

/**
 * Spec §6.1 — Share Location. Visibility is role-scoped by the caller (see
 * [rowsFor]'s congregation filter); a publisher additionally shares their
 * own live position here while preaching.
 *
 * "SHARE LOCATION SETTINGS" spec — the configured per-congregation
 * [LocationSharingSettings.sharingDurationMinutes] (auto-stop) and
 * [LocationSharingSettings.accuracyRadiusMeters] (a fix worse than this is
 * never published) are enforced inside [LocationSharingService] itself, not
 * here — see that class's doc comment for why the actual sharing loop lives
 * in a foreground Service rather than this ViewModel: it needs to keep
 * running after the Publisher leaves this screen.
 */
@HiltViewModel
class ShareLocationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedLocationRepository: SharedLocationRepository,
    private val personRepository: PersonRepository,
    private val locationTracker: LocationTracker,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val groupRepository: GroupRepository,
    private val congregationRepository: CongregationRepository,
    private val locationSharingSettingsRepository: LocationSharingSettingsRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Bug fix: this used to be a plain `MutableStateFlow(false)` local to
     * this ViewModel, which reset to false every time the screen (and this
     * ViewModel) got recreated — so leaving Share Location and reopening it
     * always showed the toggle unchecked, even while sharing was still
     * genuinely active. Now derived from the actual persisted/synced
     * [SharedLocation] doc — the same source of truth every *other*
     * publisher's row on this screen already reads from — so it reflects
     * reality regardless of when or how the screen is reopened. */
    fun isSharingFor(publisherPersonId: String): Flow<Boolean> =
        sharedLocationRepository.observeFor(publisherPersonId).map { it?.isCurrentlyFresh() == true }

    /** "The publisher cannot open Share Location fast, it will take time" —
     * [isSharingFor] only flips true once [LocationSharingService] has
     * actually obtained a fix and written the first doc, which (even with
     * that Service's own speed fix) can still take a few seconds — long
     * enough that the Switch looked unresponsive, like the tap hadn't
     * registered at all. This flips true the instant Start Sharing is
     * tapped (see [toggleSharing]) so the toggle and status card respond
     * immediately, and clears itself the moment the real doc confirms
     * sharing actually started — a purely optimistic, local-only signal,
     * never itself treated as "sharing is really on." */
    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    /** Last coordinates successfully fetched this session — reused by
     * [refreshMyLocation] and updated live from [SharedLocation] rows this
     * device published, so "My Current Location" stays current without a
     * second GPS poll of its own while actively sharing. */
    private val _myLocation = MutableStateFlow<MyLocationState?>(null)
    val myLocation: StateFlow<MyLocationState?> = _myLocation.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()
    fun isLocationServicesEnabled(): Boolean = locationTracker.isLocationServicesEnabled()

    /** [REFRESH LOCATION] — works whether or not Share Location is currently
     * on; a Publisher may just want to see where the device thinks they are.
     * [onComplete] reports success/failure so the caller can show "Unable to
     * update your location. Please try again." — [refreshMyLocation] used to
     * fail completely silently (no fix -> nothing happened, no explanation),
     * exactly the "application fail silently" spec §13 says not to do. */
    fun refreshMyLocation(onComplete: (success: Boolean) -> Unit = {}) {
        _isRefreshing.value = true
        viewModelScope.launch {
            val fix = locationTracker.getCurrentLocation()
            _isRefreshing.value = false
            if (fix != null) {
                _myLocation.value = MyLocationState(fix, System.currentTimeMillis())
            }
            onComplete(fix != null)
        }
    }

    /** Keeps "My Current Location" in sync with whatever [LocationSharingService]
     * just published for this publisher, without a second GPS poll. */
    fun observeOwnSharedLocation(publisherPersonId: String) {
        viewModelScope.launch {
            sharedLocationRepository.observeFor(publisherPersonId).collectLatest { location ->
                if (location != null && location.isSharing) {
                    _myLocation.value = MyLocationState(LatLng(location.lat, location.lng, location.accuracyMeters), location.updatedAt)
                }
            }
        }
    }

    /** "Shared Location Reports" spec — every field the report table shows,
     * enriched from the raw [SharedLocation] doc: the sharer's Publisher
     * category ("Status"), Group name, and Congregation name. Search
     * (name/status/group, plus congregation for a Super-Admin) is applied
     * here too rather than duplicated per caller. */
    fun rowsFor(visibleCongregationId: String?, excludePersonId: String, searchQuery: String): Flow<List<SharedLocationRow>> =
        combine(
            sharedLocationRepository.observeAll(),
            personRepository.observeAll(),
            roleAssignmentRepository.observeAll(),
            groupRepository.observeAll(),
            congregationRepository.observeAll(),
        ) { locations, people, assignments, groups, congregations ->
            locations
                // "Location Sharing = ON... Location data is not expired" —
                // isCurrentlyFresh() checks both the isSharing flag and its
                // own recency, so a publisher whose sharing Service died
                // without writing a clean "stopped" doc drops off this list
                // the same way they already drop off the Territory Map's
                // Publisher layer, rather than lingering as a stale "sharer."
                .filter { it.isCurrentlyFresh() && it.publisherPersonId != excludePersonId }
                .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
                .mapNotNull { location ->
                    val person = people.firstOrNull { it.id == location.publisherPersonId } ?: return@mapNotNull null
                    val category = assignments.firstOrNull {
                        it.personId == person.id && it.congregationId == location.congregationId && it.resolvedRoleTypeOrNull() is RoleType.Publisher
                    }?.let { (it.resolvedRoleTypeOrNull() as RoleType.Publisher).category }
                    val groupName = groups.firstOrNull { it.id == location.groupId }?.name
                    val congregationName = congregations.firstOrNull { it.id == location.congregationId }?.name ?: "—"
                    SharedLocationRow(person, location, category, groupName, congregationName)
                }
                .filter { row ->
                    searchQuery.isBlank() ||
                        row.person.fullName.contains(searchQuery, ignoreCase = true) ||
                        row.category?.name?.replace('_', ' ')?.contains(searchQuery, ignoreCase = true) == true ||
                        row.groupName?.contains(searchQuery, ignoreCase = true) == true ||
                        row.congregationName.contains(searchQuery, ignoreCase = true)
                }
                .sortedBy { it.person.fullName }
        }

    /** Best-effort human-readable address for one coordinate pair — see
     * [LocationTracker.reverseGeocode]'s doc comment for why this can come
     * back null. Deliberately re-resolved on every call rather than cached
     * here: this screen only ever has a handful of active sharers on it at
     * once, and the caller (see AnnouncementsScreen-style `produceState`
     * usage in [ShareLocationScreen]) already only calls this once per row
     * per composition. */
    suspend fun addressFor(lat: Double, lng: Double): String? = locationTracker.reverseGeocode(lat, lng)

    fun settingsFor(congregationId: String): Flow<LocationSharingSettings> = locationSharingSettingsRepository.observeFor(congregationId)

    fun saveSettings(settings: LocationSharingSettings, actorPersonId: String) {
        viewModelScope.launch {
            locationSharingSettingsRepository.save(settings.copy(updatedByPersonId = actorPersonId, updatedAt = System.currentTimeMillis()))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "UPDATE_LOCATION_SHARING_SETTINGS",
                targetType = "LocationSharingSettings",
                targetId = settings.congregationId,
                congregationId = settings.congregationId,
                details = "duration: ${settings.sharingDurationMinutes}min, accuracy: ${settings.accuracyRadiusMeters}m",
            )
        }
    }

    /** Starts/stops [LocationSharingService] — see that class's doc comment
     * for why the actual sharing loop lives there now instead of here.
     * [_isStarting] gives the toggle its instant feedback (see that
     * property's own doc comment) — set the moment Start is tapped, cleared
     * the moment the real doc confirms sharing actually began, or if this
     * ViewModel is torn down (screen closed) before that ever happens. */
    fun toggleSharing(enabled: Boolean, publisherPersonId: String, congregationId: String?, groupId: String?) {
        if (enabled) {
            _isStarting.value = true
            LocationSharingService.start(context, publisherPersonId, congregationId, groupId)
            viewModelScope.launch {
                isSharingFor(publisherPersonId).first { it }
                _isStarting.value = false
            }
        } else {
            _isStarting.value = false
            LocationSharingService.stop(context, publisherPersonId)
        }
    }
}
