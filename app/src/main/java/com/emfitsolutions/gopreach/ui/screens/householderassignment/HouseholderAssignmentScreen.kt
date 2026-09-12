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
            congregationProvince = congregations.firstOrNull { it.id == effectiveCongregationId }?.province,
            viewModel = viewModel,
            onDismiss = { showAddNew = false },
            onCreate = { name, address, barangay, cityMunicipality, province, lat, lng, notes ->
                scope.launch {
                    viewModel.createEligibleRecord(
                        name = name,
                        address = address,
                        barangay = barangay,
                        cityMunicipality = cityMunicipality,
                        province = province,
                        notes = notes.ifBlank { null },
                        recordType = stage,
                        congregationId = effectiveCongregationId,
                        createdByPersonId = currentPersonId,
                        gpsLat = lat,
                        gpsLng = lng,
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

/** "lat, lng" (or "lat lng"/"lat;lng") — whatever a copy-paste from Google
 * Maps or a plain manual entry looks like — parsed loosely rather than
 * requiring one exact separator. `null` when it doesn't parse as two real
 * numbers at all. */
private fun parseCoordinates(text: String): Pair<Double, Double>? {
    val parts = text.trim().split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

/**
 * "Manual Coordinates" — the Service Overseer enters a house holder's
 * latitude/longitude directly (spec: "using manual latitude and longitude
 * coordinates"), one plain line, rather than every field being typed by
 * hand: Municipality/Barangay are then reverse-geocoded from that single
 * coordinate pair (spec: "automatically retrieve and populate... from
 * internet-based location data" — the on-device Geocoder, the same
 * internet-backed mechanism every other GPS-capture flow in this app
 * already uses via [com.emfitsolutions.gopreach.data.location.LocationTracker
 * .reverseGeocodeAddress]), and Province is never asked for here at all —
 * it's always [congregationProvince], the assigned Congregation's own
 * (spec: "Province is automatically inherited from the assigned
 * Congregation's province"). Every resolved field is shown back to the
 * Service Overseer before Add, same "best-effort suggestion, never a
 * silent authoritative fill" rule this app's other reverse-geocode call
 * sites already follow — a failed/partial lookup still lets the record be
 * added with whatever did resolve (or none of it), never blocks on it.
 */
@Composable
private fun AddEligibleRecordDialog(
    recordType: PipelineStage,
    congregationProvince: String?,
    viewModel: HouseholderAssignmentViewModel,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        address: String,
        barangay: String?,
        cityMunicipality: String?,
        province: String?,
        lat: Double?,
        lng: Double?,
        notes: String,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var coordinates by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var resolvedBarangay by remember { mutableStateOf<String?>(null) }
    var resolvedMunicipality by remember { mutableStateOf<String?>(null) }
    var lookupState by remember { mutableStateOf<LookupState>(LookupState.Idle) }
    val scope = rememberCoroutineScopeCompat()
    val parsedCoordinates = remember(coordinates) { parseCoordinates(coordinates) }

    fun lookUp() {
        val (lat, lng) = parsedCoordinates ?: return
        lookupState = LookupState.Loading
        scope.launch {
            val resolved = viewModel.reverseGeocode(lat, lng)
            resolvedBarangay = resolved?.barangay
            resolvedMunicipality = resolved?.cityMunicipality
            lookupState = if (resolved?.barangay != null || resolved?.cityMunicipality != null) {
                LookupState.Resolved
            } else {
                LookupState.NotFound
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${recordType.assignmentLabel()} Record") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("House Holder Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = coordinates,
                    onValueChange = { coordinates = it; lookupState = LookupState.Idle; resolvedBarangay = null; resolvedMunicipality = null },
                    label = { Text("Manual Coordinates (Lat, Lng)") },
                    placeholder = { Text("e.g. 16.4813, 121.1358") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = ::lookUp, enabled = parsedCoordinates != null) {
                            Icon(Icons.Rounded.Search, contentDescription = "Look up Municipality/Barangay")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                when (lookupState) {
                    LookupState.Idle -> Unit
                    LookupState.Loading -> Text("Looking up location…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LookupState.Resolved -> Text(
                        "Resolved: ${resolvedBarangay ?: "—"}, ${resolvedMunicipality ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LookupState.NotFound -> Text(
                        "Could not resolve a Municipality/Barangay for these coordinates — you can still add the record.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Province is never a field here at all — always the
                // assigned Congregation's own, shown read-only just so the
                // Service Overseer can see what will actually be saved.
                Text(
                    "Province: ${congregationProvince ?: "—"} (from Congregation)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        name.trim(),
                        address.trim(),
                        resolvedBarangay,
                        resolvedMunicipality,
                        congregationProvince,
                        parsedCoordinates?.first,
                        parsedCoordinates?.second,
                        notes.trim(),
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class LookupState { Idle, Loading, Resolved, NotFound }

/** "Used when making House Holder Assignments and recommending Publishers
 * for assignments" — a one-line summary of a Publisher's own self-reported
 * [Person.preachingAvailableDays]/[Person.preachingAvailabilityRemarks],
 * for the "Assign To" picker below. `null` fields mean the Publisher never
 * filled in Preaching Availability at all — shown as "No availability set,"
 * never silently blank, so a Service Overseer isn't left guessing whether
 * that means "available every day" or "never asked." */
// Not private — reused by PublisherSchedulesScreen (Account Settings' own
// "View Other Publishers' Schedules" link) so a Publisher can see this same
// summary for fellow publishers in their congregation, not just a Service
// Overseer/Admin picking who to assign a record to.
internal fun availabilitySummary(publisher: Person): String {
    val days = publisher.preachingAvailableDays
        .mapNotNull { runCatching { com.emfitsolutions.gopreach.data.model.PreachingDay.valueOf(it) }.getOrNull() }
        .sortedBy { it.ordinal }
    val daysText = if (days.isEmpty()) null else days.joinToString(", ") { it.shortLabel }
    return when {
        daysText == null && publisher.preachingAvailabilityRemarks.isNullOrBlank() -> "No availability set"
        daysText == null -> publisher.preachingAvailabilityRemarks!!
        publisher.preachingAvailabilityRemarks.isNullOrBlank() -> "Available: $daysText"
        else -> "Available: $daysText — ${publisher.preachingAvailabilityRemarks}"
    }
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
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(p.fullName)
                                        Text(availabilitySummary(p), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { selectedPublisher = p; expanded = false },
                            )
                        }
                    }
                }
                // "Recommending Publishers for assignment" — the full
                // summary again, right under the picker, so it stays
                // visible once the dropdown itself has closed.
                selectedPublisher?.let { p ->
                    Text("Availability: ${availabilitySummary(p)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
