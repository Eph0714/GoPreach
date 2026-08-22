package com.emfitsolutions.gopreach.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemePreference { SYSTEM, LIGHT, DARK }

private const val PREFS_NAME = "gopreach_settings"
private const val KEY_THEME = "theme_preference"

/**
 * User's light/dark display preference — separate from any account data, so it
 * applies instantly on this device and survives sign-out (spec §1 asks for a
 * "modern Android UI"; a per-device display choice like this is a standard
 * expectation, not something tied to a Person record).
 */
@Singleton
class ThemePreferenceRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preference = MutableStateFlow(readStored())
    val preference: StateFlow<ThemePreference> = _preference

    private fun readStored(): ThemePreference =
        runCatching { ThemePreference.valueOf(prefs.getString(KEY_THEME, ThemePreference.SYSTEM.name)!!) }
            .getOrDefault(ThemePreference.SYSTEM)

    fun setPreference(value: ThemePreference) {
        prefs.edit { putString(KEY_THEME, value.name) }
        _preference.value = value
    }
}
