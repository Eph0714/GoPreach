package com.emfitsolutions.gopreach.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.data.repository.ThemePreferenceRepository
import com.emfitsolutions.gopreach.ui.theme.ThemeColorOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferenceRepository: ThemePreferenceRepository,
) : ViewModel() {
    val theme: StateFlow<ThemePreference> = themePreferenceRepository.preference
    val colorOption: StateFlow<ThemeColorOption> = themePreferenceRepository.colorOption
    val customColor: StateFlow<Color> = themePreferenceRepository.customColor

    fun setTheme(value: ThemePreference) = themePreferenceRepository.setPreference(value)
    fun setColorOption(value: ThemeColorOption) = themePreferenceRepository.setColorOption(value)
    fun setCustomColor(value: Color) = themePreferenceRepository.setCustomColor(value)
}
