package com.emfitsolutions.gopreach.data.location

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.emfitsolutions.gopreach.MainActivity
import com.emfitsolutions.gopreach.R
import com.emfitsolutions.gopreach.data.model.LocationSharingSettings
import com.emfitsolutions.gopreach.data.model.SharedLocation
import com.emfitsolutions.gopreach.data.repository.LocationSharingSettingsRepository
import com.emfitsolutions.gopreach.data.repository.SharedLocationRepository
import com.emfitsolutions.gopreach.notifications.LOCATION_SHARING_CHANNEL_ID
import com.emfitsolutions.gopreach.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "LocationSharingService"
private const val NOTIFICATION_ID = 9600
private const val ACTION_STOP = "com.emfitsolutions.gopreach.action.STOP_SHARING"
private const val EXTRA_PUBLISHER_PERSON_ID = "publisherPersonId"
private const val EXTRA_CONGREGATION_ID = "congregationId"
private const val EXTRA_GROUP_ID = "groupId"

/**
 * "Share Location while Preaching" actually needs to keep publishing fixes
 * even after the Publisher leaves the Share Location screen — that's the
 * whole point of "while preaching," not "while this one screen happens to
 * stay open." Bug fix: this used to be a `while` loop inside
 * [com.emfitsolutions.gopreach.ui.screens.sharelocation.ShareLocationViewModel]
 * running in `viewModelScope`, which gets cancelled the moment that
 * ViewModel is torn down — i.e. the instant the user backed out of the
 * screen ("share location is still closing after closing the share
 * location module"). A foreground Service survives exactly that.
 *
 * Started with [start], stopped either by [stop] (the in-app toggle) or by
 * tapping "Stop Sharing" on the ongoing notification (redelivers here with
 * [ACTION_STOP]) or by the configured duration elapsing on its own.
 */
