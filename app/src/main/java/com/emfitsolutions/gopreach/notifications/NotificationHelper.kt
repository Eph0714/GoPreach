package com.emfitsolutions.gopreach.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.emfitsolutions.gopreach.R
import com.emfitsolutions.gopreach.data.repository.NotificationSoundRepository

// "_v2" — bug fix ("notification sound isn't working"): a NotificationChannel's
// sound/importance is immutable once created (Android ignores every later
// createNotificationChannel call for the same id), so any device whose
// channel got created before this file's sound-handling existed (or with a
// different sound) was permanently stuck, regardless of what the code below
// says or what the user picks in Settings. Renaming forces a fresh channel —
// with today's correct settings — on every device, old installs included.
const val REMINDERS_CHANNEL_ID = "gopreach_reminders_v2"
const val CALENDAR_ALARM_CHANNEL_ID = "gopreach_calendar_alarms"
const val LOCATION_SHARING_CHANNEL_ID = "gopreach_location_sharing"

/**
 * Local-only notifications (no push backend yet — see BUILD_PLAN.md's
 * "Redesign the Publisher Dashboard" phase note): Monthly Report reminders
 * ([com.emfitsolutions.gopreach.data.sync.ReminderWorker], a periodic
 * WorkManager check), "Transfer/Forward" alerts (fired in-process — see
 * [com.emfitsolutions.gopreach.ui.screens.pipeline.ForwardRequestsViewModel]
 * — the moment a new pending request streams in while this app is running),
 * new-Announcement alerts, and Calendar Alarms (see
 * [com.emfitsolutions.gopreach.notifications.AlarmScheduler]). Every call
 * here is a no-op if the user hasn't granted POST_NOTIFICATIONS (Android
 * 13+) — never crashes, never nags twice for the same permission in one call.
 *
 * Two channels: [REMINDERS_CHANNEL_ID] carries every one-shot notification
 * above (Transfer Request, Announcement, report reminders) and plays
 * whichever sound the user picked in Settings (see [applySoundPreference]) —
 * a brand-new channel is created with that sound explicitly set (never left
 * to chance) so "make a default notification sound to all the incoming
 * notification" holds from the very first notification, not just after the
 * user opens Settings once; [CALENDAR_ALARM_CHANNEL_ID] backs the ringing
 * Calendar Alarm notification itself and is deliberately silent —
 * [AlarmRingService] plays that same user-picked sound on a loop until
 * stopped, so the channel doesn't also play it once on top.
 *
 * [notify] also honors the Publisher-facing on/off switch (spec: "allow the
 * publisher to turn on and turn off notification") — see
 * [NotificationSoundRepository.isEnabled]. That switch does not affect
 * Calendar Alarms, which ring from an event the user scheduled themselves
 * through a different mechanism entirely (see [AlarmScheduler]).
 */
object NotificationHelper {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(REMINDERS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(buildRemindersChannel(NotificationSoundRepository.readStoredSoundUri(context)))
        }
        if (manager.getNotificationChannel(CALENDAR_ALARM_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CALENDAR_ALARM_CHANNEL_ID, "Calendar Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Calendar event alarms"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        if (manager.getNotificationChannel(LOCATION_SHARING_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(LOCATION_SHARING_CHANNEL_ID, "Location Sharing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing status while your location is being shared"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    /**
     * Recreates [REMINDERS_CHANNEL_ID] with [soundUri] — Android ignores a
     * NotificationChannel's sound/importance/etc. after it's first created,
     * so "let the user change the notification sound" (spec) requires
     * deleting and recreating the channel, not just calling
     * createNotificationChannel again with different settings. [soundUri]
     * null restores the system default notification sound. Only called when
     * the user actually changes their pick in Settings (not on every app
     * start) so a user's own further tweaks to this channel via the system
     * Settings app aren't silently reset on every launch.
     */
    fun applySoundPreference(context: Context, soundUri: Uri?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(REMINDERS_CHANNEL_ID)
        manager.createNotificationChannel(buildRemindersChannel(soundUri))
    }

    /** [soundUri] null still gets an explicit sound — Android's own default
     * notification sound — rather than leaving the channel's sound unset, so
     * every notification through [REMINDERS_CHANNEL_ID] reliably makes a
     * sound (spec: "make a sound to all notification") regardless of OEM
     * quirks around a channel's implicit default. */
    private fun buildRemindersChannel(soundUri: Uri?): NotificationChannel =
        NotificationChannel(REMINDERS_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Monthly report reminders, transfer requests, and announcements"
            setSound(
                soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

    /** Posts a local notification through [REMINDERS_CHANNEL_ID] — a no-op
     * if the Publisher has turned notifications off (spec: "allow the
     * publisher to turn on and turn off notification"), same as it's
     * already a no-op without POST_NOTIFICATIONS permission. */
    fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationSoundRepository.isEnabled(context)) return
        val builder = NotificationCompat.Builder(context, REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // Below API 26 there is no NotificationChannel at all, so the sound
        // set on [buildRemindersChannel] never applies — bug fix: the sound
        // (and default vibration) has to be set directly on the notification
        // itself for it to play anything on Android 7.0/7.1. Harmless to set
        // on O+ too; the channel's own sound simply takes priority there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val soundUri = NotificationSoundRepository.readStoredSoundUri(context)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri).setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }
        val notification = builder.build()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
