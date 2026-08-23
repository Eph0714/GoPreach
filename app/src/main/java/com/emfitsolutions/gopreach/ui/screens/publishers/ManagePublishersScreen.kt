package com.emfitsolutions.gopreach.ui.screens.publishers

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RestoreFromTrash
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
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.TempCredentialLookupDialog
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

/** Spec §3/§5.1 — Manage Publishers (all categories).
 * [canPermanentlyDelete] is Super-Admin-only, per the "Admin Record Deletion"
 * spec's scoping decision (see BUILD_PLAN.md). "Move to Inactive" for a
 * Publisher is the pre-existing [PublisherCategory.REMOVED_PUBLISHER]
 * recategorization — Removed publishers are hidden unless "Show Inactive" is on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePublishersScreen(
    currentPersonId: String,
    visibleCongregationId: String?,
    canPermanentlyDelete: Boolean,
    /** A restricted user with `VIEW_PUBLISHERS` but not `MANAGE_PUBLISHERS` —
     * hides Add/Edit/Delete/Reactivate and the inline Category picker, which
     * otherwise changes data with no separate "edit" gesture to gate behind. */
    readOnly: Boolean = false,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    viewModel: ManagePublishersViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(visibleCongregationId) { viewModel.rowsFor(visibleCongregationId) }
    val allRows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showInactive by remember { mutableStateOf(false) }
    val rows = allRows.filter { showInactive || it.category != PublisherCategory.REMOVED_PUBLISHER }
    var lookupTarget by remember { mutableStateOf<Person?>(null) }
    var pendingEdit by remember { mutableStateOf<PublisherRow?>(null) }
    var pendingDelete by remember { mutableStateOf<PublisherRow?>(null) }
    var permanentDeleteBlockReason by remember { mutableStateOf<String?>(null) }
    var permanentDeleteChecked by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publishers") },
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
                    Icon(Icons.Rounded.Add, contentDescription = "Enroll Publisher")
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
                Text("Show Inactive (Removed)")
            }
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No publishers enrolled yet. Tap + to enroll one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.person.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    if (row.person.isTemporaryCredential && !readOnly) {
                                        IconButton(onClick = { lookupTarget = row.person }) {
                                            Icon(
                                                Icons.Rounded.Key,
                                                contentDescription = "View temporary sign-in",
                                                tint = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                    }
                                    if (!readOnly) {
                                        IconButton(onClick = { pendingEdit = row }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                        }
                                        if (row.category != PublisherCategory.REMOVED_PUBLISHER) {
                                            IconButton(onClick = { pendingDelete = row }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                            }
                                        } else {
                                            IconButton(onClick = { viewModel.changeCategory(row, PublisherCategory.REGULAR_PUBLISHER, currentPersonId) }) {
                                                Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Reactivate")
                                            }
                                        }
                                    }
                                }
                            }
                            Text("Group: ${row.groupName}", style = MaterialTheme.typography.bodySmall)
                            Text("Contact: ${row.person.contact}", style = MaterialTheme.typography.bodySmall)
                            if (readOnly) {
                                ReadOnlyField("Category", row.category.name.replace('_', ' '))
                            } else {
                                CategoryDropdown(
                                    selected = row.category,
                                    onSelected = { newCategory -> viewModel.changeCategory(row, newCategory, currentPersonId) },
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    lookupTarget?.let { person ->
        TempCredentialLookupDialog(person = person, onDismiss = { lookupTarget = null })
    }

    val toEdit = pendingEdit
    if (toEdit != null) {
        EditPublisherDialog(
            row = toEdit,
            onSave = { updated ->
                viewModel.updatePerson(updated)
                pendingEdit = null
            },
            onDismiss = { pendingEdit = null },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        LaunchedEffect(toDelete.person.id) {
            permanentDeleteBlockReason = viewModel.permanentDeleteBlockReason(toDelete.person.id)
            permanentDeleteChecked = true
        }
        if (permanentDeleteChecked) {
            DeleteChoiceDialog(
                recordLabel = toDelete.person.fullName,
                canPermanentlyDelete = canPermanentlyDelete,
                permanentDeleteBlockedReason = permanentDeleteBlockReason,
                onDismiss = { pendingDelete = null; permanentDeleteChecked = false },
                onMoveToInactive = { viewModel.changeCategory(toDelete, PublisherCategory.REMOVED_PUBLISHER, currentPersonId) },
                onDeletePermanently = { viewModel.permanentlyDelete(toDelete, currentPersonId) },
            )
        }
    }
}

/** Shows the complete stored Publisher record when editing — not just Address/
 * Contact — organized into Personal Information (editable), Assignment and
 * System Information (read-only: category/group already have their own
 * dedicated controls elsewhere on this screen; username/creation are
 * system-generated). */
@Composable
private fun EditPublisherDialog(
    row: PublisherRow,
    onSave: (Person) -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf(row.person.firstName) }
    var lastName by remember { mutableStateOf(row.person.lastName) }
    var email by remember { mutableStateOf(row.person.email.orEmpty()) }
    var address by remember { mutableStateOf(row.person.address) }
    var contact by remember { mutableStateOf(row.person.contact) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${row.person.fullName}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditSectionHeader("Personal Information")
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it.uppercase() },
                    label = { Text("First Name") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it.uppercase() },
                    label = { Text("Last Name") },
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
                    value = contact,
                    onValueChange = { contact = it.uppercase() },
                    label = { Text("Contact") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (optional)") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )

                EditSectionHeader("Assignment")
                ReadOnlyField("Category", row.category.name.replace('_', ' '))
                ReadOnlyField("Group", row.groupName)

                EditSectionHeader("System Information")
                ReadOnlyField("Username", row.person.username)
                ReadOnlyField("Account Status", row.person.accountStatus.name)
                ReadOnlyField("Date Added", formatRecordTimestamp(row.person.createdAt))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (firstName.isNotBlank() && lastName.isNotBlank() && address.isNotBlank() && contact.isNotBlank()) {
                        onSave(
                            row.person.copy(
                                firstName = firstName.trim(),
                                lastName = lastName.trim(),
                                address = address.trim(),
                                contact = contact.trim(),
                                email = email.trim().ifBlank { null },
                            ),
                        )
                    }
                },
            ) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(selected: PublisherCategory, onSelected: (PublisherCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
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
