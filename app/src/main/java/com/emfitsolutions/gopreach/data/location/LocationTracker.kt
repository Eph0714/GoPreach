package com.emfitsolutions.gopreach.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

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

    @SuppressLint("MissingPermission") // caller checks hasLocationPermission() first
    suspend fun getCurrentLocation(): LatLng? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY).build()
        val location = client.getCurrentLocation(request, null).await() ?: return null
        return LatLng(location.latitude, location.longitude, location.accuracy)
    }
}
