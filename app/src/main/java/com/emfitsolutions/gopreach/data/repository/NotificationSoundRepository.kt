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
private const val KEY_SOUND_ENABLED = "notification_sounds_enabled"
private const val KEY_POPUP_ENABLED = "notification_popups_enabled"
private const val KEY_TRANSFER_REQUEST_ENABLED = "notification_transfer_requests_enabled"
private const val KEY_ANNOUNCEMENT_ENABLED = "notification_announcements_enabled"

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

    // "NOTIFICATION SETTINGS: Enable Notification Sounds / Transfer Request
    // Notifications / Announcement Notifications / Show Popup Notifications"
    // — four additional, independent per-device toggles layered on top of
    // [enabled] (the pre-existing overall on/off switch, unchanged, still
    // gates every one of these). Each defaults to true so nothing changes
    // for a user who's never opened this new section of Settings.
    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ENABLED, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled

    private val _popupEnabled = MutableStateFlow(prefs.getBoolean(KEY_POPUP_ENABLED, true))
    val popupEnabled: StateFlow<Boolean> = _popupEnabled

    private val _transferRequestEnabled = MutableStateFlow(prefs.getBoolean(KEY_TRANSFER_REQUEST_ENABLED, true))
    val transferRequestEnabled: StateFlow<Boolean> = _transferRequestEnabled

    private val _announcementEnabled = MutableStateFlow(prefs.getBoolean(KEY_ANNOUNCEMENT_ENABLED, true))
    val announcementEnabled: StateFlow<Boolean> = _announcementEnabled

    private fun readStoredUri(): Uri? = prefs.getString(KEY_SOUND_URI, null)?.let(Uri::parse)

    fun setSoundUri(uri: Uri?) {
        prefs.edit { if (uri == null) remove(KEY_SOUND_URI) else putString(KEY_SOUND_URI, uri.toString()) }
        _soundUri.value = uri
    }

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    fun setSoundEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_SOUND_ENABLED, value) }
        _soundEnabled.value = value
    }

    fun setPopupEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_POPUP_ENABLED, value) }
        _popupEnabled.value = value
    }

    fun setTransferRequestEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_TRANSFER_REQUEST_ENABLED, value) }
        _transferRequestEnabled.value = value
    }

    fun setAnnouncementEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ANNOUNCEMENT_ENABLED, value) }
        _announcementEnabled.value = value
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

        /** Static reads for the four new per-category/behavior toggles —
         * same rationale as [isEnabled]: [NotificationHelper.notify] is a
         * plain object, not Hilt-injected. */
        fun isSoundEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SOUND_ENABLED, true)

        fun isPopupEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_POPUP_ENABLED, true)

        fun isTransferRequestEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_TRANSFER_REQUEST_ENABLED, true)

        fun isAnnouncementEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ANNOUNCEMENT_ENABLED, true)

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
