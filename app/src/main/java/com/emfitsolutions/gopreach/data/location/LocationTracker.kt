package com.emfitsolutions.gopreach.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LatLng(val lat: Double, val lng: Double, val accuracyMeters: Float?)

/**
 * Thin wrapper over Play Services' fused location provider — used by Share
 * Location (spec §6.1) and available for GPS-coordinate capture on Publisher/
 * Bible-study/Interested-person forms (spec §7 open question: capture accuracy
 * threshold + weak-signal fallback are left as a follow-up product decision;
 * this surfaces `accuracyMeters` so a caller can apply one).
 */
@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "GPS is currently disabled. Please enable location services." — a
     * separate question from [hasLocationPermission]: the app can be fully
     * permitted and the device's location services (GPS/network provider)
     * still switched off entirely (airplane mode's location-off toggle,
     * device settings, a work profile restriction). [getCurrentLocation]
     * returning null on its own can't distinguish "no fix yet" from "no
     * provider is even on," so callers check this first and give the
     * specific message instead of a generic failure. */
    fun isLocationServicesEnabled(): Boolean =
        LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

    @SuppressLint("MissingPermission") // caller checks hasLocationPermission() first
    suspend fun getCurrentLocation(): LatLng? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        // PRIORITY_HIGH_ACCURACY, not BALANCED_POWER_ACCURACY — the latter
        // is free to answer from WiFi/cell towers alone (often 50-100+
        // meters of error), which combined with Share Location's own
        // accuracy-radius filter (see LocationSharingSettings, default 5m)
        // meant a fix almost never met the threshold and "Share Location"
        // silently never actually published anything. HIGH_ACCURACY asks
        // for a real GPS-chip fix (typically single-digit-to-low-tens of
        // meters outdoors), which is what that filter was written assuming.
        val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()
        val location = client.getCurrentLocation(request, null).await() ?: return null
        return LatLng(location.latitude, location.longitude, location.accuracy)
    }

    /** "The publisher cannot open Share Location fast, it will take time" —
     * a fresh [getCurrentLocation] fix genuinely can take many seconds
     * (sometimes tens of seconds indoors on a cold GPS chip), since
     * PRIORITY_HIGH_ACCURACY deliberately forces a real new fix rather than
     * answering from cache. Play Services keeps its *own* rolling cache of
     * the device's last fix across every app that's asked recently, usually
     * only seconds old outdoors — [LocationSharingService] tries this first
     * so sharing can visibly turn on immediately, then upgrades to a fresh
     * [getCurrentLocation] fix on its very next cycle regardless. */
    @SuppressLint("MissingPermission") // caller checks hasLocationPermission() first
    suspend fun getLastKnownLocation(): LatLng? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = client.lastLocation.await() ?: return null
        return LatLng(location.latitude, location.longitude, location.accuracy)
    }

    /** "Shared Location Reports" spec — the human-readable "Location" line
     * next to each record's raw coordinates, via Android's own on-device
     * [Geocoder] (no API key/new dependency, same "no new dependency"
     * philosophy this app already follows elsewhere). Returns null on any
     * failure (no network, no geocoder backend on this device, nothing
     * found) — callers show the coordinates alone in that case rather than
     * blocking on an address that may never resolve. */
    suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        cont.resume(addresses.firstOrNull()?.getAddressLine(0))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
            }
        }.getOrNull()
    }

    /** Forward geocoding — "Find Location" spec: "the textbox will search a
     * coordinates or address, not just the latitude and longitude." Same
     * on-device [Geocoder] as [reverseGeocode] (no API key/new dependency),
     * just run the other direction. Returns the first/best match, or null on
     * any failure (no network, no geocoder backend on this device, nothing
     * found for that text) — the caller falls back to its own "couldn't find
     * that" error rather than assuming this always resolves. */
    suspend fun geocodeAddress(query: String): LatLng? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 1) { addresses -> cont.resume(addresses.firstOrNull()) }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 1)?.firstOrNull()
            }
            address?.let { LatLng(it.latitude, it.longitude, accuracyMeters = null) }
        }.getOrNull()
    }
}
