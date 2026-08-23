package com.emfitsolutions.gopreach.ui.screens.login

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.repository.AuthResult
import com.emfitsolutions.gopreach.data.repository.CredentialStore
import com.emfitsolutions.gopreach.data.sync.ConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Set once sign-in succeeds; the nav layer reacts to this to route the user
     * on to either the forced-password-change flow or their home screen. */
    val requiresPasswordChange: Boolean? = null,
    val signedIn: Boolean = false,
    /** True only when there's a saved credential pair AND the device has an
     * enrolled biometric that can unlock it — the fingerprint/face sign-in
     * affordance is hidden entirely otherwise, rather than shown disabled. */
    val biometricSignInAvailable: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val credentialStore: CredentialStore,
    private val connectivityObserver: ConnectivityObserver,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        val saved = credentialStore.read()
        val biometricReady = saved != null && canAuthenticateWithBiometrics()
        _uiState.update {
            it.copy(
                username = saved?.first ?: "",
                // Restoring only the username left the password field blank on every
                // relaunch despite "Remember me" showing checked — the whole point of
                // the feature is to not have to retype the password, so restore both.
                password = saved?.second ?: "",
                rememberMe = saved != null,
                biometricSignInAvailable = biometricReady,
            )
        }
    }

    private fun canAuthenticateWithBiometrics(): Boolean {
        val result = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onRememberMeChange(value: Boolean) = _uiState.update { it.copy(rememberMe = value) }

    fun signIn() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter both username and password.") }
            return
        }
        performSignIn(state.username.trim(), state.password)
    }

    /** Invoked after [androidx.biometric.BiometricPrompt] reports success — unlocks
     * the saved credential pair and signs in with it, same as a normal submit. */
    fun onBiometricAuthSucceeded() {
        val saved = credentialStore.read() ?: run {
            _uiState.update { it.copy(errorMessage = "No saved sign-in found for biometric unlock.") }
            return
        }
        performSignIn(saved.first, saved.second)
    }

    /** "Offline Login" spec §1 — no network means Firebase's own sign-in call
     * can't be made at all (it always requires a round trip; there's no offline
     * path in the SDK), so this checks connectivity first and, when offline,
     * verifies against the locally-hashed credential saved by this device's
     * last successful *online* sign-in instead — see
     * [AuthRepository.offlineSignIn]. "Remember me"'s saved pair is unrelated
     * (that's for the optional biometric shortcut) and isn't touched here. */
    private fun performSignIn(username: String, password: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = if (connectivityObserver.isOnline()) {
                authRepository.signIn(username, password)
            } else {
                authRepository.offlineSignIn(username, password)
            }
            when (result) {
                is AuthResult.Success -> {
                    if (_uiState.value.rememberMe) {
                        credentialStore.save(username, password)
                    } else {
                        credentialStore.clear()
                    }
                    _uiState.update {
                        it.copy(isLoading = false, signedIn = true, requiresPasswordChange = result.requiresPasswordChange)
                    }
                }
                is AuthResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun consumeNavigationEvent() {
        _uiState.update { it.copy(signedIn = false, requiresPasswordChange = null) }
    }
}
