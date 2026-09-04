package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RegularElderRole
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import kotlinx.coroutines.flow.first

@Composable
fun ManageRegularEldersScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    canPermanentlyDelete: Boolean,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    viewModel: ManageRegularEldersViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    ElderListScreen(
        title = "Regular Elders",
        scopeLabel = "Group",
        rows = rows,
        canPermanentlyDelete = canPermanentlyDelete,
        readOnly = readOnly,
        onBack = onBack,
        onAddNew = onAddNew,
        onSetActive = { row, active -> viewModel.setActive(row.assignment, active, currentPersonId) },
        onEdit = { _, updated -> viewModel.updatePerson(updated) },
        onPermanentlyDelete = { row -> viewModel.permanentlyDelete(row, currentPersonId) },
        editDialogContent = { row, onDismiss ->
            // "Allow the Role to be edited" — a dedicated editor (Personal
            // Information + Select Role), not the generic Personal-
            // Information-only EditElderDialog every other Manage Elder
            // screen without one still uses.
            RegularElderEditDialog(row = row, currentPersonId = currentPersonId, viewModel = viewModel, onDismiss = onDismiss)
        },
    )
}

@Composable
private fun RegularElderEditDialog(
    row: ElderRow,
    currentPersonId: String,
    viewModel: ManageRegularEldersViewModel,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf(row.person.firstName) }
    var lastName by remember { mutableStateOf(row.person.lastName) }
    var address by remember { mutableStateOf(row.person.address) }
    var contact by remember { mutableStateOf(row.person.contact) }
    var email by remember { mutableStateOf(row.person.email ?: "") }

    // Bug fix ("the checkbox for user role is not checked for the current
    // state when Editing"): same root cause as Coordinator Elder/Service
    // Overseer's own edit dialogs — seeding [publisherCategory] from
    // `collectAsStateWithLifecycle(initialValue = null)` guarded by a
    // `rolesLoaded` flag checked directly in the composable body locked in
    // that `null` placeholder before [viewModel.publisherCategoryFor]'s real
    // Firestore-backed value had a chance to arrive. `LaunchedEffect` +
    // `.first()` suspends for the flow's genuine first emission instead.
    // [isGroupOverseer] is unaffected — it's seeded directly from [row]
    // itself, already loaded synchronously, not from a separate flow.
    val publisherCategoryFlow = remember(row.person.id) { viewModel.publisherCategoryFor(row.person.id) }
    var isGroupOverseer by remember { mutableStateOf(row.regularElderRole == RegularElderRole.GROUP_OVERSEER) }
    var publisherCategory by remember { mutableStateOf<PublisherCategory?>(null) }
    LaunchedEffect(publisherCategoryFlow) {
        publisherCategory = publisherCategoryFlow.first()
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val message = requiredFieldsMessage(
            "First Name" to firstName.isNotBlank(),
            "Last Name" to lastName.isNotBlank(),
            "Address" to address.isNotBlank(),
            "Contact" to contact.isNotBlank(),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        viewModel.updateRoleAndPerson(
            row = row,
            updatedPerson = row.person.copy(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                address = address.trim(),
                contact = contact.trim(),
                email = email.trim().ifBlank { null },
            ),
            isGroupOverseer = isGroupOverseer,
            publisherCategory = publisherCategory,
            actorPersonId = currentPersonId,
        )
        onDismiss()
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = "Edit ${row.person.fullName}",
        onConfirm = ::submit,
        confirmLabel = "Save Changes",
        errorMessage = errorMessage,
        maxContentHeight = 560.dp,
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
                ReadOnlyField("Group", row.scopeName)

                HorizontalDivider()
                Text("Select Role", style = MaterialTheme.typography.titleSmall)
                Text(
                    "In addition to Regular Elder — Group Overseer and a publisher category can both apply at once, but only one publisher category at a time.",
                    style = MaterialTheme.typography.bodySmall,
                )
                RegularElderRoleCheckboxRow(
                    label = "Group Overseer",
                    checked = isGroupOverseer,
                    onCheckedChange = { isGroupOverseer = it },
                )
                RegularElderRoleCheckboxRow(
                    label = "Regular Pioneer",
                    checked = publisherCategory == PublisherCategory.REGULAR_PIONEER,
                    enabled = publisherCategory == null || publisherCategory == PublisherCategory.REGULAR_PIONEER,
                    onCheckedChange = { checked -> publisherCategory = if (checked) PublisherCategory.REGULAR_PIONEER else null },
                )
                RegularElderRoleCheckboxRow(
                    label = "Auxiliary Pioneer",
                    checked = publisherCategory == PublisherCategory.AUXILIARY_PIONEER,
                    enabled = publisherCategory == null || publisherCategory == PublisherCategory.AUXILIARY_PIONEER,
                    onCheckedChange = { checked -> publisherCategory = if (checked) PublisherCategory.AUXILIARY_PIONEER else null },
                )
                RegularElderRoleCheckboxRow(
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
}

/** Same "checking one disables/unchecks the others" checkbox row shape
 * ManageServiceOverseersScreen's own local copy uses. */
@Composable
private fun RegularElderRoleCheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
