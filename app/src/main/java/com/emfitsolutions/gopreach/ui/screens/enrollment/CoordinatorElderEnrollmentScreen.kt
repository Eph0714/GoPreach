package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.ui.components.TempCredentialsResultCard
import com.emfitsolutions.gopreach.ui.components.rememberUnsavedChangesBackHandler

/** Spec §4.3 — Coordinator Elder enrollment, by Super-Admin or Admin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinatorElderEnrollmentScreen(
    currentPersonId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CoordinatorElderEnrollmentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    var isSuperAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(currentPersonId) {
        isSuperAdmin = viewModel.isEnrollerSuperAdmin(currentPersonId)
    }

    val hasUnsavedChanges = uiState.result == null && (
        uiState.firstName.isNotBlank() || uiState.lastName.isNotBlank() || uiState.address.isNotBlank() ||
            uiState.email.isNotBlank() || uiState.contact.isNotBlank()
        )
    val guardedBack = rememberUnsavedChangesBackHandler(hasUnsavedChanges, onDiscard = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enroll Coordinator Elder") },
                navigationIcon = {
                    IconButton(onClick = guardedBack.onBackPressed) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            if (uiState.result != null) {
                TempCredentialsResultCard(credentials = uiState.result!!, onDone = onDone)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = uiState.lastName,
                        onValueChange = viewModel::onLastNameChange,
                        label = { Text("Last Name") },
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.firstName,
                        onValueChange = viewModel::onFirstNameChange,
                        label = { Text("First Name") },
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.address,
                        onValueChange = viewModel::onAddressChange,
                        label = { Text("Address") },
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text("Email (optional)") },
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.contact,
                        onValueChange = viewModel::onContactChange,
                        label = { Text("Contact") },
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isSuperAdmin) {
                        CongregationDropdown(
                            congregations = congregations,
                            selectedId = uiState.selectedCongregationId,
                            onSelected = viewModel::onCongregationSelected,
                        )
                    } else {
                        Text(
                            "This person will be assigned to your congregation.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    HorizontalDivider()
                    Text("Select Role (optional)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "In addition to Coordinator Elder — a Regular Publisher category and Group Overseer can both apply at once, but only one publisher category at a time.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RoleCheckboxRow(
                        label = "Group Overseer",
                        checked = uiState.isGroupOverseer,
                        onCheckedChange = viewModel::onGroupOverseerToggled,
                    )
                    RoleCheckboxRow(
                        label = "Regular Pioneer",
                        checked = uiState.publisherCategory == PublisherCategory.REGULAR_PIONEER,
                        enabled = uiState.publisherCategory == null || uiState.publisherCategory == PublisherCategory.REGULAR_PIONEER,
                        onCheckedChange = { viewModel.onPublisherCategoryToggled(PublisherCategory.REGULAR_PIONEER, it) },
                    )
                    RoleCheckboxRow(
                        label = "Auxiliary Pioneer",
                        checked = uiState.publisherCategory == PublisherCategory.AUXILIARY_PIONEER,
                        enabled = uiState.publisherCategory == null || uiState.publisherCategory == PublisherCategory.AUXILIARY_PIONEER,
                        onCheckedChange = { viewModel.onPublisherCategoryToggled(PublisherCategory.AUXILIARY_PIONEER, it) },
                    )
                    RoleCheckboxRow(
                        label = "Regular Publisher",
                        checked = uiState.publisherCategory == PublisherCategory.REGULAR_PUBLISHER,
                        enabled = uiState.publisherCategory == null || uiState.publisherCategory == PublisherCategory.REGULAR_PUBLISHER,
                        onCheckedChange = { viewModel.onPublisherCategoryToggled(PublisherCategory.REGULAR_PUBLISHER, it) },
                    )

                    if (uiState.errorMessage != null) {
                        Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = { viewModel.save(enrollingPersonId = currentPersonId) },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text("Create Coordinator Elder Account")
                    }
                }
            }
        }
    }
}

/** One "Select Role" checkbox — [enabled] false renders it visibly disabled
 * (spec: checking one of the three mutually-exclusive categories disables
 * the other two, not just leaves them checkable-but-ignored). */
@Composable
private fun RoleCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
