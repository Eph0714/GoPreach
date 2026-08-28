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
            serviceScope.launch { stopSharingAndSelf() }
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

        sharingJob?.cancel()
        sharingJob = serviceScope.launch {
            runSharingLoop(publisherPersonId, congregationId, groupId)
            stopSharingAndSelf()
        }
        return START_NOT_STICKY
    }

    /** Same fix-every-30s / accuracy-gate / auto-stop-after-duration logic
     * that used to live in the ViewModel's while loop — unchanged behavior,
     * just running somewhere that survives leaving the screen. */
    private suspend fun runSharingLoop(publisherPersonId: String, congregationId: String?, groupId: String?) {
        val settings = congregationId?.let { locationSharingSettingsRepository.currentFor(it) }
            ?: LocationSharingSettings.defaultsFor(congregationId.orEmpty())
        val durationMillis = settings.sharingDurationMinutes * 60_000L
        val startedAt = System.currentTimeMillis()

        while (serviceScope.isActive) {
            if (System.currentTimeMillis() - startedAt >= durationMillis) return
            val fix = runCatching { locationTracker.getCurrentLocation() }.getOrNull()
            if (fix != null) {
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
                    runCatching { sharedLocationRepository.update(location) }
                        .onFailure { Log.e(TAG, "Failed to publish shared location", it) }
                }
            }
            delay(30_000)
        }
    }

    /** Awaits the stop-sharing write before tearing anything down, so it
     * never races [onDestroy]'s `serviceScope.cancel()` — a cancelled scope
     * would otherwise abandon this write mid-flight and leave the doc
     * showing `isSharing = true` forever (which is exactly the "someone's
     * location is still showing as shared even though they stopped" failure
     * mode this needs to avoid). */
    private suspend fun stopSharingAndSelf() {
        sharingJob?.cancel()
        val toStop = lastPublished
        if (toStop != null) {
            runCatching { sharedLocationRepository.stopSharing(toStop.publisherPersonId, toStop) }
                .onFailure { Log.e(TAG, "Failed to persist stop-sharing", it) }
        }
        _isRunning.value = false
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

    companion object {
        private val _isRunning = MutableStateFlow(false)
        /** Immediate (no Room round-trip) "is the service itself alive right
         * now" signal — mainly useful for a snappier UI right after tapping
         * the toggle; [SharedLocationRepository.observeFor] is still the
         * authoritative source of truth once this settles. */
        val isRunning: StateFlow<Boolean> = _isRunning

        fun start(context: Context, publisherPersonId: String, congregationId: String?, groupId: String?) {
            val intent = Intent(context, LocationSharingService::class.java).apply {
                putExtra(EXTRA_PUBLISHER_PERSON_ID, publisherPersonId)
                putExtra(EXTRA_CONGREGATION_ID, congregationId)
                putExtra(EXTRA_GROUP_ID, groupId)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LocationSharingService::class.java).setAction(ACTION_STOP))
        }
    }
}
