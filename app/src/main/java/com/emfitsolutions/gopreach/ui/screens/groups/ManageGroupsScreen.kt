package com.emfitsolutions.gopreach.ui.screens.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Person

/** Spec §3 — "CRUD Groups + assign 1 Elder". [fixedCongregationId] scopes both
 * the list and the create dialog for Admin/Coordinator Elder; null lets a
 * Super-Admin pick a congregation per group instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageGroupsScreen(
    fixedCongregationId: String?,
    onBack: () -> Unit,
    viewModel: ManageGroupsViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New Group")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No groups yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.group.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(row.group.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Elder: ${row.elderName ?: "Unassigned"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { pendingDelete = row.group }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete group")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            fixedCongregationId = fixedCongregationId,
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${toDelete.name}?") },
            text = { Text("Publishers already assigned to this group keep their assignment; reassign them afterward.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(toDelete.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGroupDialog(
    fixedCongregationId: String?,
    viewModel: ManageGroupsViewModel,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedElder by remember { mutableStateOf<Person?>(null) }
    var pickedCongregation by remember { mutableStateOf<Congregation?>(null) }
    val congregations by viewModel.congregations.collectAsStateWithLifecycle(initialValue = emptyList())
    // Scoped roles (Admin/Coordinator Elder) already have exactly one congregation;
    // only a Super-Admin needs to pick one here.
    val congregationId = fixedCongregationId ?: pickedCongregation?.id
    val elders by remember(congregationId) {
        if (congregationId != null) viewModel.availableEldersFor(congregationId) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (fixedCongregationId == null) {
                    CongregationPickerDropdown(
                        congregations = congregations,
                        selected = pickedCongregation,
                        onSelected = { pickedCongregation = it },
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                ElderDropdown(elders = elders, selected = selectedElder, onSelected = { selectedElder = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && congregationId != null) {
                        viewModel.save(
                            Group(
                                congregationId = congregationId,
                                name = name.trim(),
                                regularElderPersonId = selectedElder?.id,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                        onDismiss()
                    }
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CongregationPickerDropdown(
    congregations: List<Congregation>,
    selected: Congregation?,
    onSelected: (Congregation) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Congregation") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            congregations.forEach { congregation ->
                DropdownMenuItem(
                    text = { Text(congregation.name) },
                    onClick = {
                        onSelected(congregation)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElderDropdown(elders: List<Person>, selected: Person?, onSelected: (Person) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.fullName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Regular Elder") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            elders.forEach { elder ->
                DropdownMenuItem(
                    text = { Text(elder.fullName) },
                    onClick = {
                        onSelected(elder)
                        expanded = false
                    },
                )
            }
        }
    }
}
