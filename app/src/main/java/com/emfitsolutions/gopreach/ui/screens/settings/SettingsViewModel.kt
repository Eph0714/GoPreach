package com.emfitsolutions.gopreach.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.AppLanguageRepository
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.repository.NotificationSoundRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.data.repository.ThemePreferenceRepository
import com.emfitsolutions.gopreach.domain.AppLanguage
import com.emfitsolutions.gopreach.notifications.NotificationHelper
import com.emfitsolutions.gopreach.ui.theme.ThemeColorOption
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themePreferenceRepository: ThemePreferenceRepository,
    private val notificationSoundRepository: NotificationSoundRepository,
    private val appLanguageRepository: AppLanguageRepository,
    private val authRepository: AuthRepository,
    private val personRepository: PersonRepository,
) : ViewModel() {
    val theme: StateFlow<ThemePreference> = themePreferenceRepository.preference
    val colorOption: StateFlow<ThemeColorOption> = themePreferenceRepository.colorOption
    val customColor: StateFlow<Color> = themePreferenceRepository.customColor
    val notificationSoundUri: StateFlow<Uri?> = notificationSoundRepository.soundUri
    val notificationsEnabled: StateFlow<Boolean> = notificationSoundRepository.enabled
    val notificationSoundsEnabled: StateFlow<Boolean> = notificationSoundRepository.soundEnabled
    val popupNotificationsEnabled: StateFlow<Boolean> = notificationSoundRepository.popupEnabled
    val transferRequestNotificationsEnabled: StateFlow<Boolean> = notificationSoundRepository.transferRequestEnabled
    val announcementNotificationsEnabled: StateFlow<Boolean> = notificationSoundRepository.announcementEnabled
    val language: StateFlow<AppLanguage> = appLanguageRepository.current

    /** One-shot "Language successfully changed." (in the newly selected
     * language — see [AppLanguage.confirmationMessage]) for the screen to
     * show as a toast. A [SharedFlow], not part of [language]'s own state,
     * so it fires exactly once per tap rather than replaying on every
     * recomposition/screen re-entry the way a StateFlow would. */
    private val _languageChanged = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val languageChanged: SharedFlow<String> = _languageChanged.asSharedFlow()

    fun setTheme(value: ThemePreference) = themePreferenceRepository.setPreference(value)
    fun setColorOption(value: ThemeColorOption) = themePreferenceRepository.setColorOption(value)
    fun setCustomColor(value: Color) = themePreferenceRepository.setCustomColor(value)

    /** "Settings -> Language" — applies immediately on this device
     * ([AppLanguageRepository.applyLanguage], which triggers the usual
     * Activity recreation), then persists to the signed-in Person's own
     * profile so this choice follows them to any other device they sign
     * into (spec: user-specific, synced via the existing offline-first
     * Person save — queues and syncs later if currently offline, same as
     * every other profile edit in this app). */
    fun setLanguage(value: AppLanguage) {
        appLanguageRepository.applyLanguage(value)
        _languageChanged.tryEmit(value.confirmationMessage)
        val personId = authRepository.currentPersonId ?: return
        viewModelScope.launch {
            val person = personRepository.get(personId) ?: return@launch
            personRepository.save(person.copy(language = value.code))
        }
    }

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

    /** "NOTIFICATION SETTINGS" — four independent per-device toggles layered
     * on top of [setNotificationsEnabled]'s master switch, checked in
     * [NotificationHelper.notify]: whether a notification plays a sound at
     * all (popup still shows), whether a popup shows at all, and separately
     * gating the two categories the spec names (Transfer Request/
     * Announcement) — every other category (Monthly Report reminders,
     * Calendar events) is unaffected by the last two and stays governed by
     * the master switch alone, same as before these existed. */
    fun setNotificationSoundsEnabled(value: Boolean) = notificationSoundRepository.setSoundEnabled(value)
    fun setPopupNotificationsEnabled(value: Boolean) = notificationSoundRepository.setPopupEnabled(value)
    fun setTransferRequestNotificationsEnabled(value: Boolean) = notificationSoundRepository.setTransferRequestEnabled(value)
    fun setAnnouncementNotificationsEnabled(value: Boolean) = notificationSoundRepository.setAnnouncementEnabled(value)
}
