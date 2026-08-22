package com.emfitsolutions.gopreach.ui.screens.controlpanel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AppSettings
import com.emfitsolutions.gopreach.data.repository.AppSettingsRepository
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.data.repository.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlPanelUiState(
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
)

/** Spec §1/§5.1 — Control Panel module; logo upload/replace is Super-Admin only.
 * The theme picker here is the same per-device preference as the Settings
 * screen's copy (not Super-Admin-only — every signed-in user has their own),
 * just also surfaced here since some users look for display settings under
 * "Control Panel" rather than a separate "Settings" entry. */
@HiltViewModel
class ControlPanelViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val themePreferenceRepository: ThemePreferenceRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        appSettingsRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val theme: StateFlow<ThemePreference> = themePreferenceRepository.preference

    fun setTheme(preference: ThemePreference) = themePreferenceRepository.setPreference(preference)

    private val _uiState = MutableStateFlow(ControlPanelUiState())
    val uiState: StateFlow<ControlPanelUiState> = _uiState

    fun uploadLogo(imageUri: Uri, updatedByPersonId: String) {
        _uiState.update { it.copy(isUploading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                appSettingsRepository.uploadLogo(imageUri, updatedByPersonId)
                _uiState.update { it.copy(isUploading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploading = false, errorMessage = e.localizedMessage ?: "Upload failed.") }
            }
        }
    }
}
