package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

@Composable
fun ManageServiceOverseersScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    canPermanentlyDelete: Boolean,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    viewModel: ManageServiceOverseersViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()

    ElderListScreen(
        title = "Service Overseers",
        scopeLabel = "Congregation",
        rows = rows,
        canPermanentlyDelete = canPermanentlyDelete,
        readOnly = readOnly,
        onBack = onBack,
        onAddNew = onAddNew,
        onSetActive = { row, active -> viewModel.setActive(row.assignment, active, currentPersonId) },
        onEdit = { _, updated -> viewModel.updatePerson(updated) },
        onPermanentlyDelete = { row -> viewModel.permanentlyDelete(row, currentPersonId) },
        editDialogContent = { row, onDismiss ->
            // "In editing Service overseer, allow the user to edit also the
            // roles, and other details" — a dedicated editor (Congregation +
            // Select Role, on top of Personal Information), not the generic
            // Personal-Information-only EditElderDialog every other Manage
            // Elder screen still uses.
            ServiceOverseerEditDialog(
                row = row,
                fixedCongregationId = fixedCongregationId,
                congregations = congregations,
                currentPersonId = currentPersonId,
                viewModel = viewModel,
                onDismiss = onDismiss,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceOverseerEditDialog(
    row: ElderRow,
    fixedCongregationId: String?,
    congregations: List<Congregation>,
    currentPersonId: String,
    viewModel: ManageServiceOverseersViewModel,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf(row.person.firstName) }
    var lastName by remember { mutableStateOf(row.person.lastName) }
    var address by remember { mutableStateOf(row.person.address) }
    var contact by remember { mutableStateOf(row.person.contact) }
    var email by remember { mutableStateOf(row.person.email ?: "") }
    var pickedCongregationId by remember { mutableStateOf(row.assignment.congregationId) }
    val congregationId = fixedCongregationId ?: pickedCongregationId

    val additionalRolesFlow = remember(row.person.id) { viewModel.additionalRolesFor(row.person.id) }
    val additionalRoles by additionalRolesFlow.collectAsStateWithLifecycle(initialValue = false to null)
    var rolesLoaded by remember { mutableStateOf(false) }
    var isGroupOverseer by remember { mutableStateOf(false) }
    var publisherCategory by remember { mutableStateOf<PublisherCategory?>(null) }
    if (!rolesLoaded) {
        isGroupOverseer = additionalRoles.first
        publisherCategory = additionalRoles.second
        rolesLoaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${row.person.fullName}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
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
                if (fixedCongregationId == null) {
                    ServiceOverseerCongregationDropdown(
                        congregations = congregations,
                        selectedId = pickedCongregationId,
                        onSelected = { pickedCongregationId = it },
                    )
                } else {
                    ReadOnlyField("Congregation", row.scopeName)
                }

                HorizontalDivider()
                Text("Select Role", style = MaterialTheme.typography.titleSmall)
                Text(
                    "In addition to Service Overseer — Group Overseer and a publisher category can both apply at once, but only one publisher category at a time.",
                    style = MaterialTheme.typography.bodySmall,
                )
                RoleCheckboxRowLocal(
                    label = "Group Overseer",
                    checked = isGroupOverseer,
                    onCheckedChange = { isGroupOverseer = it },
                )
                RoleCheckboxRowLocal(
                    label = "Regular Pioneer",
                    checked = publisherCategory == PublisherCategory.REGULAR_PIONEER,
                    enabled = publisherCategory == null || publisherCategory == PublisherCategory.REGULAR_PIONEER,
                    onCheckedChange = { checked -> publisherCategory = if (checked) PublisherCategory.REGULAR_PIONEER else null },
                )
                RoleCheckboxRowLocal(
                    label = "Auxiliary Pioneer",
                    checked = publisherCategory == PublisherCategory.AUXILIARY_PIONEER,
                    enabled = publisherCategory == null || publisherCategory == PublisherCategory.AUXILIARY_PIONEER,
                    onCheckedChange = { checked -> publisherCategory = if (checked) PublisherCategory.AUXILIARY_PIONEER else null },
                )
                RoleCheckboxRowLocal(
                    label = "Regular Publisher",
                    checked = publisherCategory == PublisherCategory.REGULAR_PUBLISHER,
                    enabled = publisherCategory == null || publisherCategory == PublisherCategory.REGULAR_PUBLISHER,
                    onCheckedChange = { checked -> publisherCategory = if (checked) PublisherCategory.REGULAR_PUBLISHER else null },
                )

                EditSectionHeader("System Information")
                ReadOnlyField("Username", row.person.username)
                ReadOnlyField("Status", if (row.isActive) "Active" else "Inactive")
                ReadOnlyField("Date Added", formatRecordTimestamp(row.person.createdAt))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (firstName.isNotBlank() && lastName.isNotBlank() && address.isNotBlank() && contact.isNotBlank() && congregationId != null) {
                        viewModel.updateRolesAndPerson(
                            row = row,
                            updatedPerson = row.person.copy(
                                firstName = firstName.trim(),
                                lastName = lastName.trim(),
                                address = address.trim(),
                                contact = contact.trim(),
                                email = email.trim().ifBlank { null },
                            ),
                            newCongregationId = congregationId,
                            isGroupOverseer = isGroupOverseer,
                            publisherCategory = publisherCategory,
                            actorPersonId = currentPersonId,
                        )
                        onDismiss()
                    }
                },
            ) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceOverseerCongregationDropdown(congregations: List<Congregation>, selectedId: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Congregation") },
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

/** Same "checking one disables/unchecks the others" checkbox row shape
 * CoordinatorElderEnrollmentScreen's internal RoleCheckboxRow already uses —
 * not reused directly since that one lives in the `enrollment` package. */
@Composable
private fun RoleCheckboxRowLocal(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
