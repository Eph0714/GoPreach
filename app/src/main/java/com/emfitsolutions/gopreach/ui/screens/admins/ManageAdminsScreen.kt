package com.emfitsolutions.gopreach.ui.screens.admins

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.RestoreFromTrash
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
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.ui.components.TempCredentialLookupDialog

/** Spec §3/§5.1 — Manage Admins, Super-Admin only. "Delete" deactivates rather
 * than erases the record (same pattern as Publishers' recategorize) — an
 * inactive Admin can be restored, and their history/audit trail stays intact. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAdminsScreen(
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    viewModel: ManageAdminsViewModel = hiltViewModel(),
) {
    val admins by viewModel.admins.collectAsStateWithLifecycle()
    var lookupTarget by remember { mutableStateOf<Person?>(null) }
    var pendingEdit by remember { mutableStateOf<AdminRow?>(null) }
    var pendingDeactivate by remember { mutableStateOf<AdminRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admins") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Rounded.Add, contentDescription = "Enroll Admin")
            }
        },
    ) { padding ->
        if (admins.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No admins enrolled yet. Tap + to enroll one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(admins, key = { it.person.id }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = row.person.isTemporaryCredential) { lookupTarget = row.person },
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                Text("Congregation: ${row.congregationName}", style = MaterialTheme.typography.bodySmall)
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
                                IconButton(onClick = { viewModel.setActive(row.assignment, true) }) {
                                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Restore")
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
        EditAdminDialog(
            row = toEdit,
            onSave = { updated ->
                viewModel.updatePerson(updated)
                pendingEdit = null
            },
            onDismiss = { pendingEdit = null },
        )
    }

    val toDeactivate = pendingDeactivate
    if (toDeactivate != null) {
        AlertDialog(
            onDismissRequest = { pendingDeactivate = null },
            title = { Text("Delete ${toDeactivate.person.fullName}?") },
            text = { Text("This deactivates their admin access. Their record and history stay intact and can be restored later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setActive(toDeactivate.assignment, false)
                    pendingDeactivate = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeactivate = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditAdminDialog(
    row: AdminRow,
    onSave: (Person) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by remember { mutableStateOf(row.person.address) }
    var contact by remember { mutableStateOf(row.person.contact) }
    var email by remember { mutableStateOf(row.person.email ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${row.person.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(row.person.copy(address = address.trim(), contact = contact.trim(), email = email.trim().ifBlank { null }))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
