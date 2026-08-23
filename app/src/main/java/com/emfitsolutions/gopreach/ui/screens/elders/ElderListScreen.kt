package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.TempCredentialLookupDialog
import com.emfitsolutions.gopreach.ui.components.displayLabel
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

/** Shared list UI for [ManageCoordinatorEldersScreen] and [ManageRegularEldersScreen] —
 * same card layout as Manage Admins (name/scope/contact/edit/delete(deactivate)/
 * temp-credential lookup), just parameterized by title and what "scope" means
 * for that role. [canPermanentlyDelete] is Super-Admin-only, per the "Admin
 * Record Deletion" spec's scoping decision (see BUILD_PLAN.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderListScreen(
    title: String,
    scopeLabel: String,
    rows: List<ElderRow>,
    canPermanentlyDelete: Boolean,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onSetActive: (ElderRow, Boolean) -> Unit,
    onEdit: (ElderRow, Person) -> Unit,
    onPermanentlyDelete: (ElderRow) -> Unit,
) {
    var showInactive by remember { mutableStateOf(false) }
    val visibleRows = rows.filter { showInactive || it.isActive }
    var lookupTarget by remember { mutableStateOf<Person?>(null) }
    var pendingEdit by remember { mutableStateOf<ElderRow?>(null) }
    var pendingDeactivate by remember { mutableStateOf<ElderRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Rounded.Add, contentDescription = "Enroll $title")
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
        if (visibleRows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("None enrolled yet. Tap + to enroll one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleRows, key = { it.person.id }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = row.person.isTemporaryCredential) { lookupTarget = row.person },
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                if (row.regularElderRole != null) {
                                    Text("Role: ${row.regularElderRole.displayLabel()}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("$scopeLabel: ${row.scopeName}", style = MaterialTheme.typography.bodySmall)
                                Text("Contact: ${row.person.contact}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (row.isActive) "Active" else "Inactive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (row.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                                if (row.person.isTemporaryCredential) {
                                    Text(
                                        "Tap to view temporary sign-in",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            IconButton(onClick = { pendingEdit = row }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                            }
                            if (row.isActive) {
                                IconButton(onClick = { pendingDeactivate = row }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                }
                            } else {
                                IconButton(onClick = { onSetActive(row, true) }) {
                                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Restore")
                                }
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
        EditElderDialog(
            row = toEdit,
            scopeLabel = scopeLabel,
            onSave = { updated ->
                onEdit(toEdit, updated)
                pendingEdit = null
            },
            onDismiss = { pendingEdit = null },
        )
    }

    val toDeactivate = pendingDeactivate
    if (toDeactivate != null) {
        DeleteChoiceDialog(
            recordLabel = toDeactivate.person.fullName,
            canPermanentlyDelete = canPermanentlyDelete,
            onDismiss = { pendingDeactivate = null },
            onMoveToInactive = { onSetActive(toDeactivate, false) },
            onDeletePermanently = { onPermanentlyDelete(toDeactivate) },
        )
    }
}

/** Shows the complete stored Elder record when editing, not just Address/
 * Contact — Personal Information is editable; Assignment and System
 * Information are read-only (Group role/reassignment and active/inactive
 * status already have their own dedicated controls elsewhere). */
@Composable
private fun EditElderDialog(
    row: ElderRow,
    scopeLabel: String,
    onSave: (Person) -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf(row.person.firstName) }
    var lastName by remember { mutableStateOf(row.person.lastName) }
    var address by remember { mutableStateOf(row.person.address) }
    var contact by remember { mutableStateOf(row.person.contact) }
    var email by remember { mutableStateOf(row.person.email ?: "") }

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
                ReadOnlyField(scopeLabel, row.scopeName)
                if (row.regularElderRole != null) {
                    ReadOnlyField("Group Role", row.regularElderRole.displayLabel())
                }

                EditSectionHeader("System Information")
                ReadOnlyField("Username", row.person.username)
                ReadOnlyField("Status", if (row.isActive) "Active" else "Inactive")
                ReadOnlyField("Date Added", formatRecordTimestamp(row.person.createdAt))
            }
        },
        confirmButton = {
            TextButton(onClick = {
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
            }) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
