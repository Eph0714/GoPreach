package com.emfitsolutions.gopreach.ui.screens.householderassignment

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.HouseholderAssignment
import com.emfitsolutions.gopreach.data.model.HouseholderAssignmentStatus
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import kotlinx.coroutines.launch

/** "House Holder Assignment" module — spec's own exact record-type labels
 * (never the pipeline's internal "Searching"). */
internal fun PipelineStage.assignmentLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Interested Person"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

internal fun HouseholderAssignmentStatus.label(): String = when (this) {
    HouseholderAssignmentStatus.PENDING -> "Pending"
    HouseholderAssignmentStatus.ACCEPTED -> "Accepted"
    HouseholderAssignmentStatus.REJECTED -> "Rejected"
    HouseholderAssignmentStatus.CANCELLED -> "Cancelled"
    HouseholderAssignmentStatus.COMPLETED -> "Completed"
}

/** Barangay/City-Municipality/Province, comma-joined, skipping blanks —
 * same helper every forward-request screen already carries its own copy of. */
private fun addressLine(person: InterestedPerson): String? {
    val parts = listOfNotNull(person.barangay, person.cityMunicipality, person.province).filter { it.isNotBlank() }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/**
 * "Add a New Module: House Holder Assignment" — the Service Overseer/Admin/
 * Super-Admin's own side: House Holder Assignment -> Select Record Type ->
 * Search Record -> View Record Details -> Select Publisher -> Review
 * Assignment -> Send Assignment. Every eligible record here is still the
 * exact same [InterestedPerson] entity/fields the Searching/Return Visit/
 * Bible Study modules already use (spec's own "Do not create duplicate
 * versions of these entities") — this screen only ever *searches*, *creates
 * a new eligible one*, and *sends an assignment*; it never edits a record
 * directly (spec's own "Core Principle").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholderAssignmentScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    currentPersonName: String,
    onBack: () -> Unit,
    viewModel: HouseholderAssignmentViewModel = hiltViewModel(),
) {
    val isSuperAdmin = fixedCongregationId == null
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    var selectedCongregationId by remember { mutableStateOf<String?>(null) }
    val effectiveCongregationId = if (isSuperAdmin) selectedCongregationId else fixedCongregationId
    val congregationName = congregations.firstOrNull { it.id == effectiveCongregationId }?.name.orEmpty()
    val showToast = rememberActionToast()
    val scope = rememberCoroutineScopeCompat()

    var stage by remember { mutableStateOf(PipelineStage.SEARCHING) }
    var query by remember { mutableStateOf("") }
    val recordsFlow = remember(stage, effectiveCongregationId, query) { viewModel.eligibleRecordsFor(stage, effectiveCongregationId, query) }
    val records by recordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val sentFlow = remember(effectiveCongregationId, isSuperAdmin) {
        viewModel.sentAssignmentsFor(if (isSuperAdmin) effectiveCongregationId?.let { setOf(it) } else setOfNotNull(fixedCongregationId))
    }
    val sentAssignments by sentFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var showAddNew by remember { mutableStateOf(false) }
    var assigningPerson by remember { mutableStateOf<InterestedPerson?>(null) }
    var viewingSent by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("House Holder Assignment") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        TextButton(onClick = { viewingSent = !viewingSent }) {
                            Text(if (viewingSent) "Search" else "Sent (${sentAssignments.size})")
                        }
                    },
                )
                if (!viewingSent) {
                    ScrollableTabRow(selectedTabIndex = PipelineStage.entries.indexOf(stage)) {
                        PipelineStage.entries.forEach { s ->
                            Tab(selected = stage == s, onClick = { stage = s }, text = { Text(s.assignmentLabel()) })
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!viewingSent && effectiveCongregationId != null) {
                FloatingActionButton(onClick = { showAddNew = true }) { Icon(Icons.Rounded.Add, contentDescription = "Add New Record") }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isSuperAdmin) {
                CongregationDropdown(
                    congregations = congregations,
                    selectedId = selectedCongregationId,
                    onSelected = { selectedCongregationId = it },
                )
            }
            if (effectiveCongregationId == null) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select a congregation to begin.", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (viewingSent) {
                SentAssignmentsList(
                    assignments = sentAssignments,
                    onCancel = { assignment ->
                        viewModel.cancel(assignment, currentPersonId)
                        showToast("Assignment cancelled.")
                    },
                )
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search by name or address") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (records.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No eligible ${stage.assignmentLabel()} records found. Use + to add a newly searched one.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(records, key = { it.id }) { person ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { assigningPerson = person }) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    addressLine(person)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    person.notes?.let { if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddNew && effectiveCongregationId != null) {
        AddEligibleRecordDialog(
            recordType = stage,
            onDismiss = { showAddNew = false },
            onCreate = { name, address, barangay, notes ->
                scope.launch {
                    viewModel.createEligibleRecord(
                        name = name,
                        address = address,
                        barangay = barangay.ifBlank { null },
                        cityMunicipality = null,
                        province = null,
                        notes = notes.ifBlank { null },
                        recordType = stage,
                        congregationId = effectiveCongregationId,
                        createdByPersonId = currentPersonId,
                    )
                    showToast("Record added — search for it above to assign.")
                }
                showAddNew = false
            },
        )
    }

    val person = assigningPerson
    if (person != null) {
        AssignPublisherDialog(
            person = person,
            congregationName = congregationName,
            viewModel = viewModel,
            onDismiss = { assigningPerson = null },
            onSend = { toPublisher ->
                viewModel.sendAssignment(
                    person = person,
                    congregationNameSnapshot = congregationName,
                    toPublisher = toPublisher,
                    assignedByPersonId = currentPersonId,
                    assignedByNameSnapshot = currentPersonName,
                )
                showToast("Assignment sent to ${toPublisher.fullName}.")
                assigningPerson = null
            },
        )
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CongregationDropdown(congregations: List<Congregation>, selectedId: String?, onSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: "Select Congregation"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Congregation") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            congregations.sortedBy { it.name }.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelected(c.id); expanded = false })
            }
        }
    }
}

@Composable
private fun AddEligibleRecordDialog(
    recordType: PipelineStage,
    onDismiss: () -> Unit,
    onCreate: (name: String, address: String, barangay: String, notes: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var barangay by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${recordType.assignmentLabel()} Record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("House Holder Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = barangay, onValueChange = { barangay = it }, label = { Text("Barangay") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), address.trim(), barangay.trim(), notes.trim()) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignPublisherDialog(
    person: InterestedPerson,
    congregationName: String,
    viewModel: HouseholderAssignmentViewModel,
    onDismiss: () -> Unit,
    onSend: (Person) -> Unit,
) {
    val publishersFlow = remember(person.congregationId) { viewModel.assignablePublishers(person.congregationId) }
    val publishers by publishersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPublisher by remember { mutableStateOf<Person?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
        onDismissRequest = onDismiss,
        title = { Text("Assign: ${person.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Record Type: ${person.pipelineStage.assignmentLabel()}", style = MaterialTheme.typography.bodyMedium)
                addressLine(person)?.let { Text("Location: $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Congregation: $congregationName", style = MaterialTheme.typography.bodyMedium)
                person.notes?.let { if (it.isNotBlank()) Text("Notes: $it", style = MaterialTheme.typography.bodyMedium) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedPublisher?.fullName ?: "Select Publisher",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assign To") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (publishers.isEmpty()) {
                            DropdownMenuItem(text = { Text("No eligible publishers") }, onClick = {}, enabled = false)
                        }
                        publishers.forEach { p ->
                            DropdownMenuItem(text = { Text(p.fullName) }, onClick = { selectedPublisher = p; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selectedPublisher?.let(onSend) }, enabled = selectedPublisher != null) { Text("Send Assignment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SentAssignmentsList(assignments: List<HouseholderAssignment>, onCancel: (HouseholderAssignment) -> Unit) {
    if (assignments.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No assignments sent yet.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(assignments, key = { it.id }) { assignment ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(assignment.personNameSnapshot, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(assignment.status.label(), style = MaterialTheme.typography.labelMedium)
                    }
                    Text("Type: ${assignment.recordType.assignmentLabel()}", style = MaterialTheme.typography.bodySmall)
                    Text("To: ${assignment.toPublisherNameSnapshot}", style = MaterialTheme.typography.bodySmall)
                    Text("Sent: ${formatRecordTimestamp(assignment.assignedAt)}", style = MaterialTheme.typography.bodySmall)
                    assignment.respondedAt?.let {
                        Text("Responded: ${formatRecordTimestamp(it)} by ${assignment.respondedByNameSnapshot ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    }
                    assignment.rejectionReason?.let { if (it.isNotBlank()) Text("Reason: $it", style = MaterialTheme.typography.bodySmall) }
                    if (assignment.status == HouseholderAssignmentStatus.PENDING) {
                        TextButton(onClick = { onCancel(assignment) }) { Text("Cancel Assignment") }
                    }
                }
            }
        }
    }
}
