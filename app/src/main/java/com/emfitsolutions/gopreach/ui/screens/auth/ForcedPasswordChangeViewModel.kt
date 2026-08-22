package com.emfitsolutions.gopreach.ui.screens.auth

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

data class ForcedPasswordChangeUiState(
    val newUsername: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val completed: Boolean = false,
)

/**
 * Spec §4.5 steps 2-3: mandatory username+password change after a temp-credential
 * login, then a forced re-login with the new credentials.
 */
@HiltViewModel
class ForcedPasswordChangeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForcedPasswordChangeUiState())
    val uiState: StateFlow<ForcedPasswordChangeUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) = _uiState.update { it.copy(newUsername = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        when {
            state.newUsername.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Choose a username.") }
                return
            }
            state.newPassword.length < 8 -> {
                _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters.") }
                return
            }
            state.newPassword != state.confirmPassword -> {
                _uiState.update { it.copy(errorMessage = "Passwords don't match.") }
                return
            }
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.forcedPasswordChange(state.newUsername.trim(), state.newPassword)) {
                is AuthResult.Success -> _uiState.update { it.copy(isLoading = false, completed = true) }
                is AuthResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }
}
