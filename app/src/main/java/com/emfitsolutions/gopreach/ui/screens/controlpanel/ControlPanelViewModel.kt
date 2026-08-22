package com.emfitsolutions.gopreach.ui.screens.controlpanel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AppSettings
import com.emfitsolutions.gopreach.data.repository.AppSettingsRepository
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

/** Spec §1/§5.1 — Control Panel module; logo upload/replace is Super-Admin only. */
@HiltViewModel
class ControlPanelViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        appSettingsRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

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
