package com.emfitsolutions.gopreach.ui.screens.login

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.R
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

private const val TAG = "AuthDebug"

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
        // Bug fix ("the app is not opening"): CredentialStore is backed by
        // EncryptedSharedPreferences, which can throw (a documented Android
        // Keystore failure mode — e.g. the key got invalidated by a lock-
        // screen change, a security patch, or a restored device) rather than
        // just returning null. This used to call credentialStore.read()
        // directly and unguarded, during ViewModel construction — a throw
        // here crashed this ViewModel's own creation, which crashed the
        // entire Login screen the instant it tried to compose, before a
        // person could even see a login form to retry with. A "remember me"
        // convenience feature must never be able to take down the one screen
        // every session depends on; worst case now is simply landing on a
        // blank, unfilled login form instead of a crash.
        val saved = runCatching { credentialStore.read() }
            .onFailure { Log.e(TAG, "Failed to read saved credential: ${it::class.simpleName}") }
            .getOrNull()
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
        // Bug fix ("Prevent duplicate login requests"): the Login button is
        // already disabled while isLoading (see LoginScreen), but that's a
        // UI-layer guard only — this ViewModel is the one place both real
        // entry points (a normal submit and a biometric unlock, below) funnel
        // through, so the actual "never start a second sign-in while one is
        // already running" guarantee belongs here, not duplicated in every
        // caller.
        if (state.isLoading) return
        val username = state.username.trim()
        val password = state.password
        // "Please enter your username." / "Please enter your password." —
        // reported separately (not just a combined "enter both") so a user
        // who filled in one field but not the other gets told which one is
        // actually missing, per the login audit spec's own test scenarios.
        val validationError = when {
            username.isBlank() && password.isBlank() -> context.getString(R.string.login_error_missing_fields)
            username.isBlank() -> context.getString(R.string.login_error_missing_username)
            password.isBlank() -> context.getString(R.string.login_error_missing_password)
            else -> null
        }
        Log.d(TAG, "Username validation result: ${if (username.isBlank()) "MISSING" else "OK"}")
        Log.d(TAG, "Password validation result: ${if (password.isBlank()) "MISSING" else "OK"}")
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        performSignIn(username, password)
    }

    /** Invoked after [androidx.biometric.BiometricPrompt] reports success — unlocks
     * the saved credential pair and signs in with it, same as a normal submit. */
    fun onBiometricAuthSucceeded() {
        if (_uiState.value.isLoading) return
        val saved = credentialStore.read() ?: run {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.login_error_no_saved_credential)) }
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
            // Bug fix ("Never allow: Infinite Loading, Frozen Login Button") —
            // a try/finally around the whole request, not just a when-branch
            // on the two AuthResult cases: [AuthRepository.signIn]/
            // [offlineSignIn] already guarantee they never throw, but
            // [connectivityObserver.isOnline] itself is a plain
            // ConnectivityManager call outside that guarantee, and this
            // finally block is what makes "isLoading always ends up false
            // again" true regardless of what actually goes wrong, not just
            // for the two outcomes this function already anticipated.
            try {
                val online = connectivityObserver.isOnline()
                Log.d(TAG, "Database/API connection result: ${if (online) "ONLINE" else "OFFLINE"}")
                val result = if (online) authRepository.signIn(username, password) else authRepository.offlineSignIn(username, password)
                when (result) {
                    is AuthResult.Success -> {
                        Log.d(TAG, "Authentication result: SUCCESS")
                        // Bug fix: a real, successful sign-in must never be
                        // reported as a failure just because the "Remember
                        // me" convenience write afterward hit a Keystore
                        // error — this is a non-essential side effect of an
                        // otherwise-complete login, so it's caught and
                        // logged, not allowed to fall through to this
                        // function's own catch block below and overwrite a
                        // real success with an error message.
                        runCatching {
                            if (_uiState.value.rememberMe) credentialStore.save(username, password) else credentialStore.clear()
                        }.onFailure { Log.e(TAG, "Failed to update saved credential: ${it::class.simpleName}") }
                        _uiState.update {
                            it.copy(isLoading = false, signedIn = true, requiresPasswordChange = result.requiresPasswordChange)
                        }
                    }
                    is AuthResult.Error -> {
                        Log.d(TAG, "Authentication result: FAILED")
                        _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected login error: ${e::class.simpleName}")
                _uiState.update { it.copy(isLoading = false, errorMessage = "Something went wrong. Please try again.") }
            }
        }
    }

    fun consumeNavigationEvent() {
        _uiState.update { it.copy(signedIn = false, requiresPasswordChange = null) }
    }
}
