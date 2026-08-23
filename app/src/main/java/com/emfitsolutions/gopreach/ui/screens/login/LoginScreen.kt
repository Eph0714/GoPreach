package com.emfitsolutions.gopreach.ui.screens.login

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.ui.components.GradientHero

private val FieldShape = RoundedCornerShape(16.dp)

/** Fires the system biometric prompt on top of [activity] and reports back through
 * plain callbacks — kept free of ViewModel/Compose state so it's a thin wrapper
 * around the androidx.biometric API. */
private fun launchBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // errorCode 13 (ERROR_USER_CANCELED) / 10 (ERROR_NEGATIVE_BUTTON) are the
                // user backing out on purpose — not worth surfacing as an error banner.
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }
        },
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Sign in to GoPreach")
        .setSubtitle("Use your fingerprint or face to continue")
        .setNegativeButtonText("Use password instead")
        .build()
    prompt.authenticate(promptInfo)
}

/**
 * Login entry screen. Establishes the visual pattern used everywhere: plain-text
 * fields by default, password fields masked with a show/hide toggle (spec §1).
 */
@Composable
fun LoginScreen(
    onForgotPasswordClick: () -> Unit,
    onSignedIn: (requiresPasswordChange: Boolean) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    val activity = LocalContext.current as FragmentActivity

    LaunchedEffect(uiState.signedIn) {
        if (uiState.signedIn) {
            onSignedIn(uiState.requiresPasswordChange == true)
            viewModel.consumeNavigationEvent()
        }
    }

    // A single scrollable Column, footer included as its last item — not a
    // Box-aligned overlay pinned to the bottom of the screen. A pinned overlay
    // looked right with no keyboard up, but once the IME shrinks the visible
    // height (see MainActivity's imePadding()), a fixed-position footer ends up
    // sitting on top of whatever content the shrunk viewport happens to place
    // there — in practice, directly on top of the Log In button. Keeping the
    // footer in-flow means it simply scrolls along like everything else instead.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        GradientHero(height = 220.dp) {
                // Bottom-aligned rather than pinned under the status bar — sitting
                // right at the top of the hero read as too high/cramped; anchoring
                // to the bottom instead gives the title/subtitle block room to
                // breathe and keeps it comfortably in view regardless of status bar
                // height across devices.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("GoPreach", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Text(
                        "Ministry Activity Tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(PaddingValues(horizontal = 24.dp))
                    .padding(top = 32.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Welcome back!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Username") },
                    singleLine = true,
                    shape = FieldShape,
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    // Plain text, never masked — per spec, only password fields mask input.
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    shape = FieldShape,
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.rememberMe, onCheckedChange = viewModel::onRememberMeChange)
                        Text("Remember me", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = onForgotPasswordClick) {
                        Text("Forgot Password?")
                    }
                }

                if (uiState.errorMessage != null) {
                    Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = viewModel::signIn,
                    enabled = !uiState.isLoading,
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 6.dp, shape = FieldShape),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Log In", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (uiState.biometricSignInAvailable) {
                    OutlinedButton(
                        onClick = {
                            launchBiometricPrompt(
                                activity = activity,
                                onSuccess = viewModel::onBiometricAuthSucceeded,
                                onError = { /* user-visible errors already filtered above; ignore transient hardware errors */ },
                            )
                        },
                        enabled = !uiState.isLoading,
                        shape = FieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Sign in with biometrics")
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "POWERED BY: EMF IT Solutions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}
