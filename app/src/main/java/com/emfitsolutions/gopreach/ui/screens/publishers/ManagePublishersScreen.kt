package com.emfitsolutions.gopreach.ui.screens.publishers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.emfitsolutions.gopreach.data.model.AccountStatus
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Gender
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.TempCredentialLookupDialog
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage

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
    // Super-Admin only (visibleCongregationId == null from the caller) — an
    // Admin/Coordinator Elder is already scoped to their own single
    // congregation upstream, so there's nothing for them to filter.
    var congregationFilter by remember { mutableStateOf<String?>(null) }
    val effectiveCongregationId = visibleCongregationId ?: congregationFilter
    val congregations by viewModel.congregations.collectAsStateWithLifecycle(initialValue = emptyList())
    val rowsFlow = remember(effectiveCongregationId) { viewModel.rowsFor(effectiveCongregationId) }
    val allRows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showInactive by remember { mutableStateOf(false) }
    val rows = allRows.filter { showInactive || it.category != PublisherCategory.REMOVED_PUBLISHER }
    var lookupTarget by remember { mutableStateOf<Person?>(null) }
    var pendingEdit by remember { mutableStateOf<PublisherRow?>(null) }
    var pendingDelete by remember { mutableStateOf<PublisherRow?>(null) }
    val showToast = rememberActionToast()
    var permanentDeleteImpactSummary by remember { mutableStateOf<String?>(null) }
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
            // "Publisher UI – Make the Main Button Larger and More
            // Professional" spec — an ExtendedFloatingActionButton (icon +
            // short label) rather than the plain icon-only FAB every other
            // Manage screen still uses: larger touch target, clearer intent
            // at a glance, still Material3-standard corner radius/elevation/
            // ripple/typography, so it reads as "more polished," not "bigger
            // for its own sake" (explicitly not wanted per the spec).
            if (!readOnly) {
                ExtendedFloatingActionButton(
                    onClick = onAddNew,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("ADD PUBLISHER") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (visibleCongregationId == null) {
                CongregationFilterDropdown(
                    congregations = congregations,
                    selectedId = congregationFilter,
                    onSelected = { congregationFilter = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
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
                                            IconButton(
                                                onClick = {
                                                    viewModel.changeCategory(row, PublisherCategory.REGULAR_PUBLISHER, currentPersonId)
                                                    showToast("\"${row.person.fullName}\" reactivated.")
                                                },
                                            ) {
                                                Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Reactivate")
                                            }
                                        }
                                    }
                                }
                            }
                            Text("Group: ${row.groupName}", style = MaterialTheme.typography.bodySmall)
                            Text("Contact: ${row.person.contact}", style = MaterialTheme.typography.bodySmall)
                            if (row.possibleDuplicateOf != null) {
                                // "Check if there are duplicate names,
                                // evaluate it if they are the same person" —
                                // a heads-up only; nothing here merges or
                                // deletes anything automatically. The admin
                                // reviews both records (Edit/Delete icons
                                // above) and decides.
                                Text(
                                    "⚠ Possible duplicate of \"${row.possibleDuplicateOf}\" — review both records.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
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
            currentPersonId = currentPersonId,
            viewModel = viewModel,
            onDismiss = { pendingEdit = null },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        LaunchedEffect(toDelete.person.id) {
            permanentDeleteImpactSummary = viewModel.permanentDeleteImpactSummary(toDelete.person.id)
            permanentDeleteChecked = true
        }
        if (permanentDeleteChecked) {
            DeleteChoiceDialog(
                recordLabel = toDelete.person.fullName,
                canPermanentlyDelete = canPermanentlyDelete,
                permanentDeleteImpactSummary = permanentDeleteImpactSummary,
                onDismiss = { pendingDelete = null; permanentDeleteChecked = false },
                onMoveToInactive = { viewModel.changeCategory(toDelete, PublisherCategory.REMOVED_PUBLISHER, currentPersonId) },
                onDeletePermanently = { viewModel.permanentlyDelete(toDelete, currentPersonId) },
            )
        }
    }
}

/** Shows the complete stored Publisher record when editing — every Person
 * field, plus Category and Group (spec: "enable the user to edit all
 * entities like the Groups and all data"), not just Address/Contact. Category/
 * Group changes go through [ManagePublishersViewModel.changeCategory]/
 * [changeGroup] (they live on the [RoleAssignment], not the [Person] document)
 * while every other field saves through [ManagePublishersViewModel.updatePerson]
 * — both fire from this one Save Changes button so editing here still feels
 * like one record, not three separate saves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPublisherDialog(
    row: PublisherRow,
    currentPersonId: String,
    viewModel: ManagePublishersViewModel,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf(row.person.firstName) }
    var lastName by remember { mutableStateOf(row.person.lastName) }
    var middleInitial by remember { mutableStateOf(row.person.middleInitial.orEmpty()) }
    var extensionName by remember { mutableStateOf(row.person.extensionName.orEmpty()) }
    var gender by remember { mutableStateOf(row.person.gender) }
    var email by remember { mutableStateOf(row.person.email.orEmpty()) }
    var address by remember { mutableStateOf(row.person.address) }
    var contact by remember { mutableStateOf(row.person.contact) }
    var contactPerson by remember { mutableStateOf(row.person.contactPerson.orEmpty()) }
    var contactPersonNumber by remember { mutableStateOf(row.person.contactPersonNumber.orEmpty()) }
    var category by remember { mutableStateOf(row.category) }
    var groupId by remember { mutableStateOf(row.assignment.groupId) }
    // "Allow the user to Edit the Publishers Status" — this was a read-only
    // System Information field; the account's actual sign-in eligibility,
    // distinct from [category] (which already covers Publisher/Pioneer/
    // Reproof/etc. standing) — see AccountStatus's own doc comment.
    var accountStatus by remember { mutableStateOf(row.person.accountStatus) }
    val showToast = rememberActionToast()

    val groupsFlow = remember(row.assignment.congregationId) { viewModel.groupsFor(row.assignment.congregationId) }
    val groups by groupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

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
        viewModel.updatePerson(
            row.person.copy(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                middleInitial = middleInitial.trim().ifBlank { null },
                extensionName = extensionName.trim().ifBlank { null },
                gender = gender,
                address = address.trim(),
                contact = contact.trim(),
                email = email.trim().ifBlank { null },
                contactPerson = contactPerson.trim().ifBlank { null },
                contactPersonNumber = contactPersonNumber.trim().ifBlank { null },
                accountStatus = accountStatus,
            ),
        )
        if (category != row.category) viewModel.changeCategory(row, category, currentPersonId)
        if (groupId != row.assignment.groupId) viewModel.changeGroup(row, groupId, currentPersonId)
        showToast("\"${row.person.fullName}\" saved.")
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
                    value = middleInitial,
                    onValueChange = { middleInitial = it.uppercase() },
                    label = { Text("Middle Initial (optional)") },
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
                    value = extensionName,
                    onValueChange = { extensionName = it.uppercase() },
                    label = { Text("Extension Name (optional, e.g. Jr., III)") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    Gender.entries.forEach { g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = gender == g, onClick = { gender = g })
                            Text(g.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
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
                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it.uppercase() },
                    label = { Text("Contact Person (optional)") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contactPersonNumber,
                    onValueChange = { contactPersonNumber = it.uppercase() },
                    label = { Text("Contact Person Number (optional)") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )

                EditSectionHeader("Assignment")
                CategoryDropdown(selected = category, onSelected = { category = it })
                GroupDropdown(groups = groups, selectedGroupId = groupId, onSelected = { groupId = it })

                EditSectionHeader("System Information")
                ReadOnlyField("Username", row.person.username)
                AccountStatusDropdown(selected = accountStatus, onSelected = { accountStatus = it })
                ReadOnlyField("Date Added", formatRecordTimestamp(row.person.createdAt))
    }
}

/** "In enrolling publisher record for superadmin, show a dropdown to select
 * a congregation as filter" — Super-Admin-only (see the `visibleCongregationId
 * == null` gate at the call site); narrows the list to one congregation,
 * "All Congregations" (the default) showing every one at once same as before. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CongregationFilterDropdown(
    congregations: List<Congregation>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: "All Congregations"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
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
            DropdownMenuItem(text = { Text("All Congregations") }, onClick = { onSelected(null); expanded = false })
            congregations.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelected(c.id); expanded = false })
            }
        }
    }
}

/** Every Group in this publisher's own congregation, plus "Unassigned"
 * (`null`) — the same clear-back-out option a fresh enrollment leaves them
 * in before an admin ever places them into a Group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDropdown(
    groups: List<Group>,
    selectedGroupId: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "Unassigned"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Unassigned") }, onClick = { onSelected(null); expanded = false })
            groups.forEach { g ->
                DropdownMenuItem(text = { Text(g.name) }, onClick = { onSelected(g.id); expanded = false })
            }
        }
    }
}

/** "Allow the user to Edit the Publishers Status" — [Person.accountStatus]
 * (ACTIVE/INACTIVE/SUSPENDED), the account's sign-in eligibility. Same
 * three-way choice [ManageUsersScreen] already exposes for restricted
 * users, offered here as a plain dropdown to match this dialog's other
 * Assignment-section fields rather than that screen's icon-menu shape. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountStatusDropdown(selected: AccountStatus, onSelected: (AccountStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AccountStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.name) },
                    onClick = {
                        onSelected(status)
                        expanded = false
                    },
                )
            }
        }
    }
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
