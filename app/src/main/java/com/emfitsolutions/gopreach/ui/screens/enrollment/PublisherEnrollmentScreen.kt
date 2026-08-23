package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.LaunchedEffect
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Gender
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.ui.components.TempCredentialsResultCard
import com.emfitsolutions.gopreach.ui.components.rememberUnsavedChangesBackHandler

/** Spec §4.6 — Publisher Master File enrollment.
 *
 * "Publisher Congregation Assignment" spec — [visibleCongregationId] is the
 * security boundary (resolved by the caller from the enrolling session's own
 * role, see GoPreachNavGraph): `null` means Super-Admin, and only then does
 * the Select Congregation field appear at all (spec §2: "do not unnecessarily
 * change the existing... design" for Admin/Elder) — a non-null value keeps
 * their original Group-only screen exactly as it was, just correctly scoped
 * to their own congregation's groups now (previously every congregation's
 * groups showed here, a real pre-existing scope gap this closes). */
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
            uiState.address.isNotBlank() || uiState.contact.isNotBlank() ||
            uiState.contactPerson.isNotBlank() || uiState.contactPersonNumber.isNotBlank()
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.middleInitial,
                            onValueChange = viewModel::onMiddleInitialChange,
                            label = { Text("M.I. (optional)") },
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = uiState.extensionName,
                            onValueChange = viewModel::onExtensionNameChange,
                            label = { Text("Extension (optional)") },
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = uiState.address,
                        onValueChange = viewModel::onAddressChange,
                        label = { Text("Address") },
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    GenderSelector(selected = uiState.gender, onSelected = viewModel::onGenderChange)

                    OutlinedTextField(
                        value = uiState.contact,
                        onValueChange = viewModel::onContactChange,
                        label = { Text("Contact") },
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.contactPerson,
                        onValueChange = viewModel::onContactPersonChange,
                        label = { Text("Contact Person (optional)") },
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.contactPersonNumber,
                        onValueChange = viewModel::onContactPersonNumberChange,
                        label = { Text("Contact Person Number (optional)") },
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PublisherCategoryDropdown(selected = uiState.category, onSelected = viewModel::onCategoryChange)

                    // Super-Admin only (spec §1) — Admin/Coordinator Elder are
                    // already restricted to their own congregation upstream
                    // (visibleCongregationId), so this field would be both
                    // redundant and a way to imply a choice they don't
                    // actually have; omitted entirely for them instead of
                    // shown-but-disabled.
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

@Composable
private fun GenderSelector(selected: Gender?, onSelected: (Gender) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Gender.entries.forEach { gender ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(selected = selected == gender, onClick = { onSelected(gender) }),
            ) {
                RadioButton(selected = selected == gender, onClick = { onSelected(gender) })
                Text(gender.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublisherCategoryDropdown(selected: PublisherCategory, onSelected: (PublisherCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text("Publisher Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PublisherCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name.replace('_', ' ')) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

// CongregationDropdown (Super-Admin only, spec §1) is already defined in
// AdminEnrollmentScreen.kt — same package, same exact shape needed here, so
// it's reused as-is rather than duplicated.

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
