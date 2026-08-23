package com.emfitsolutions.gopreach.ui.screens.interestedpeople

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.model.Gender
import com.emfitsolutions.gopreach.data.model.HouseholderStatus
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.SupportingImage
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.ui.components.DateTimeField
import com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.SupportingImageSection
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Spec §6.3 — Interested People Records, each with multiple preaching visits.
 * [canPermanentlyDelete] is Super-Admin-only, per the "Admin Record Deletion"
 * spec's scoping decision (see BUILD_PLAN.md) — a Publisher can always Move to
 * Inactive their own contacts, but never permanently erase one. */
@Composable
fun InterestedPeopleScreen(
    publisherPersonId: String,
    currentPersonId: String,
    canPermanentlyDelete: Boolean,
    onBack: () -> Unit,
    viewModel: InterestedPeopleViewModel = hiltViewModel(),
) {
    var selectedPerson by remember { mutableStateOf<InterestedPerson?>(null) }
    val current = selectedPerson
    if (current == null) {
        InterestedPeopleListScreen(
            publisherPersonId = publisherPersonId,
            currentPersonId = currentPersonId,
            canPermanentlyDelete = canPermanentlyDelete,
            onBack = onBack,
            onOpenPerson = { selectedPerson = it },
            viewModel = viewModel,
        )
    } else {
        InterestedPersonDetailScreen(
            person = current,
            currentPersonId = currentPersonId,
            onBack = { selectedPerson = null },
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterestedPeopleListScreen(
    publisherPersonId: String,
    currentPersonId: String,
    canPermanentlyDelete: Boolean,
    onBack: () -> Unit,
    onOpenPerson: (InterestedPerson) -> Unit,
    viewModel: InterestedPeopleViewModel,
) {
    val peopleFlow = remember(publisherPersonId) { viewModel.peopleFor(publisherPersonId) }
    val allPeople by peopleFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showInactive by remember { mutableStateOf(false) }
    val people = allPeople.filter { showInactive || it.status == RecordStatus.ACTIVE }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InterestedPerson?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interested People") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New Interested Person")
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
        if (people.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No interested people yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(people, key = { it.id }) { person ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPerson(person) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(person.name, style = MaterialTheme.typography.titleMedium)
                                Text(person.address, style = MaterialTheme.typography.bodySmall)
                                if (person.status == RecordStatus.INACTIVE) {
                                    Text("Inactive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (person.status == RecordStatus.ACTIVE) {
                                IconButton(onClick = { pendingDelete = person }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                }
                            } else {
                                IconButton(onClick = { viewModel.setStatus(person, RecordStatus.ACTIVE, currentPersonId) }) {
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
        InterestedPersonDialog(
            existingPerson = null,
            publisherPersonId = publisherPersonId,
            onSave = { viewModel.save(it) },
            onDismiss = { showCreateDialog = false },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        DeleteChoiceDialog(
            recordLabel = toDelete.name,
            canPermanentlyDelete = canPermanentlyDelete,
            onDismiss = { pendingDelete = null },
            onMoveToInactive = { viewModel.setStatus(toDelete, RecordStatus.INACTIVE, currentPersonId) },
            onDeletePermanently = { viewModel.permanentlyDelete(toDelete, currentPersonId) },
        )
    }
}

/** Handles both "New Interested Person" (spec §7: image is optional at
 * creation) and "Edit Interested Person" (spec §1-§2: the existing supporting
 * image, if any, shows here with Change/Clear) — one form, per this
 * codebase's established Create/Edit-dialog pattern (see e.g. ManageGroupsScreen). */
@Composable
private fun InterestedPersonDialog(
    existingPerson: InterestedPerson?,
    publisherPersonId: String,
    onSave: (InterestedPerson) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existingPerson?.name.orEmpty()) }
    var address by remember { mutableStateOf(existingPerson?.address.orEmpty()) }
    var religion by remember { mutableStateOf(existingPerson?.religion.orEmpty()) }
    var gender by remember { mutableStateOf(existingPerson?.gender) }
    var image by remember { mutableStateOf(existingPerson?.primarySupportingImage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingPerson == null) "New Interested Person" else "Edit Interested Person") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditSectionHeader("Personal Information")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.uppercase() },
                    label = { Text("Name") },
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
                    value = religion,
                    onValueChange = { religion = it.uppercase() },
                    label = { Text("Religion (optional)") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )

                EditSectionHeader("Supporting Information")
                SupportingImageSection(
                    currentImage = image,
                    onImageConfirmed = { image = it },
                    onClear = { image = null },
                )

                if (existingPerson != null) {
                    EditSectionHeader("System Information")
                    ReadOnlyField("Record ID", existingPerson.id)
                    ReadOnlyField("Status", existingPerson.status.name)
                    ReadOnlyField("Date Added", formatRecordTimestamp(existingPerson.createdAt))
                    if (existingPerson.gpsLat != null && existingPerson.gpsLng != null) {
                        ReadOnlyField("Location", "${existingPerson.gpsLat}, ${existingPerson.gpsLng}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && address.isNotBlank() && gender != null) {
                        onSave(
                            (existingPerson ?: InterestedPerson(
                                publisherPersonId = publisherPersonId,
                                createdAt = System.currentTimeMillis(),
                            )).copy(
                                name = name.trim(),
                                gender = gender,
                                address = address.trim(),
                                religion = religion.trim().ifBlank { null },
                                supportingImages = listOfNotNull(image),
                            ),
                        )
                        onDismiss()
                    }
                },
            ) { Text(if (existingPerson == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterestedPersonDetailScreen(
    person: InterestedPerson,
    currentPersonId: String,
    onBack: () -> Unit,
    viewModel: InterestedPeopleViewModel,
) {
    LaunchedEffect(person.id) { viewModel.startVisitSync(person.id) }
    val visitsFlow = remember(person.id) { viewModel.visitsFor(person.id) }
    val visits by visitsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Live, not the static snapshot passed in — a GPS capture/edit/clear
    // (or any other in-place edit) needs to show up here immediately, not
    // only after backing out to the list and reopening this person.
    val livePersonFlow = remember(person.id) {
        viewModel.peopleFor(person.publisherPersonId).map { list -> list.firstOrNull { it.id == person.id } ?: person }
    }
    val livePerson by livePersonFlow.collectAsStateWithLifecycle(initialValue = person)
    var showAddVisit by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(livePerson.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddVisit = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Log Visit")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Address: ${livePerson.address}", style = MaterialTheme.typography.bodyMedium)
                if (livePerson.religion != null) {
                    Text("Religion: ${livePerson.religion}", style = MaterialTheme.typography.bodyMedium)
                }
                SupportingImagePreview(livePerson.primarySupportingImage)
                GpsLocationSection(person = livePerson, currentPersonId = currentPersonId, viewModel = viewModel)
                Text("Visits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
            if (visits.isEmpty()) {
                item { Text("No visits logged yet.", style = MaterialTheme.typography.bodySmall) }
            }
            items(visits, key = { it.id }) { visit ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(dateFormat.format(Date(visit.visitDate)), style = MaterialTheme.typography.titleSmall)
                            Text(visit.householderStatus.name.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                            if (visit.topicDiscussed != null) {
                                Text("Topic: ${visit.topicDiscussed}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Time: ${visit.timeConsumedMinutes / 60}h ${visit.timeConsumedMinutes % 60}m",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteVisit(person.id, visit.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete visit")
                        }
                    }
                }
            }
        }
    }

    if (showAddVisit) {
        AddVisitDialog(
            interestedPersonId = person.id,
            onSave = { viewModel.saveVisit(it) },
            onDismiss = { showAddVisit = false },
        )
    }

    if (showEditDialog) {
        InterestedPersonDialog(
            existingPerson = livePerson,
            publisherPersonId = person.publisherPersonId,
            onSave = { viewModel.save(it) },
            onDismiss = { showEditDialog = false },
        )
    }
}

/** Read-only thumbnail on the detail screen — the editable Capture/Change/
 * Clear controls live inside [InterestedPersonDialog]'s edit mode instead, so
 * this just shows what's currently saved (or nothing, if there isn't one). */
@Composable
private fun SupportingImagePreview(image: SupportingImage?) {
    if (image == null || image.base64Jpeg.isBlank()) return
    val bitmap = remember(image.base64Jpeg) {
        runCatching {
            val bytes = android.util.Base64.decode(image.base64Jpeg, android.util.Base64.NO_WRAP)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Supporting image",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * "Interested Person GPS Capture" spec §4-§8 — Capture/Edit/Clear, all in one
 * place on the profile screen, persisting straight through
 * [InterestedPeopleViewModel.saveGpsLocation]/[clearGpsLocation] (offline-
 * first — spec §10 — same as every other field on this record). Capture and
 * Edit share one flow: both end with a fresh coordinate the user must
 * explicitly confirm (spec §5/§6) before it replaces anything, shown as its
 * own "Confirm Location" state rather than saving the instant a fix comes
 * back.
 *
 * Access control follows the same rule as the rest of this screen (spec §9):
 * this composable has no permission logic of its own — it's only ever reached
 * by a signed-in session already authorized to open this Interested Person's
 * profile at all (enforced upstream, same as viewing/editing anything else
 * about them), and every write goes through the same authenticated
 * repository/Firestore-rules path as any other field.
 */
@Composable
private fun GpsLocationSection(
    person: InterestedPerson,
    currentPersonId: String,
    viewModel: InterestedPeopleViewModel,
) {
    val coroutineScope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf<LatLng?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun runCapture() {
        captureError = null
        isCapturing = true
        coroutineScope.launch {
            val result = viewModel.captureCurrentLocation()
            isCapturing = false
            if (result == null) {
                captureError = "Could not get a GPS fix. Make sure location is turned on and try again."
            } else {
                pendingCapture = result
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCapture() else captureError = "Location permission is required to capture GPS."
    }

    fun startCapture() {
        if (viewModel.hasLocationPermission()) runCapture() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Interested Person Location", style = MaterialTheme.typography.titleMedium)

        val capture = pendingCapture
        when {
            // Spec §5/§6 — freshly captured, not yet saved: shown for an
            // explicit confirm/retake before it touches the stored record.
            capture != null -> Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("New Location Captured", style = MaterialTheme.typography.titleSmall)
                    Text("Latitude: ${"%.6f".format(capture.lat)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Longitude: ${"%.6f".format(capture.lng)}", style = MaterialTheme.typography.bodyMedium)
                    if (capture.accuracyMeters != null) {
                        Text("Accuracy: ${capture.accuracyMeters.toInt()} meters", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.saveGpsLocation(person, capture.lat, capture.lng, capture.accuracyMeters, currentPersonId)
                            pendingCapture = null
                        }) { Text("CONFIRM & SAVE") }
                        OutlinedButton(onClick = { pendingCapture = null }) { Text("CANCEL") }
                    }
                }
            }
            isCapturing -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Text("Getting current location…")
            }
            person.hasGpsLocation -> Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "GPS Location Captured",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Text("Latitude: ${"%.6f".format(person.gpsLat)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Longitude: ${"%.6f".format(person.gpsLng)}", style = MaterialTheme.typography.bodyMedium)
                    if (person.gpsAccuracy != null) {
                        Text("Accuracy: ${person.gpsAccuracy.toInt()} meters", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (person.gpsCapturedAt != null) {
                        Text("Captured: ${formatRecordTimestamp(person.gpsCapturedAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { startCapture() }) { Text("EDIT GPS") }
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("CLEAR GPS") }
                    }
                }
            }
            else -> Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "No GPS location captured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { startCapture() }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("CAPTURE GPS")
                }
            }
        }

        if (captureError != null) {
            Text(captureError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear GPS Location?") },
            text = { Text("This will remove the saved GPS coordinates from this Interested Person.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearGpsLocation(person, currentPersonId)
                    showClearConfirm = false
                }) { Text("CLEAR") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("CANCEL") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVisitDialog(
    interestedPersonId: String,
    onSave: (Visit) -> Unit,
    onDismiss: () -> Unit,
) {
    var visitDate by remember { mutableStateOf<Long?>(null) }
    var topic by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(HouseholderStatus.NOT_AT_HOME) }
    var minutesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Visit") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateTimeField(label = "Visit Date/Time", valueMillis = visitDate, onValueChange = { visitDate = it })
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it.uppercase() },
                    label = { Text("Topic Discussed (optional)") },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                HouseholderStatusDropdown(selected = status, onSelected = { status = it })
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                    label = { Text("Time Consumed (minutes)") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = visitDate
                    val minutes = minutesText.toIntOrNull()
                    if (date != null && minutes != null) {
                        onSave(
                            Visit(
                                interestedPersonId = interestedPersonId,
                                visitDate = date,
                                visitTime = date,
                                topicDiscussed = topic.trim().ifBlank { null },
                                householderStatus = status,
                                timeConsumedMinutes = minutes,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                        onDismiss()
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseholderStatusDropdown(selected: HouseholderStatus, onSelected: (HouseholderStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text("Status of Householder") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HouseholderStatus.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name.replace('_', ' ')) },
                    onClick = {
                        onSelected(s)
                        expanded = false
                    },
                )
            }
        }
    }
}
