package com.emfitsolutions.gopreach.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Spec §1 — "Edit Account / Account Settings" screen: change username (with
 * uniqueness + current-password checks) and change password (current/new/
 * confirm + requirement validation), both hashed/stored by Firebase Auth
 * itself, never in plaintext by this app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onSignedOutForPasswordChange: () -> Unit,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.passwordChanged) {
        if (uiState.passwordChanged) onSignedOutForPasswordChange()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Change Username", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.newUsername,
                onValueChange = viewModel::onNewUsernameChange,
                label = { Text("New Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.currentPasswordForUsername,
                onValueChange = viewModel::onCurrentPasswordForUsernameChange,
                label = { Text("Current Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.usernameError != null) Text(uiState.usernameError!!, color = MaterialTheme.colorScheme.error)
            if (uiState.usernameMessage != null) Text(uiState.usernameMessage!!, color = MaterialTheme.colorScheme.primary)
            Button(onClick = viewModel::saveUsername, enabled = !uiState.isSavingUsername, modifier = Modifier.fillMaxWidth()) {
                if (uiState.isSavingUsername) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Save Username")
            }

            HorizontalDivider()

            Text("Change Password", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.currentPassword,
                onValueChange = viewModel::onCurrentPasswordChange,
                label = { Text("Current Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = { Text("New Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.confirmNewPassword,
                onValueChange = viewModel::onConfirmNewPasswordChange,
                label = { Text("Confirm New Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("At least 6 characters.", style = MaterialTheme.typography.bodySmall)
            if (uiState.passwordError != null) Text(uiState.passwordError!!, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::savePassword, enabled = !uiState.isSavingPassword, modifier = Modifier.fillMaxWidth()) {
                if (uiState.isSavingPassword) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Save Password")
            }
            Text(
                "Changing your password signs you out — log back in with the new one.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
