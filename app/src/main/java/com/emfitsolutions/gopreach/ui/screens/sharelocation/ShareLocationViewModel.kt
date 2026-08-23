package com.emfitsolutions.gopreach.ui.screens.sharelocation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.SharedLocation
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.SharedLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedLocationRow(val person: Person, val location: SharedLocation)

/** "Share Location – Show Current Coordinates" spec §1 — [capturedAt] is
 * when this specific fix was obtained, so the UI can say "captured just
 * now" rather than only showing raw numbers with no sense of freshness. */
data class MyLocationState(
    val fix: LatLng,
    val capturedAt: Long,
)

/**
 * Spec §6.1 — Share Location. Visibility is role-scoped by the caller (see
 * [rowsFor]'s congregation/group filters); a publisher additionally shares
 * their own live position here while preaching.
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
) : ViewModel() {

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

    fun rowsFor(visibleCongregationId: String?, visibleGroupId: String?, excludePersonId: String): Flow<List<SharedLocationRow>> =
        combine(sharedLocationRepository.observeAll(), personRepository.observeAll()) { locations, people ->
            locations
                .filter { it.isSharing && it.publisherPersonId != excludePersonId }
                .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
                .filter { visibleGroupId == null || it.groupId == visibleGroupId }
                .mapNotNull { location ->
                    val person = people.firstOrNull { it.id == location.publisherPersonId } ?: return@mapNotNull null
                    SharedLocationRow(person, location)
                }
        }

    fun toggleSharing(enabled: Boolean, publisherPersonId: String, congregationId: String?, groupId: String?) {
        _isSharing.value = enabled
        if (enabled) {
            viewModelScope.launch {
                while (isActive && _isSharing.value) {
                    val fix = locationTracker.getCurrentLocation()
                    if (fix != null) {
                        _myLocation.value = MyLocationState(fix, System.currentTimeMillis())
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
