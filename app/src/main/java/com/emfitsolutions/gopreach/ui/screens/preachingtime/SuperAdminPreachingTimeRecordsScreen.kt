package com.emfitsolutions.gopreach.ui.screens.preachingtime

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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PreachingTimeRecord
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.ui.components.CongregationFilterDropdown
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.DateTimeField
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Preaching Time Records — Super Admin Management Module" — the Super-
 * Admin's own "All Congregations" view of every Publisher's Preaching Time
 * Record (spec §1/§10), with a Congregation filter (§2), search (§3),
 * Add/Edit (§4/§5) via a Congregation -> Publisher cascading picker, normal
 * Delete (§6, reusing [com.emfitsolutions.gopreach.ui.components
 * .DeleteChoiceDialog]'s existing Move-to-Inactive/Delete-Permanently pair),
 * and a separate, more strongly-worded Force Delete (§7-§9). Only ever
 * reachable by a Super-Admin session — see SidePanel's `isSuperAdmin` gate —
 * so every action here is unconditionally available; the real, unbypassable
 * enforcement of "only Super-Admin may delete" lives in firestore.rules'
 * `preachingTimeRecords` rule, not in any flag on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminPreachingTimeRecordsScreen(
    currentPersonId: String,
    currentPersonRoleLabel: String,
    onBack: () -> Unit,
    viewModel: SuperAdminPreachingTimeRecordsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showToast = rememberActionToast()

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<SuperAdminPreachingTimeRow?>(null) }
    var pendingDelete by remember { mutableStateOf<SuperAdminPreachingTimeRow?>(null) }
    var pendingForceDelete by remember { mutableStateOf<SuperAdminPreachingTimeRow?>(null) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preaching Time Records") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Preaching Time Record")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Spec §2 — "All Congregations -> Specific Congregation", loaded
                // dynamically from the database (this list is [uiState
                // .congregations], sourced from CongregationRepository, never a
                // hardcoded sample).
                CongregationFilterDropdown(
                    congregations = uiState.congregations,
                    selectedCongregationId = uiState.selectedCongregationId,
                    onSelected = viewModel::selectCongregation,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateRangeFilterBar(range = uiState.dateRange, onRangeChange = viewModel::setDateRange)
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    label = { Text("Search Publisher name, Publisher ID, or Congregation") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.showInactive, onCheckedChange = viewModel::setShowInactive)
                    Text("Show Inactive")
                }
                Text(
                    "Total: ${"%.2f".format(uiState.totalHours)} hours across ${uiState.rows.size} record${if (uiState.rows.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (uiState.rows.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (uiState.isLoading) "Loading…" else "No Preaching Time Records match the current filters.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.rows, key = { it.record.id }) { row ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                    Row {
                                        IconButton(onClick = { pendingEdit = row }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                        }
                                        if (row.record.status == RecordStatus.ACTIVE) {
                                            IconButton(onClick = { pendingDelete = row }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                            }
                                        } else {
                                            IconButton(onClick = {
                                                viewModel.setStatus(row.record, RecordStatus.ACTIVE, currentPersonId)
                                                showToast("Record reactivated.")
                                            }) {
                                                Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Reactivate")
                                            }
                                        }
                                        // Spec §7 — a separate action from Delete
                                        // above, always available to this
                                        // Super-Admin-only screen, for records a
                                        // normal delete can't clear (related
                                        // data, sync problems, corruption).
                                        IconButton(onClick = { pendingForceDelete = row }) {
                                            Icon(Icons.Rounded.DeleteForever, contentDescription = "Force Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                // Congregation column — spec §13: "clearly
                                // display the Congregation so it's obvious
                                // which congregation each record belongs to."
                                Text("Congregation: ${row.congregationName}", style = MaterialTheme.typography.bodySmall)
                                if (row.category != null) {
                                    Text("Status Category: ${row.category.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(dateFormat.format(Date(row.record.date)), style = MaterialTheme.typography.bodyMedium)
                                Text("${"%.2f".format(row.record.hoursConsumed)} hours", style = MaterialTheme.typography.bodyMedium)
                                if (row.record.remarks != null) {
                                    Text(row.record.remarks, style = MaterialTheme.typography.bodySmall)
                                }
                                if (row.record.status == RecordStatus.INACTIVE) {
                                    Text("Inactive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SuperAdminPreachingTimeDialog(
            existing = null,
            currentPersonId = currentPersonId,
            viewModel = viewModel,
            onSave = { record ->
                viewModel.save(record, currentPersonId, isNew = true)
                showToast("Preaching Time Record added successfully.")
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    val toEdit = pendingEdit
    if (toEdit != null) {
        SuperAdminPreachingTimeDialog(
            existing = toEdit,
            currentPersonId = currentPersonId,
            viewModel = viewModel,
            onSave = { record ->
                viewModel.save(record, currentPersonId, isNew = false)
                showToast("Preaching Time Record updated successfully.")
                pendingEdit = null
            },
            onDismiss = { pendingEdit = null },
        )
    }

    // Spec §6 — plain "Are you sure...?" / Cancel / Delete, reusing the
    // Move-to-Inactive/Delete-Permanently pair every other Manage screen's
    // DeleteChoiceDialog already provides (Delete Permanently is Super-Admin
    // only everywhere else it appears; unconditionally offered here since
    // this whole screen already is Super-Admin-only).
    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Preaching Time Record") },
            text = { Text("Are you sure you want to delete this Preaching Time Record?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setStatus(toDelete.record, RecordStatus.INACTIVE, currentPersonId)
                    showToast("Preaching Time Record deleted successfully.")
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    // Spec §7 — the strong, explicit warning, separate from the normal
    // Delete confirmation above.
    val toForceDelete = pendingForceDelete
    if (toForceDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingForceDelete = null },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Force Delete Warning") },
            text = {
                Text(
                    "FORCE DELETE WARNING: This will permanently remove this Preaching Time Record and its " +
                        "related dependent data where applicable. This action cannot be undone. Continue?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forceDelete(
                        record = toForceDelete.record,
                        person = toForceDelete.person,
                        congregationName = toForceDelete.congregationName,
                        actorPersonId = currentPersonId,
                        actorRoleLabel = currentPersonRoleLabel,
                    )
                    showToast("Preaching Time Record permanently deleted.")
                    pendingForceDelete = null
                }) { Text("Force Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingForceDelete = null }) { Text("Cancel") } },
        )
    }
}

/** Add/Edit — Add shows a Congregation -> Publisher cascading picker (spec
 * §4: a Super-Admin has no "own congregation" to default to, unlike the
 * Publisher's own [PreachingTimeRecordScreen]); Edit shows that same
 * relationship read-only instead (spec §5: "validate the Publisher/
 * Congregation relationship... save only the intended changes" — neither is
 * ever re-pointed by an edit). Date/Hours/Remarks are the same fields and
 * the same validation as the Publisher's own Add/Edit dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuperAdminPreachingTimeDialog(
    existing: SuperAdminPreachingTimeRow?,
    currentPersonId: String,
    viewModel: SuperAdminPreachingTimeRecordsViewModel,
    onSave: (PreachingTimeRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var congregationId by remember { mutableStateOf(existing?.record?.congregationId) }
    var publisher by remember { mutableStateOf(existing?.person) }
    val publishersFlow = remember(congregationId) { viewModel.publishersFor(congregationId) }
    val publishers by publishersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val initialDate = remember { existing?.record?.date?.takeIf { it != 0L } ?: System.currentTimeMillis() }
    var date by remember { mutableStateOf(initialDate) }
    var hoursText by remember { mutableStateOf(existing?.record?.hoursConsumed?.toString().orEmpty()) }
    var remarks by remember { mutableStateOf(existing?.record?.remarks.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val hours = hoursText.toDoubleOrNull()
    val canSaveHours = hours != null && hours > 0.0

    fun submit() {
        val message = requiredFieldsMessage(
            "Congregation" to (congregationId != null),
            "Publisher" to (publisher != null),
            "Hour Consumed" to canSaveHours,
        )
        if (message != null) {
            errorMessage = message
            return
        }
        val now = System.currentTimeMillis()
        val base = existing?.record ?: PreachingTimeRecord(
            publisherPersonId = publisher!!.id,
            congregationId = congregationId!!,
            createdByPersonId = currentPersonId,
            createdAt = now,
        )
        onSave(
            base.copy(
                date = date,
                hoursConsumed = hours!!,
                remarks = remarks.trim().ifBlank { null },
                lastEditedByPersonId = if (existing != null) currentPersonId else base.lastEditedByPersonId,
                lastEditedAt = if (existing != null) now else base.lastEditedAt,
            ),
        )
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "Add Preaching Time Record" else "Edit Preaching Time Record",
        onConfirm = ::submit,
        confirmLabel = if (existing == null) "Add" else "Save",
        errorMessage = errorMessage,
        maxContentHeight = 600.dp,
        hasUnsavedChanges = if (existing == null) {
            congregationId != null || publisher != null || hoursText.isNotBlank() || remarks.isNotBlank()
        } else {
            date != initialDate || hoursText != existing.record.hoursConsumed.toString() || remarks != existing.record.remarks.orEmpty()
        },
    ) {
        if (existing == null) {
            CongregationPickerDropdown(
                congregations = uiState.congregations,
                selectedId = congregationId,
                onSelected = { id ->
                    congregationId = id
                    publisher = null
                    errorMessage = null
                },
            )
            PublisherPickerDropdown(
                publishers = publishers,
                selectedId = publisher?.id,
                enabled = congregationId != null,
                onSelected = { id ->
                    publisher = publishers.firstOrNull { it.id == id }
                    errorMessage = null
                },
            )
        } else {
            EditSectionHeader("Publisher & Congregation")
            ReadOnlyField("Publisher", existing.person.fullName)
            ReadOnlyField("Congregation", existing.congregationName)
        }
        DateTimeField(label = "Date", valueMillis = date, onValueChange = { date = it })
        OutlinedTextField(
            value = hoursText,
            onValueChange = { hoursText = it.filter { c -> c.isDigit() || c == '.' }; errorMessage = null },
            label = { Text("Hour Consumed") },
            placeholder = { Text("e.g. 2.5") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = { Text("Remarks (optional)") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        if (existing != null) {
            EditSectionHeader("System Information")
            ReadOnlyField("Recorded By", existing.record.createdByPersonId.ifBlank { "—" })
            ReadOnlyField("Date Created", formatRecordTimestamp(existing.record.createdAt))
            if (existing.record.lastEditedAt != null) {
                ReadOnlyField("Updated By", existing.record.lastEditedByPersonId ?: "—")
                ReadOnlyField("Date Updated", formatRecordTimestamp(existing.record.lastEditedAt))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CongregationPickerDropdown(
    congregations: List<com.emfitsolutions.gopreach.data.model.Congregation>,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: "Select Congregation"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Congregation *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            congregations.forEach { congregation ->
                DropdownMenuItem(text = { Text(congregation.name) }, onClick = { onSelected(congregation.id); expanded = false })
            }
        }
    }
}

/** Publisher dropdown for the Add flow — filtered to whichever Congregation
 * was picked above (spec §4: "The Publisher dropdown should be filtered
 * according to the selected Congregation"); disabled with a hint until one
 * is, same convention as [com.emfitsolutions.gopreach.ui.components
 * .PhilippineAddressPicker]'s own cascading dropdowns. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublisherPickerDropdown(
    publishers: List<Person>,
    selectedId: String?,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = publishers.firstOrNull { it.id == selectedId }?.fullName ?: "Select Publisher"
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Publisher *") },
            supportingText = if (!enabled) { { Text("Select Congregation first") } } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            if (publishers.isEmpty()) {
                DropdownMenuItem(text = { Text("No Publishers in this Congregation") }, onClick = {}, enabled = false)
            }
            publishers.forEach { person ->
                DropdownMenuItem(text = { Text(person.fullName) }, onClick = { onSelected(person.id); expanded = false })
            }
        }
    }
}
