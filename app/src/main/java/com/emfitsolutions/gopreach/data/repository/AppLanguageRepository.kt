package com.emfitsolutions.gopreach.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.emfitsolutions.gopreach.domain.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Settings -> Language" — applies [AppLanguage.localeTag] on this device via
 * AndroidX's per-app language API (`AppCompatDelegate.setApplicationLocales`),
 * which works down to this app's minSdk regardless of API level: Android 13+
 * delegates straight to the system `LocaleManager`; older versions get it
 * backported by AppCompat itself (see AndroidManifest.xml's
 * `AppLocalesMetadataHolderService` entry and [MainActivity]'s own doc
 * comment on why it now extends `AppCompatActivity`). Persistence across app
 * restarts on *this* device is handled entirely by that same platform/library
 * mechanism — nothing extra to store locally here.
 *
 * Cross-device sync (spec: "the same user logs in from Android [and]
 * Desktop... should detect the language preference") is a separate concern
 * this repository doesn't own: [com.emfitsolutions.gopreach.domain.UserSession]
 * reads the signed-in [com.emfitsolutions.gopreach.data.model.Person.language]
 * and calls [applyLanguage] whenever it differs from what's already applied —
 * see that class's own doc comment.
 */
@Singleton
class AppLanguageRepository @Inject constructor() {

    private val _current = MutableStateFlow(readAppliedLanguage())
    val current: StateFlow<AppLanguage> = _current

    private fun readAppliedLanguage(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return AppLanguage.entries.firstOrNull { it.localeTag == tag } ?: AppLanguage.ENGLISH
    }

    /** Applies [language] to the whole app immediately (triggers the usual
     * Activity recreation a per-app locale change causes — the same
     * mechanism a system dark-mode toggle already relies on, nothing new to
     * this app). Callers that also need this persisted to the signed-in
     * Person's own profile (the normal case — see [SettingsViewModel
     * .setLanguage]) do that as a separate step; this function only ever
     * touches the on-device applied locale. */
    fun applyLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.localeTag))
        _current.value = language
    }
}
