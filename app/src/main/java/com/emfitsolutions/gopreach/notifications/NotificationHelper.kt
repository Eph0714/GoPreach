package com.emfitsolutions.gopreach.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.emfitsolutions.gopreach.R

const val REMINDERS_CHANNEL_ID = "gopreach_reminders"
const val CALENDAR_ALARM_CHANNEL_ID = "gopreach_calendar_alarms"

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
 * whichever sound the user picked in Settings (see [applySoundPreference]);
 * [CALENDAR_ALARM_CHANNEL_ID] backs the ringing Calendar Alarm notification
 * itself and is deliberately silent — [AlarmRingService] plays that same
 * user-picked sound on a loop until stopped, so the channel doesn't also
 * play it once on top.
 */
object NotificationHelper {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(REMINDERS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(REMINDERS_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Monthly report reminders, transfer requests, and announcements"
                }
            )
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
        manager.createNotificationChannel(
            NotificationChannel(REMINDERS_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Monthly report reminders, transfer requests, and announcements"
                if (soundUri != null) {
                    setSound(
                        soundUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                }
            }
        )
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
