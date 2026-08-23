package com.emfitsolutions.gopreach.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountSettingsUiState(
    val currentPasswordForUsername: String = "",
    val newUsername: String = "",
    val isSavingUsername: Boolean = false,
    val usernameMessage: String? = null,
    val usernameError: String? = null,

    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val isSavingPassword: Boolean = false,
    val passwordChanged: Boolean = false,
    val passwordError: String? = null,
)

/** Spec §1 — Super-Admin (or any signed-in user) editing their own login
 * credentials. Both actions require the current password (spec's "require
 * the current password before changing X"); a password change signs the user
 * out afterward so they log back in with the new one. */
@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    fun onCurrentPasswordForUsernameChange(value: String) = _uiState.update { it.copy(currentPasswordForUsername = value, usernameError = null) }
    fun onNewUsernameChange(value: String) = _uiState.update { it.copy(newUsername = value, usernameError = null) }
    fun onCurrentPasswordChange(value: String) = _uiState.update { it.copy(currentPassword = value, passwordError = null) }
    fun onNewPasswordChange(value: String) = _uiState.update { it.copy(newPassword = value, passwordError = null) }
    fun onConfirmNewPasswordChange(value: String) = _uiState.update { it.copy(confirmNewPassword = value, passwordError = null) }

    fun saveUsername() {
        val state = _uiState.value
        if (state.newUsername.isBlank() || state.currentPasswordForUsername.isBlank()) {
            _uiState.update { it.copy(usernameError = "Enter your current password and a new username.") }
            return
        }
        _uiState.update { it.copy(isSavingUsername = true, usernameError = null, usernameMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.changeUsername(state.newUsername, state.currentPasswordForUsername)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isSavingUsername = false, usernameMessage = "Username updated.", newUsername = "", currentPasswordForUsername = "")
                }
                is AuthResult.Error -> _uiState.update { it.copy(isSavingUsername = false, usernameError = result.message) }
            }
        }
    }

    fun savePassword() {
        val state = _uiState.value
        if (state.currentPassword.isBlank() || state.newPassword.isBlank()) {
            _uiState.update { it.copy(passwordError = "Enter your current and new password.") }
            return
        }
        if (state.newPassword != state.confirmNewPassword) {
            _uiState.update { it.copy(passwordError = "New password and confirmation don't match.") }
            return
        }
        _uiState.update { it.copy(isSavingPassword = true, passwordError = null) }
        viewModelScope.launch {
            when (val result = authRepository.changePassword(state.currentPassword, state.newPassword)) {
                is AuthResult.Success -> _uiState.update { it.copy(isSavingPassword = false, passwordChanged = true) }
                is AuthResult.Error -> _uiState.update { it.copy(isSavingPassword = false, passwordError = result.message) }
            }
        }
    }
}
