package com.emfitsolutions.gopreach.ui.screens.congregations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.LanguagesTagInput
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import kotlinx.coroutines.launch

/** Spec §3/§5.1 — Manage Congregation Master File, Super-Admin only.
 * [canPermanentlyDelete] is Super-Admin-only across every record type in
 * this app's "Admin Record Deletion" pass (see BUILD_PLAN.md).
 * [readOnly] — a restricted user (e.g. Circuit Overseer/custom) whose
 * [com.emfitsolutions.gopreach.data.model.UserAccessGrant] has
 * `VIEW_CONGREGATIONS` but not `ADD_CONGREGATIONS`/`EDIT_CONGREGATIONS`/
 * `DELETE_CONGREGATIONS` still needs somewhere to actually see this data —
 * this hides Add/Edit/Delete/Reactivate without duplicating the whole
 * screen (backlog: "read-only variants of the management screens"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCongregationsScreen(
    currentPersonId: String,
    canPermanentlyDelete: Boolean,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    viewModel: ManageCongregationsViewModel = hiltViewModel(),
) {
    val allCongregations by viewModel.congregations.collectAsStateWithLifecycle()
    var showInactive by remember { mutableStateOf(false) }
    val congregations = allCongregations.filter { showInactive || it.status == RecordStatus.ACTIVE }
    var pendingDelete by remember { mutableStateOf<Congregation?>(null) }
    var pendingEdit by remember { mutableStateOf<Congregation?>(null) }
    var permanentDeleteImpactSummary by remember { mutableStateOf<String?>(null) }
    var permanentDeleteChecked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
            if (!readOnly) {
                FloatingActionButton(onClick = onAddNew) {
                    Icon(Icons.Rounded.Add, contentDescription = "New Congregation")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = showInactive, onCheckedChange = { showInactive = it })
                Text("Show Inactive")
            }
            if (congregations.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No congregations yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                                    if (congregation.languages.isNotEmpty()) {
                                        Text("Languages: ${congregation.languages.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (congregation.status == RecordStatus.INACTIVE) {
                                        Text("Inactive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (!readOnly) {
                                    Row {
                                        IconButton(onClick = { pendingEdit = congregation }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                        }
                                        if (congregation.status == RecordStatus.ACTIVE) {
                                            IconButton(onClick = { pendingDelete = congregation }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                            }
                                        } else {
                                            IconButton(onClick = { viewModel.setStatus(congregation, RecordStatus.ACTIVE, currentPersonId) }) {
                                                Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Reactivate")
                                            }
                                        }
                                    }
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
        LaunchedEffect(toDelete.id) {
            permanentDeleteImpactSummary = viewModel.permanentDeleteImpactSummary(toDelete.id)
            permanentDeleteChecked = true
        }
        if (permanentDeleteChecked) {
            DeleteChoiceDialog(
                recordLabel = toDelete.name,
                canPermanentlyDelete = canPermanentlyDelete,
                permanentDeleteImpactSummary = permanentDeleteImpactSummary,
                onDismiss = { pendingDelete = null; permanentDeleteChecked = false },
                onMoveToInactive = { viewModel.setStatus(toDelete, RecordStatus.INACTIVE, currentPersonId) },
                onDeletePermanently = { viewModel.permanentlyDelete(toDelete.id, currentPersonId) },
            )
        }
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

/** Congregation's editable surface (Name/Address/Code) is already the whole
 * record's editable content — this adds the previously-missing read-only
 * System Information (record ID, status, date added) so the full stored
 * record is visible, not just what happens to be editable. */
@Composable
private fun EditCongregationDialog(
    congregation: Congregation,
    onSave: (Congregation) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(congregation.name) }
    var address by remember { mutableStateOf(congregation.address) }
    var code by remember { mutableStateOf(congregation.code) }
    var languages by remember { mutableStateOf(congregation.languages) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Congregation") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditSectionHeader("Congregation Information")
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
                LanguagesTagInput(
                    languages = languages,
                    onAdd = { languages = languages + it },
                    onRemove = { languages = languages - it },
                    modifier = Modifier.fillMaxWidth(),
                )

                EditSectionHeader("System Information")
                ReadOnlyField("Record ID", congregation.id)
                ReadOnlyField("Status", congregation.status.name)
                ReadOnlyField("Date Added", formatRecordTimestamp(congregation.createdAt))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && address.isNotBlank() && code.isNotBlank()) {
                        onSave(congregation.copy(name = name.trim(), address = address.trim(), code = code.trim(), languages = languages))
                    }
                },
            ) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
