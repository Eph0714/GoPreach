package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.ui.components.TempCredentialsResultCard
import com.emfitsolutions.gopreach.ui.components.rememberUnsavedChangesBackHandler

/**
 * "CREATING PUBLISHER" spec — Publisher enrollment.
 *
 * [visibleCongregationId] is the security boundary (resolved by the caller
 * from the enrolling session's own role, see GoPreachNavGraph): `null` means
 * Super-Admin, and only then does the Select Congregation field appear at
 * all — a non-null value keeps the Group-only screen for anyone already
 * scoped to one congregation (Admin/Coordinator Elder/Service Overseer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublisherEnrollmentScreen(
    currentPersonId: String,
    visibleCongregationId: String? = null,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: PublisherEnrollmentViewModel = hiltViewModel(),
) {
    LaunchedEffect(visibleCongregationId) { viewModel.restrictTo(visibleCongregationId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()

    val hasUnsavedChanges = uiState.result == null && (
        uiState.firstName.isNotBlank() || uiState.lastName.isNotBlank() ||
            uiState.address.isNotBlank() || uiState.contact.isNotBlank() || uiState.email.isNotBlank()
        )
    val guardedBack = rememberUnsavedChangesBackHandler(hasUnsavedChanges, onDiscard = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enroll Publisher") },
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
                        value = uiState.contact,
                        onValueChange = viewModel::onContactChange,
                        label = { Text("Contact") },
                        singleLine = true,
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

                    // Super-Admin only — Admin/Coordinator Elder/Service
                    // Overseer are already restricted to their own
                    // congregation upstream (visibleCongregationId), so this
                    // field would be both redundant and a way to imply a
                    // choice they don't actually have; omitted entirely for
                    // them instead of shown-but-disabled.
                    if (visibleCongregationId == null) {
                        CongregationDropdown(
                            congregations = congregations,
                            selectedId = uiState.selectedCongregationId,
                            onSelected = viewModel::onCongregationSelected,
                        )
                    }

                    GroupDropdown(
                        groups = groups,
                        selectedId = uiState.selectedGroupId,
                        onSelected = viewModel::onGroupSelected,
                        enabled = visibleCongregationId != null || uiState.selectedCongregationId != null,
                    )

                    HorizontalDivider()
                    Text("Status", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Checking one status disables and unchecks every other one.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    PublisherCategory.entries.forEach { category ->
                        RoleCheckboxRow(
                            label = category.name.replace('_', ' '),
                            checked = uiState.category == category,
                            enabled = uiState.category == null || uiState.category == category,
                            onCheckedChange = { viewModel.onCategoryToggled(category, it) },
                        )
                    }

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
                        Text("Create Publisher Account")
                    }
                }
            }
        }
    }
}

// CongregationDropdown (Super-Admin only) is already defined in
// AdminEnrollmentScreen.kt — same package, same exact shape needed here, so
// it's reused as-is rather than duplicated. RoleCheckboxRow is defined in
// CoordinatorElderEnrollmentScreen.kt, same package, reused the same way.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDropdown(groups: List<Group>, selectedId: String?, onSelected: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedId }?.name ?: ""
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Group") },
            placeholder = { if (!enabled) Text("Select a congregation first") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onSelected(group.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
