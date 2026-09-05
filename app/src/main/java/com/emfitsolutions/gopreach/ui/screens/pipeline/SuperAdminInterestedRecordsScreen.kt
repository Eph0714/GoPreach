package com.emfitsolutions.gopreach.ui.screens.pipeline

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
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage

private fun PipelineStage.tabLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

/**
 * "The super admin can see all congregation Search[ing]/Bible Study/Return
 * Visit record[s]... Add, Edit, [and permanently] Delete the record" — a
 * Super-Admin-only, cross-congregation counterpart to [PipelineScreen], which
 * is always scoped to one publisher's own records. One screen for all three
 * pipeline stages (tabs switch [PipelineViewModel.allPeopleFor]'s filter),
 * each row showing its own congregation + owning publisher so a Super-Admin
 * can tell them apart across congregations. Reuses [PipelinePersonDialog]
 * (Add/Edit) and [PipelinePersonDetailScreen] (open a record — visit
 * history, GPS, forward, permanent delete) unchanged; the only thing new
 * here is the unscoped data source and the congregation/publisher picker a
 * brand-new record needs, since a Super-Admin has no "own congregation" to
 * default to the way every other caller of [PipelinePersonDialog] does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminInterestedRecordsScreen(
    currentPersonId: String,
    onBack: () -> Unit,
    viewModel: PipelineViewModel = hiltViewModel(),
) {
    var stage by remember { mutableStateOf(PipelineStage.SEARCHING) }
    var selectedPerson by remember { mutableStateOf<InterestedPerson?>(null) }

    val current = selectedPerson
    if (current != null) {
        val congregationName by remember(current.congregationId) { viewModel.congregationName(current.congregationId) }.collectAsStateWithLifecycle(initialValue = null)
        PipelinePersonDetailScreen(
            person = current,
            currentPersonId = currentPersonId,
            congregationName = congregationName ?: "—",
            stage = stage,
            onBack = { selectedPerson = null },
            viewModel = viewModel,
        )
        return
    }

    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    val peopleFlow = remember(stage) { viewModel.allPeopleFor(stage) }
    val allPeople by peopleFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showInactive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InterestedPerson?>(null) }
    val showToast = rememberActionToast()

    val congregationNameById = remember(congregations) { congregations.associateBy({ it.id }, { it.name }) }
    val people = remember(allPeople, showInactive, query, congregationNameById) {
        allPeople
            .filter { showInactive || it.status == RecordStatus.ACTIVE }
            .filter { p ->
                query.isBlank() ||
                    p.name.contains(query, ignoreCase = true) ||
                    p.address.contains(query, ignoreCase = true) ||
                    (congregationNameById[p.congregationId]?.contains(query, ignoreCase = true) == true)
            }
            .sortedBy { it.name }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Interested People — All Congregations") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                )
                ScrollableTabRow(selectedTabIndex = PipelineStage.entries.indexOf(stage)) {
                    PipelineStage.entries.forEach { s ->
                        Tab(selected = stage == s, onClick = { stage = s }, text = { Text(s.tabLabel()) })
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New ${stage.tabLabel()} Record")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name, address, or congregation") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showInactive, onCheckedChange = { showInactive = it })
                Text("Show Inactive")
            }
            if (people.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No ${stage.tabLabel()} records found.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(people, key = { it.id }) { person ->
                        val publisherName by remember(person.publisherPersonId) { viewModel.personName(person.publisherPersonId) }.collectAsStateWithLifecycle(initialValue = null)
                        Card(modifier = Modifier.fillMaxWidth().clickable { selectedPerson = person }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOfNotNull(congregationNameById[person.congregationId] ?: "Unassigned", publisherName?.let { "Publisher: $it" }).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(person.address, style = MaterialTheme.typography.bodySmall)
                                    if (person.status == RecordStatus.INACTIVE) {
                                        Text("Inactive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (person.status == RecordStatus.ACTIVE) {
                                    IconButton(onClick = { pendingDelete = person }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                                } else {
                                    IconButton(onClick = { viewModel.setStatus(person, RecordStatus.ACTIVE, currentPersonId); showToast("\"${person.name}\" reactivated.") }) {
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

    if (showCreateDialog) {
        SuperAdminCreateRecordFlow(
            stage = stage,
            currentPersonId = currentPersonId,
            congregations = congregations,
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false },
            onCreated = { showCreateDialog = false; showToast("Record added.") },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        // Super-Admin may always permanently delete, in any congregation
        // (spec: "He can Add, Edit, Delete permanently the record") — same
        // [DeleteChoiceDialog] every other permanent-delete flow in this app
        // uses, just with [canPermanentlyDelete] unconditionally true here.
        DeleteChoiceDialog(
            recordLabel = toDelete.name,
            canPermanentlyDelete = true,
            onDismiss = { pendingDelete = null },
            onMoveToInactive = { viewModel.setStatus(toDelete, RecordStatus.INACTIVE, currentPersonId) },
            onDeletePermanently = { viewModel.permanentlyDelete(toDelete, currentPersonId) },
        )
    }
}

/** Step 1 of the Super-Admin "Add Record" flow — pick which congregation and
 * which of its publishers the brand-new record belongs to (every other
 * caller of [PipelinePersonDialog] already knows this from the signed-in
 * session's own assignment; a Super-Admin has none, so this is the one thing
 * that flow needs before it can reuse the exact same create form). Once both
 * are picked, hands off to [PipelinePersonDialog] unchanged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuperAdminCreateRecordFlow(
    stage: PipelineStage,
    currentPersonId: String,
    congregations: List<Congregation>,
    viewModel: PipelineViewModel,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    var selectedCongregation by remember { mutableStateOf<Congregation?>(null) }
    var selectedPublisher by remember { mutableStateOf<Person?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPersonForm by remember { mutableStateOf(false) }

    val publishersFlow = remember(selectedCongregation) {
        selectedCongregation?.let { viewModel.publishersFor(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val publishers by publishersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    if (showPersonForm) {
        val congregation = selectedCongregation
        val publisher = selectedPublisher
        if (congregation != null && publisher != null) {
            PipelinePersonDialog(
                existingPerson = null,
                publisherPersonId = publisher.id,
                congregationId = congregation.id,
                currentPersonId = currentPersonId,
                stage = stage,
                // [PipelinePersonDialog]'s own submit() calls onSave(...) then
                // onDismiss() unconditionally right after — [onCreated] must
                // only fire from inside onSave (an actual successful save),
                // never as a side effect of this composable simply being
                // entered, or every recomposition while this step is showing
                // would re-fire it.
                onSave = { viewModel.save(it); onCreated() },
                onDismiss = onDismiss,
                viewModel = viewModel,
            )
        }
        return
    }

    fun submit() {
        val message = requiredFieldsMessage(
            "Congregation" to (selectedCongregation != null),
            "Publisher" to (selectedPublisher != null),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        showPersonForm = true
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = "New ${stage.tabLabel()} Record",
        onConfirm = ::submit,
        confirmLabel = "Next",
        errorMessage = errorMessage,
        maxContentHeight = 360.dp,
    ) {
        Text("Choose which congregation and publisher this record belongs to.", style = MaterialTheme.typography.bodySmall)
        SimplePicker(
            label = "Congregation",
            options = congregations,
            selected = selectedCongregation,
            optionLabel = { it.name },
            onSelected = { selectedCongregation = it; selectedPublisher = null },
        )
        if (selectedCongregation != null) {
            SimplePicker(
                label = "Publisher",
                options = publishers,
                selected = selectedPublisher,
                optionLabel = { it.fullName },
                onSelected = { selectedPublisher = it },
            )
            if (publishers.isEmpty()) {
                Text("No active publishers in this congregation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Small reusable read-only dropdown — same [ExposedDropdownMenuBox] shape
 * [GroupChatDialogs.kt]'s own congregation picker already uses, generalized
 * over any labeled option list rather than duplicated per type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SimplePicker(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}