@AndroidEntryPoint
class LocationSharingService : Service() {

    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var sharedLocationRepository: SharedLocationRepository
    @Inject lateinit var locationSharingSettingsRepository: LocationSharingSettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var sharingJob: Job? = null
    private var lastPublished: SharedLocation? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Bug fix ("Share location cannot be turned off"): this used to
            // rely solely on [lastPublished], an in-memory field that only
            // this exact Service *instance* ever sets — while it's actually
            // running the sharing loop below. Stop is delivered via
            // `startService()`, which Android is free to satisfy by cold-
            // starting a brand-new instance of this Service (e.g. the
            // previous one already died — process killed, doze, the OS
            // reclaiming it — while the Firestore doc it last wrote was
            // still `isSharing = true`); that new instance's [lastPublished]
            // is null, so the old "only write if lastPublished != null"
            // stop path silently did nothing and the doc stayed stuck
            // showing as sharing forever, no matter how many times the
            // Publisher tapped the toggle off. Now the publisher id always
            // rides along with the stop request itself, so stopping never
            // depends on this being the same instance that started sharing.
            val publisherPersonId = intent.getStringExtra(EXTRA_PUBLISHER_PERSON_ID)
            serviceScope.launch { stopSharingAndSelf(publisherPersonId) }
            return START_NOT_STICKY
        }

        val publisherPersonId = intent?.getStringExtra(EXTRA_PUBLISHER_PERSON_ID)
        if (publisherPersonId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val congregationId = intent.getStringExtra(EXTRA_CONGREGATION_ID)
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID)

        NotificationHelper.ensureChannel(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        _isRunning.value = true
        // "Sharing Activated" vs. "Location Acquired" vs. "Location
        // Synchronized" are different events — reset both for this fresh
        // session so a screen reopened mid-share doesn't show stale
        // "Synced"/"acquired" state left over from a previous share.
        _locationAcquired.value = false
        _syncState.value = LocationSyncState.CONNECTING

        sharingJob?.cancel()
        sharingJob = serviceScope.launch {
            runSharingLoop(publisherPersonId, congregationId, groupId)
            stopSharingAndSelf(publisherPersonId)
        }
        return START_NOT_STICKY
    }

    /** Same fix-every-30s / accuracy-gate / auto-stop-after-duration logic
     * that used to live in the ViewModel's while loop — unchanged behavior,
     * just running somewhere that survives leaving the screen.
     *
     * Bug fix ("the publisher cannot open Share Location fast, it will take
     * time"): two separate things used to make the very first publish slow
     * even once the toggle was tapped —
     *   1. Every attempt, including the first, called [LocationTracker
     *      .getCurrentLocation] alone, which forces Play Services to obtain
     *      a genuinely *new* PRIORITY_HIGH_ACCURACY GPS fix rather than
     *      answering from any cache — commonly several seconds, sometimes
     *      tens of seconds indoors on a cold GPS chip.
     *   2. A first fix that failed the accuracy gate (very plausible — a
     *      fresh chip's first fix is often worse than [LocationSharingSettings
     *      .accuracyRadiusMeters]'s tight default) published nothing and
     *      then waited the *full* 30-second cadence before trying again,
     *      compounding into a genuinely long wait before the Publisher's
     *      toggle/status ever reflected reality.
     * Fixed by trying Play Services' own already-cached last-known fix
     * first (near-instant when available) before ever falling back to a
     * fresh one, and retrying every 5s instead of 30s until the very first
     * publish actually succeeds — settling into the normal 30s cadence only
     * once sharing is genuinely up and running. */
    private suspend fun runSharingLoop(publisherPersonId: String, congregationId: String?, groupId: String?) {
        val settings = congregationId?.let { locationSharingSettingsRepository.currentFor(it) }
            ?: LocationSharingSettings.defaultsFor(congregationId.orEmpty())
        val durationMillis = settings.sharingDurationMinutes * 60_000L
        val startedAt = System.currentTimeMillis()
        var hasPublishedOnce = false
        var isFirstAttempt = true

        while (serviceScope.isActive) {
            if (System.currentTimeMillis() - startedAt >= durationMillis) return
            val fix = if (isFirstAttempt) {
                runCatching { locationTracker.getLastKnownLocation() }.getOrNull()
                    ?: runCatching { locationTracker.getCurrentLocation() }.getOrNull()
            } else {
                runCatching { locationTracker.getCurrentLocation() }.getOrNull()
            }
            isFirstAttempt = false
            if (fix != null) {
                // "Location Acquired" — a valid GPS fix, distinct from
                // whether it met the accuracy gate or ever reached the
                // server; §7's own three-state example ("📍 Location:
                // Updating...") only needs a fix to exist on-device.
                _locationAcquired.value = true
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
                    lastPublished = location
                    // "Location Synchronized" — whether this specific fix
                    // actually reached Firestore, tracked separately so the
                    // UI can say "Waiting for network synchronization"
                    // instead of falsely claiming a sync that never
                    // happened (§5/§7). A failure here doesn't stop the
                    // loop — the next 5s/30s cycle retries automatically
                    // (§6 "keep retrying... do not silently fail").
                    runCatching { sharedLocationRepository.update(location) }
                        .onSuccess { _syncState.value = LocationSyncState.SYNCED }
                        .onFailure {
                            _syncState.value = LocationSyncState.FAILED
                            Log.e(TAG, "Failed to publish shared location", it)
                        }
                    hasPublishedOnce = true
                }
            }
            delay(if (hasPublishedOnce) 30_000 else 5_000)
        }
    }

    /** Awaits the stop-sharing write before tearing anything down, so it
     * never races [onDestroy]'s `serviceScope.cancel()` — a cancelled scope
     * would otherwise abandon this write mid-flight and leave the doc
     * showing `isSharing = true` forever (which is exactly the "someone's
     * location is still showing as shared even though they stopped" failure
     * mode this needs to avoid). [publisherPersonId] is who to stop sharing
     * for — passed in explicitly (see [onStartCommand]'s [ACTION_STOP]
     * branch) rather than trusted to always be this instance's own
     * [lastPublished], which a freshly (re)started instance never has. */
    private suspend fun stopSharingAndSelf(publisherPersonId: String? = null) {
        sharingJob?.cancel()
        val toStop = lastPublished
            ?: publisherPersonId?.let { id ->
                runCatching { sharedLocationRepository.observeFor(id).first() }.getOrNull()
            }
        if (toStop != null && toStop.isSharing) {
            runCatching { sharedLocationRepository.stopSharing(toStop.publisherPersonId, toStop) }
                .onFailure { Log.e(TAG, "Failed to persist stop-sharing", it) }
        }
        _isRunning.value = false
        _locationAcquired.value = false
        _syncState.value = LocationSyncState.CONNECTING
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val stopPendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, LocationSharingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, LOCATION_SHARING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sharing Your Location")
            .setContentText("Other publishers in your congregation can see where you are while preaching.")
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Stop Sharing", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    /** "Clearly distinguish between... Sharing Activated / Location
     * Acquired / Location Synchronized — these are different states and
     * should not be incorrectly treated as the same event." */
    enum class LocationSyncState { CONNECTING, SYNCED, FAILED }

    companion object {
        private val _isRunning = MutableStateFlow(false)
        /** Immediate (no Room round-trip) "is the service itself alive right
         * now" signal — mainly useful for a snappier UI right after tapping
         * the toggle; [SharedLocationRepository.observeFor] is still the
         * authoritative source of truth once this settles. */
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _locationAcquired = MutableStateFlow(false)
        /** "Location Acquired" — a valid GPS fix obtained since the current
         * sharing session started, independent of whether it met the
         * accuracy gate or reached the server yet. Only ever meaningful
         * while [isRunning] is true; reset false the moment a session starts
         * or stops. Process-wide rather than per-publisher — this Service
         * only ever runs for the signed-in device's own share, never
         * someone else's, so there's exactly one "my own sharing session"
         * to track at a time. */
        val locationAcquired: StateFlow<Boolean> = _locationAcquired

        private val _syncState = MutableStateFlow(LocationSyncState.CONNECTING)
        /** "Location Synchronized" — whether the most recent fix actually
         * reached Firestore, distinct from [locationAcquired] (obtained on
         * the device) and [isRunning] (the sharing session itself). */
        val syncState: StateFlow<LocationSyncState> = _syncState

        fun start(context: Context, publisherPersonId: String, congregationId: String?, groupId: String?) {
            val intent = Intent(context, LocationSharingService::class.java).apply {
                putExtra(EXTRA_PUBLISHER_PERSON_ID, publisherPersonId)
                putExtra(EXTRA_CONGREGATION_ID, congregationId)
                putExtra(EXTRA_GROUP_ID, groupId)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        /** [publisherPersonId] rides along on the stop intent itself now
         * (bug fix — see [onStartCommand]'s [ACTION_STOP] doc comment) so a
         * cold-started instance handling this stop can still find and clear
         * the right [SharedLocation] doc, even if it never ran the sharing
         * loop itself. */
        fun stop(context: Context, publisherPersonId: String) {
            context.startService(
                Intent(context, LocationSharingService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_PUBLISHER_PERSON_ID, publisherPersonId),
            )
        }
    }
}
