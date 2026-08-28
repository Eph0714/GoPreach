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
 */
@Singleton
class NotificationSoundRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _soundUri = MutableStateFlow(readStoredUri())
    val soundUri: StateFlow<Uri?> = _soundUri

    private fun readStoredUri(): Uri? = prefs.getString(KEY_SOUND_URI, null)?.let(Uri::parse)

    fun setSoundUri(uri: Uri?) {
        prefs.edit { if (uri == null) remove(KEY_SOUND_URI) else putString(KEY_SOUND_URI, uri.toString()) }
        _soundUri.value = uri
    }
}
