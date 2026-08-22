package com.emfitsolutions.gopreach.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.data.repository.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferenceRepository: ThemePreferenceRepository,
) : ViewModel() {
    val theme: StateFlow<ThemePreference> = themePreferenceRepository.preference

    fun setTheme(value: ThemePreference) = themePreferenceRepository.setPreference(value)
}
