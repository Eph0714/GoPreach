package com.emfitsolutions.gopreach.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.data.repository.NotificationSoundRepository
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.data.repository.ThemePreferenceRepository
import com.emfitsolutions.gopreach.notifications.NotificationHelper
import com.emfitsolutions.gopreach.ui.theme.ThemeColorOption
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themePreferenceRepository: ThemePreferenceRepository,
    private val notificationSoundRepository: NotificationSoundRepository,
) : ViewModel() {
    val theme: StateFlow<ThemePreference> = themePreferenceRepository.preference
    val colorOption: StateFlow<ThemeColorOption> = themePreferenceRepository.colorOption
    val customColor: StateFlow<Color> = themePreferenceRepository.customColor
    val notificationSoundUri: StateFlow<Uri?> = notificationSoundRepository.soundUri
    val notificationsEnabled: StateFlow<Boolean> = notificationSoundRepository.enabled

    fun setTheme(value: ThemePreference) = themePreferenceRepository.setPreference(value)
    fun setColorOption(value: ThemeColorOption) = themePreferenceRepository.setColorOption(value)
    fun setCustomColor(value: Color) = themePreferenceRepository.setCustomColor(value)

    /** [uri] null means "use the system default notification sound," same as
     * picking "Default" in the system ringtone picker. Applies immediately —
     * see [NotificationHelper.applySoundPreference]'s doc comment for why
     * this is the one place that recreates the notification channel. */
    fun setNotificationSound(uri: Uri?) {
        notificationSoundRepository.setSoundUri(uri)
        NotificationHelper.applySoundPreference(context, uri)
    }

    /** Master on/off switch for every notification [NotificationHelper.notify]
     * posts (Transfer Request, Announcement, report reminders) — spec: "allow
     * the publisher to turn on and turn off notification." Calendar Alarms
     * are untouched by this; see [NotificationSoundRepository]'s doc comment. */
    fun setNotificationsEnabled(value: Boolean) = notificationSoundRepository.setEnabled(value)
}
