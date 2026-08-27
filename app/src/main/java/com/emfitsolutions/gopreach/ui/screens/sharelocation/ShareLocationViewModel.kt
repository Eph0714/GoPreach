package com.emfitsolutions.gopreach.ui.screens.sharelocation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.LocationSharingSettings
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.model.SharedLocation
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.LocationSharingSettingsRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.SharedLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 * "SHARE LOCATION SETTINGS" spec — [toggleSharing] enforces the configured
 * per-congregation [LocationSharingSettings.sharingDurationMinutes] (auto-
 * stop) and [LocationSharingSettings.accuracyRadiusMeters] (a fix worse than
 * this is never published — the old, still-accurate-enough position stays
 * shown until a good one arrives).
 *
 * Updates happen on a foreground timer while this screen is open, rather than a
 * background/foreground service — a reasonable first pass for "share while
 * preaching," with continuous background tracking as a natural follow-up.
 */
@HiltViewModel
class ShareLocationViewModel @Inject constructor(
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

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing

    /** Last coordinates successfully fetched this session — reused by
     * [toggleSharing]'s "stop" path so turning sharing off doesn't clobber the
     * last-known position with a zeroed-out one. */
    private var lastFix: SharedLocation? = null

    /** "Share Location – Show Current Coordinates" spec §1 — the signed-in
     * user's own current position, independent of whether they're actively
     * *sharing* it with anyone (those are two different questions: "where am
     * I" vs. "is my group allowed to see where I am"). Populated by
     * [refreshMyLocation] and, while actively sharing, by every tick of the
     * same fix [toggleSharing]'s loop already captures — no second GPS poll
     * needed just to keep this in sync. */
    private val _myLocation = MutableStateFlow<MyLocationState?>(null)
    val myLocation: StateFlow<MyLocationState?> = _myLocation.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()

    /** [REFRESH LOCATION] — works whether or not Share Location is currently
     * on; a Publisher may just want to see where the device thinks they are. */
    fun refreshMyLocation() {
        _isRefreshing.value = true
        viewModelScope.launch {
            val fix = locationTracker.getCurrentLocation()
            _isRefreshing.value = false
            if (fix != null) _myLocation.value = MyLocationState(fix, System.currentTimeMillis())
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
                .filter { it.isSharing && it.publisherPersonId != excludePersonId }
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

    fun toggleSharing(enabled: Boolean, publisherPersonId: String, congregationId: String?, groupId: String?) {
        _isSharing.value = enabled
        if (enabled) {
            viewModelScope.launch {
                val settings = congregationId?.let { locationSharingSettingsRepository.currentFor(it) }
                    ?: LocationSharingSettings.defaultsFor(congregationId.orEmpty())
                val durationMillis = settings.sharingDurationMinutes * 60_000L
                val startedAt = System.currentTimeMillis()
                while (isActive && _isSharing.value) {
                    if (System.currentTimeMillis() - startedAt >= durationMillis) {
                        // "The publisher can share their location [N] min only
                        // and it will automatically stop" — the configured
                        // window elapsed; stop exactly like the manual toggle
                        // does, so the UI (bound to isSharing) reflects it.
                        toggleSharing(false, publisherPersonId, congregationId, groupId)
                        break
                    }
                    val fix = locationTracker.getCurrentLocation()
                    if (fix != null) {
                        _myLocation.value = MyLocationState(fix, System.currentTimeMillis())
                        // "Accuracy Radius: 5 mtrs" — a fix worse than the
                        // configured radius is never published; the
                        // previously-shared (still valid) position stays
                        // visible to others until a good-enough fix arrives.
                        val meetsAccuracy = fix.accuracyMeters == null || fix.accuracyMeters <= settings.accuracyRadiusMeters
                        if (meetsAccuracy) {
                            val location = SharedLocation(
                                publisherPersonId = publisherPersonId,
                                congregationId = congregationId.orEmpty(),
                                groupId = groupId,
                                lat = fix.lat,
                                lng = fix.lng,
                                accuracyMeters = fix.accuracyMeters,
                                isSharing = true,
                                updatedAt = System.currentTimeMillis(),
                            )
                            lastFix = location
                            sharedLocationRepository.update(location)
                        }
                    }
                    delay(30_000)
                }
            }
        } else {
            viewModelScope.launch {
                val fallback = SharedLocation(publisherPersonId = publisherPersonId, congregationId = congregationId.orEmpty(), groupId = groupId)
                sharedLocationRepository.stopSharing(publisherPersonId, lastFix ?: fallback)
            }
        }
    }
}
