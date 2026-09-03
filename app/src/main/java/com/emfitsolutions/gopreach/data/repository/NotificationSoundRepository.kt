package com.emfitsolutions.gopreach.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "gopreach_notification_settings"
private const val KEY_SOUND_URI = "notification_sound_uri"
private const val KEY_ENABLED = "notifications_enabled"

/**
 * Per-device choice of which system sound plays for every incoming
 * notification this app posts — Transfer Request, Announcement, and Calendar
 * Alarm alike all share this one setting (spec: "make a default notification
 * sound to all the incoming notification"), same "own choice on this device
 * only, never synced" pattern as [ThemePreferenceRepository]. Null means "use
 * the system default notification sound" (Android's own default when a
 * channel has no sound explicitly set), not silence — the user picks that by
 * browsing to their phone's own notification sounds via the system ringtone
 * picker (see SettingsScreen), same picker Android's own Settings app uses.
 *
 * Also carries [enabled] — spec: "allow the publisher to turn on and turn off
 * notification" — a single per-device master switch for every notification
 * [com.emfitsolutions.gopreach.notifications.NotificationHelper.notify] posts
 * (Transfer Request, Announcement, report reminders). Calendar Alarms are
 * deliberately unaffected: those ring from an event the Publisher explicitly
 * scheduled themselves (see [com.emfitsolutions.gopreach.notifications
 * .AlarmRingService]), a different mechanism from this on/off switch for
 * *incoming* notifications from other people/the system.
 */
@Singleton
class NotificationSoundRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _soundUri = MutableStateFlow(readStoredUri())
    val soundUri: StateFlow<Uri?> = _soundUri

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled

    private fun readStoredUri(): Uri? = prefs.getString(KEY_SOUND_URI, null)?.let(Uri::parse)

    fun setSoundUri(uri: Uri?) {
        prefs.edit { if (uri == null) remove(KEY_SOUND_URI) else putString(KEY_SOUND_URI, uri.toString()) }
        _soundUri.value = uri
    }

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    companion object {
        /**
         * Static read used by [com.emfitsolutions.gopreach.notifications
         * .NotificationHelper.notify] — that's a plain object (not Hilt-
         * injected, so it can be called from WorkManager/Compose callsites
         * without threading a repository instance through every one of
         * them), so it reads this same SharedPreferences file directly
         * rather than going through the injected [enabled] StateFlow above.
         */
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        /** Same static-read rationale as [isEnabled] — used by
         * [com.emfitsolutions.gopreach.notifications.NotificationHelper
         * .ensureChannel] so a brand-new [REMINDERS_CHANNEL_ID][com.emfitsolutions
         * .gopreach.notifications.REMINDERS_CHANNEL_ID] is created with
         * whichever sound the user already picked, not the system default,
         * if this is a reinstall or the channel was otherwise cleared. */
        fun readStoredSoundUri(context: Context): Uri? =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SOUND_URI, null)?.let(Uri::parse)
    }
}
