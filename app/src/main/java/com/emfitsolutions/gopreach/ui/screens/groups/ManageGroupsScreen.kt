package com.emfitsolutions.gopreach.ui.screens.groups

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
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.RegularElderRole
import com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.displayLabel
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

/** Spec: "CRUD Groups" — each Group needs exactly one Elder in each of three
 * roles (Overseer/Servant/Assistant), not the single Elder this used to allow.
 * [fixedCongregationId] scopes both the list and the create dialog for Admin/
 * Coordinator Elder; null lets a Super-Admin pick a congregation per group instead.
 * [canPermanentlyDelete] is Super-Admin-only, per the "Admin Record Deletion"
 * spec's scoping decision (see BUILD_PLAN.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageGroupsScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    canPermanentlyDelete: Boolean,
    onBack: () -> Unit,
    viewModel: ManageGroupsViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val allRows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showInactive by remember { mutableStateOf(false) }
    val rows = allRows.filter { showInactive || it.group.status == RecordStatus.ACTIVE }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<Group?>(null) }
    var pendingDelete by remember { mutableStateOf<Group?>(null) }
    var permanentDeleteBlockReason by remember { mutableStateOf<String?>(null) }
    var permanentDeleteChecked by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New Group")
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
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No groups yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.group.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(row.group.name, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = { pendingEdit = row.group }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit group")
                                    }
                                    if (row.group.status == RecordStatus.ACTIVE) {
                                        IconButton(onClick = { pendingDelete = row.group }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete group")
                                        }
                                    } else {
                                        IconButton(onClick = { viewModel.setStatus(row.group, RecordStatus.ACTIVE, currentPersonId) }) {
                                            Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Reactivate")
                                        }
                                    }
                                }
                            }
                            if (row.group.status == RecordStatus.INACTIVE) {
                                Text("Inactive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                "${RegularElderRole.GROUP_OVERSEER.displayLabel()}: ${row.overseerName ?: "Unassigned"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${RegularElderRole.GROUP_SERVANT.displayLabel()}: ${row.servantName ?: "Unassigned"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${RegularElderRole.GROUP_ASSISTANT.displayLabel()}: ${row.assistantName ?: "Unassigned"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (row.group.regularElderPersonId != null && !row.group.isComplete) {
                                Text(
                                    "Existing assignment on file — open Edit to place them in a role.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            if (!row.group.isComplete) {
                                val missing = row.group.missingRoles().joinToString(", ") { it.displayLabel() }
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            "Group Assignment Incomplete",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Text(
                                            "Missing: $missing",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
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

    if (showCreateDialog) {
        GroupDialog(
            fixedCongregationId = fixedCongregationId,
            existingGroup = null,
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false },
        )
    }

    val toEditGroup = pendingEdit
    if (toEditGroup != null) {
        GroupDialog(
            fixedCongregationId = fixedCongregationId,
            existingGroup = toEditGroup,
            viewModel = viewModel,
            onDismiss = { pendingEdit = null },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        LaunchedEffect(toDelete.id) {
            permanentDeleteBlockReason = viewModel.permanentDeleteBlockReason(toDelete.id)
            permanentDeleteChecked = true
        }
        if (permanentDeleteChecked) {
            DeleteChoiceDialog(
                recordLabel = toDelete.name,
                canPermanentlyDelete = canPermanentlyDelete,
                permanentDeleteBlockedReason = permanentDeleteBlockReason,
                onDismiss = { pendingDelete = null; permanentDeleteChecked = false },
                onMoveToInactive = { viewModel.setStatus(toDelete, RecordStatus.INACTIVE, currentPersonId) },
                onDeletePermanently = { viewModel.permanentlyDelete(toDelete.id, currentPersonId) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDialog(
    fixedCongregationId: String?,
    existingGroup: Group?,
    viewModel: ManageGroupsViewModel,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existingGroup?.name ?: "") }
    val congregations by viewModel.congregations.collectAsStateWithLifecycle(initialValue = emptyList())
    var pickedCongregation by remember(congregations) {
        mutableStateOf(congregations.firstOrNull { it.id == existingGroup?.congregationId })
    }
    // Scoped roles (Admin/Coordinator Elder) already have exactly one congregation;
    // only a Super-Admin needs to pick one here.
    val congregationId = fixedCongregationId ?: pickedCongregation?.id ?: existingGroup?.congregationId

    var overseer by remember { mutableStateOf<Person?>(null) }
    var servant by remember { mutableStateOf<Person?>(null) }
    var assistant by remember { mutableStateOf<Person?>(null) }
    var preselected by remember { mutableStateOf(existingGroup == null) }

    val overseerCandidates by remember(congregationId, servant, assistant) {
        if (congregationId != null) {
            viewModel.availableEldersFor(congregationId, RegularElderRole.GROUP_OVERSEER, setOfNotNull(servant?.id, assistant?.id))
        } else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val servantCandidates by remember(congregationId, overseer, assistant) {
        if (congregationId != null) {
            viewModel.availableEldersFor(congregationId, RegularElderRole.GROUP_SERVANT, setOfNotNull(overseer?.id, assistant?.id))
        } else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val assistantCandidates by remember(congregationId, overseer, servant) {
        if (congregationId != null) {
            viewModel.availableEldersFor(congregationId, RegularElderRole.GROUP_ASSISTANT, setOfNotNull(overseer?.id, servant?.id))
        } else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Pre-fill from the existing Group's three role slots once their candidate
    // lists have loaded — a legacy single-Elder Group (regularElderPersonId set,
    // the three role fields still null) has nothing to pre-fill here, which is
    // exactly the "assign the appropriate role" gap the admin fills in manually below.
    if (!preselected && (overseerCandidates.isNotEmpty() || servantCandidates.isNotEmpty() || assistantCandidates.isNotEmpty())) {
        overseer = overseerCandidates.firstOrNull { it.id == existingGroup?.overseerPersonId }
        servant = servantCandidates.firstOrNull { it.id == existingGroup?.servantPersonId }
        assistant = assistantCandidates.firstOrNull { it.id == existingGroup?.assistantPersonId }
        preselected = true
    }

    val legacyElderName = existingGroup?.regularElderPersonId?.let { legacyId ->
        (overseerCandidates + servantCandidates + assistantCandidates).firstOrNull { it.id == legacyId }?.fullName
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingGroup == null) "New Group" else "Edit Group") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (fixedCongregationId == null) {
                    CongregationPickerDropdown(
                        congregations = congregations,
                        selected = pickedCongregation,
                        onSelected = { pickedCongregation = it },
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.uppercase() },
                    label = { Text("Group Name") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (legacyElderName != null && existingGroup?.isComplete == false) {
                    Text(
                        "Existing assignment: $legacyElderName — pick which role they hold below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                ElderRoleDropdown(
                    label = RegularElderRole.GROUP_OVERSEER.displayLabel(),
                    elders = overseerCandidates,
                    selected = overseer,
                    onSelected = { overseer = it },
                )
                ElderRoleDropdown(
                    label = RegularElderRole.GROUP_SERVANT.displayLabel(),
                    elders = servantCandidates,
                    selected = servant,
                    onSelected = { servant = it },
                )
                ElderRoleDropdown(
                    label = RegularElderRole.GROUP_ASSISTANT.displayLabel(),
                    elders = assistantCandidates,
                    selected = assistant,
                    onSelected = { assistant = it },
                )

                if (existingGroup != null) {
                    EditSectionHeader("System Information")
                    ReadOnlyField("Record ID", existingGroup.id)
                    ReadOnlyField("Status", existingGroup.status.name)
                    ReadOnlyField("Date Added", formatRecordTimestamp(existingGroup.createdAt))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && congregationId != null) {
                        viewModel.save(
                            Group(
                                id = existingGroup?.id ?: "",
                                congregationId = congregationId,
                                name = name.trim(),
                                regularElderPersonId = existingGroup?.regularElderPersonId,
                                overseerPersonId = overseer?.id,
                                servantPersonId = servant?.id,
                                assistantPersonId = assistant?.id,
                                createdAt = existingGroup?.createdAt ?: System.currentTimeMillis(),
                            )
                        )
                        onDismiss()
                    }
                },
            ) { Text(if (existingGroup == null) "Create" else "Save") }
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

/** One role's Elder picker — [elders] is already filtered to that role (plus
 * unclassified legacy Elders) and already excludes whoever the *other two*
 * dropdowns currently hold, so cross-role duplicate selection isn't reachable
 * through this UI at all. A "None" option lets a role be cleared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElderRoleDropdown(label: String, elders: List<Person>, selected: Person?, onSelected: (Person?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.fullName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            elders.forEach { elder ->
                DropdownMenuItem(
                    text = { Text(elder.fullName) },
                    onClick = {
                        onSelected(elder)
                        expanded = false
                    },
                )
            }
        }
    }
}
