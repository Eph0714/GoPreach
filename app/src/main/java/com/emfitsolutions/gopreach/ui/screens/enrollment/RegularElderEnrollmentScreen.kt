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
import com.emfitsolutions.gopreach.data.model.ElderTitleEntity
import com.emfitsolutions.gopreach.data.model.RegularElderRole
import com.emfitsolutions.gopreach.ui.components.TempCredentialsResultCard
import com.emfitsolutions.gopreach.ui.components.displayLabel

/** Spec §4.4 — Regular Elder enrollment (Super-Admin, Admin, Coordinator Elder). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegularElderEnrollmentScreen(
    currentPersonId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: RegularElderEnrollmentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val elderTitles by viewModel.elderTitles.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enroll Regular Elder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Name") },
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

                    ElderTitleDropdown(
                        titles = elderTitles,
                        selectedId = uiState.selectedElderTitleId,
                        onSelected = viewModel::onElderTitleSelected,
                    )

                    RegularElderRoleDropdown(
                        selected = uiState.selectedRole,
                        onSelected = viewModel::onRoleSelected,
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
                        Text("Create Regular Elder Account")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ElderTitleDropdown(
    titles: List<ElderTitleEntity>,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = titles.firstOrNull { it.id == selectedId }?.titleName ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Specific Role") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            titles.forEach { title ->
                DropdownMenuItem(
                    text = { Text(title.titleName) },
                    onClick = {
                        onSelected(title.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Required — a Group needs exactly one Elder in each of these three roles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegularElderRoleDropdown(
    selected: RegularElderRole?,
    onSelected: (RegularElderRole) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.displayLabel() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Group Role") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RegularElderRole.entries.forEach { role ->
                DropdownMenuItem(
                    text = { Text(role.displayLabel()) },
                    onClick = {
                        onSelected(role)
                        expanded = false
                    },
                )
            }
        }
    }
}
