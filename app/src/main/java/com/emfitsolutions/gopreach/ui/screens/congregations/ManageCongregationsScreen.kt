package com.emfitsolutions.gopreach.ui.screens.congregations

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
import androidx.compose.material3.ExperimentalMaterial3Api
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

/** Spec §3/§5.1 — Manage Congregation Master File, Super-Admin only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCongregationsScreen(
    currentPersonId: String,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    viewModel: ManageCongregationsViewModel = hiltViewModel(),
) {
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Congregation?>(null) }
    var pendingEdit by remember { mutableStateOf<Congregation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Congregations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Rounded.Add, contentDescription = "New Congregation")
            }
        },
    ) { padding ->
        if (congregations.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No congregations yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(congregations, key = { it.id }) { congregation ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(congregation.name, style = MaterialTheme.typography.titleMedium)
                                Text(congregation.address, style = MaterialTheme.typography.bodySmall)
                                Text("Code: ${congregation.code}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row {
                                IconButton(onClick = { pendingEdit = congregation }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { pendingDelete = congregation }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${toDelete.name}?") },
            text = { Text("This removes the congregation record. Admins, groups, and publishers already assigned to it are not automatically reassigned.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(toDelete.id, currentPersonId)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    val toEdit = pendingEdit
    if (toEdit != null) {
        EditCongregationDialog(
            congregation = toEdit,
            onSave = { updated ->
                viewModel.update(updated, currentPersonId)
                pendingEdit = null
            },
            onDismiss = { pendingEdit = null },
        )
    }
}

@Composable
private fun EditCongregationDialog(
    congregation: Congregation,
    onSave: (Congregation) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(congregation.name) }
    var address by remember { mutableStateOf(congregation.address) }
    var code by remember { mutableStateOf(congregation.code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Congregation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.uppercase() },
                    label = { Text("Congregation Name") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.uppercase() },
                    label = { Text("Address") },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Congregation Code") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && address.isNotBlank() && code.isNotBlank()) {
                        onSave(congregation.copy(name = name.trim(), address = address.trim(), code = code.trim()))
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
