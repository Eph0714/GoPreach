package com.emfitsolutions.gopreach.ui.screens.territories

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
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
import com.emfitsolutions.gopreach.data.model.Territory

/** Spec §3/§5.1 — Territory Master File. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTerritoriesScreen(
    fixedCongregationId: String?,
    onBack: () -> Unit,
    viewModel: ManageTerritoriesViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val groups by viewModel.groups.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<Territory?>(null) }
    var pendingDelete by remember { mutableStateOf<Territory?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Territories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New Territory")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No territories yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.territory.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(row.territory.name, style = MaterialTheme.typography.titleMedium)
                                if (row.territory.description != null) {
                                    Text(row.territory.description, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "Assigned group: ${row.groupName ?: "Unassigned"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row {
                                IconButton(onClick = { pendingEdit = row.territory }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit territory")
                                }
                                IconButton(onClick = { pendingDelete = row.territory }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete territory")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val congregations by viewModel.congregations.collectAsStateWithLifecycle(initialValue = emptyList())

    if (showCreateDialog) {
        TerritoryDialog(
            fixedCongregationId = fixedCongregationId,
            existingTerritory = null,
            congregations = congregations,
            allGroups = groups,
            onSave = { viewModel.save(it) },
            onDismiss = { showCreateDialog = false },
        )
    }

    val toEditTerritory = pendingEdit
    if (toEditTerritory != null) {
        TerritoryDialog(
            fixedCongregationId = fixedCongregationId,
            existingTerritory = toEditTerritory,
            congregations = congregations,
            allGroups = groups,
            onSave = { viewModel.save(it) },
            onDismiss = { pendingEdit = null },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${toDelete.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(toDelete.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            text = { Text("This removes the territory record.") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerritoryDialog(
    fixedCongregationId: String?,
    existingTerritory: Territory?,
    congregations: List<Congregation>,
    allGroups: List<Group>,
    onSave: (Territory) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existingTerritory?.name ?: "") }
    var description by remember { mutableStateOf(existingTerritory?.description ?: "") }
    var pickedCongregation by remember(congregations) {
        mutableStateOf(congregations.firstOrNull { it.id == existingTerritory?.congregationId })
    }
    val congregationId = fixedCongregationId ?: pickedCongregation?.id ?: existingTerritory?.congregationId
    val groups = allGroups.filter { it.congregationId == congregationId }
    var selectedGroup by remember(groups) {
        mutableStateOf(groups.firstOrNull { it.id == existingTerritory?.assignedGroupId })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingTerritory == null) "New Territory" else "Edit Territory") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (fixedCongregationId == null) {
                    CongregationPickerDropdown(
                        congregations = congregations,
                        selected = pickedCongregation,
                        onSelected = { pickedCongregation = it; selectedGroup = null },
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.uppercase() },
                    label = { Text("Territory Name") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.uppercase() },
                    label = { Text("Description (optional)") },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                GroupDropdown(groups = groups, selected = selectedGroup, onSelected = { selectedGroup = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && congregationId != null) {
                        onSave(
                            Territory(
                                id = existingTerritory?.id ?: "",
                                congregationId = congregationId,
                                name = name.trim(),
                                description = description.trim().ifBlank { null },
                                assignedGroupId = selectedGroup?.id,
                                createdAt = existingTerritory?.createdAt ?: System.currentTimeMillis(),
                            )
                        )
                        onDismiss()
                    }
                },
            ) { Text(if (existingTerritory == null) "Create" else "Save") }
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
private fun GroupDropdown(groups: List<Group>, selected: Group?, onSelected: (Group) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Assigned Group (optional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onSelected(group)
                        expanded = false
                    },
                )
            }
        }
    }
}
