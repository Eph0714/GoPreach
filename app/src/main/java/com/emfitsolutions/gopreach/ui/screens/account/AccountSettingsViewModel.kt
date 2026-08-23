package com.emfitsolutions.gopreach.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.repository.AuthResult
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountSettingsUiState(
    /** Personal Information — shown/editable for whoever is signed in,
     * including a Super-Admin (spec request: "add a name of the Super-Admin"
     * in Account Settings). No current-password check here, unlike
     * username/password below — this is profile info, not a login credential. */
    val firstName: String = "",
    val lastName: String = "",
    val isSavingName: Boolean = false,
    val nameMessage: String? = null,
    val nameError: String? = null,

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

/** Spec §1 — Super-Admin (or any signed-in user) editing their own account.
 * Username/password changes require the current password (spec's "require
 * the current password before changing X"); a password change signs the user
 * out afterward so they log back in with the new one. Name is plain profile
 * info and doesn't need re-authentication to change. */
@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val personRepository: PersonRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val person = authRepository.currentPersonId?.let { personRepository.get(it) }
            if (person != null) {
                _uiState.update { it.copy(firstName = person.firstName, lastName = person.lastName) }
            }
        }
    }

    fun onFirstNameChange(value: String) = _uiState.update { it.copy(firstName = value.uppercase(), nameError = null, nameMessage = null) }
    fun onLastNameChange(value: String) = _uiState.update { it.copy(lastName = value.uppercase(), nameError = null, nameMessage = null) }

    fun saveName() {
        val state = _uiState.value
        if (state.firstName.isBlank() || state.lastName.isBlank()) {
            _uiState.update { it.copy(nameError = "First and last name are required.") }
            return
        }
        val personId = authRepository.currentPersonId ?: run {
            _uiState.update { it.copy(nameError = "Session expired — please log in again.") }
            return
        }
        _uiState.update { it.copy(isSavingName = true, nameError = null, nameMessage = null) }
        viewModelScope.launch {
            val person = personRepository.get(personId)
            if (person == null) {
                _uiState.update { it.copy(isSavingName = false, nameError = "Account record not found.") }
                return@launch
            }
            personRepository.save(person.copy(firstName = state.firstName.trim(), lastName = state.lastName.trim()))
            _uiState.update { it.copy(isSavingName = false, nameMessage = "Name updated.") }
        }
    }

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
