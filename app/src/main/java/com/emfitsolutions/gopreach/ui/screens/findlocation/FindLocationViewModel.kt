package com.emfitsolutions.gopreach.ui.screens.findlocation

import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.data.location.LocationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * "Find Location" — a Publisher manually enters a destination's GPS
 * coordinates and gets a same-screen shortcut into Google Maps turn-by-turn
 * directions for it, walking or by any of a few vehicle types (spec request:
 * "he can see the fastest route to go there using different kinds of vehicle
 * or by walking"). Reuses [LocationTracker.reverseGeocode] (already used by
 * Share Location) purely to show the destination as a human-readable address
 * next to the raw coordinates — the actual routing/"fastest route"
 * computation is left to Google Maps itself once launched (see
 * [FindLocationScreen]'s `openDirections`), rather than reimplementing a
 * routing engine with no bundled Directions API key.
 */
@HiltViewModel
class FindLocationViewModel @Inject constructor(
    private val locationTracker: LocationTracker,
) : ViewModel() {
    suspend fun addressFor(lat: Double, lng: Double): String? = locationTracker.reverseGeocode(lat, lng)
}
