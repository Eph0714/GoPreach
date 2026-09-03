package com.emfitsolutions.gopreach.ui.screens.pipeline

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SwapHoriz
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
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.ForwardRequest
import com.emfitsolutions.gopreach.data.model.ForwardRequestStatus
import com.emfitsolutions.gopreach.data.model.Gender
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.PublisherForwardRequest
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.SupportingImage
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.model.VisitOutcome
import com.emfitsolutions.gopreach.ui.components.CoordinatesValue
import com.emfitsolutions.gopreach.ui.components.ClickableCoordinatesText
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.DateTimeField
import com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog
import com.emfitsolutions.gopreach.ui.components.EditSectionHeader
import com.emfitsolutions.gopreach.ui.components.ManualCoordinatesDialog
import com.emfitsolutions.gopreach.ui.components.ReadOnlyField
import com.emfitsolutions.gopreach.ui.components.SupportingImageSection
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun PipelineStage.label(): String = when (this) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

/** "Visited by" for a Return Visit, "Studied by" for a Bible Study (spec's
 * own wording for each module's Visit History). */
private fun PipelineStage.visitorLabel(): String = if (this == PipelineStage.BIBLE_STUDY) "Studied by" else "Visited by"

/**
 * "Redesign the Publisher Dashboard" spec — one screen for all three
 * pipeline stages (Searching / Return Visit / Bible Study), since a record at
 * any of them is the same [InterestedPerson] entity (see [PipelineStage]).
 * Only [stage] and the derived [PipelineStage.label]/[PipelineStage.visitorLabel]
 * differ what's shown: full create/edit fields + [MOVE TO RETURN VISIT] only
 * at Searching; [MOVE TO BIBLE STUDY] only at Return Visit; visit logging at
 * Return Visit/Bible Study. [FORWARD TO OTHER CONGREGATION] and [FORWARD TO
 * OTHER PUBLISHER] (same-congregation hand-off, accept/decline by the
 * target publisher, no Service Overseer step) are both available at every
 * stage, Searching included — the same transfer logic Return Visit/Bible
 * Study already had.
 */
@Composable
fun PipelineScreen(
    publisherPersonId: String,
    currentPersonId: String,
    congregationId: String,
    stage: PipelineStage,
    canPermanentlyDelete: Boolean,
    onBack: () -> Unit,
    viewModel: PipelineViewModel = hiltViewModel(),
) {
    var selectedPerson by remember { mutableStateOf<InterestedPerson?>(null) }
    val current = selectedPerson
    if (current == null) {
        PipelineListScreen(
            publisherPersonId = publisherPersonId,
            currentPersonId = currentPersonId,
            congregationId = congregationId,
            stage = stage,
            canPermanentlyDelete = canPermanentlyDelete,
            onBack = onBack,
            onOpenPerson = { selectedPerson = it },
            viewModel = viewModel,
        )
    } else {
        val congregationName by remember(current.congregationId) { viewModel.congregationName(current.congregationId) }.collectAsStateWithLifecycle(initialValue = null)
        PipelinePersonDetailScreen(
            person = current,
            currentPersonId = currentPersonId,
            congregationName = congregationName ?: "—",
            stage = stage,
            onBack = { selectedPerson = null },
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PipelineListScreen(
    publisherPersonId: String,
    currentPersonId: String,
    congregationId: String,
    stage: PipelineStage,
    canPermanentlyDelete: Boolean,
    onBack: () -> Unit,
    onOpenPerson: (InterestedPerson) -> Unit,
    viewModel: PipelineViewModel,
) {
    val peopleFlow = remember(publisherPersonId, stage) { viewModel.peopleFor(publisherPersonId, stage) }
    val allPeople by peopleFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showInactive by remember { mutableStateOf(false) }
    val people = allPeople.filter { showInactive || it.status == RecordStatus.ACTIVE }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InterestedPerson?>(null) }
    val showToast = rememberActionToast()

    // Bug fix: surfaces a save failure (e.g. from a corrupt/oversized photo)
    // as a toast instead of the app silently closing — see
    // PipelineViewModel.errorEvents' doc comment.
    LaunchedEffect(Unit) { viewModel.errorEvents.collect { showToast(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stage.label()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            // "The Publisher can directly add from Return Visit and Bible
            // Study Module... use the same entity as the Searching Fields.
            // If it save under Return Visit Module then the status will be
            // automatically 'Return Visit'" — a new record can now be
            // created directly at whichever stage screen it's added from,
            // not just Searching; [PipelinePersonDialog] sets the new
            // record's initial [InterestedPerson.pipelineStage] to this
            // [stage] rather than always defaulting to Searching. A record
            // still gains further stages the normal way too ([MOVE TO
            // RETURN VISIT]/[MOVE TO BIBLE STUDY] on an existing record) —
            // this is an additional entry point, not a replacement for that
            // one.
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New ${stage.label()} Record")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showInactive, onCheckedChange = { showInactive = it })
                Text("Show Inactive")
            }
            if (people.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No records yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(people, key = { it.id }) { person ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { onOpenPerson(person) }) {
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
                                    if (person.pendingForwardRequestId != null) {
                                        ForwardStatusBadge(person = person, viewModel = viewModel)
                                    }
                                    if (person.pendingPublisherForwardRequestId != null) {
                                        PublisherForwardStatusBadge(person = person, viewModel = viewModel)
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
        PipelinePersonDialog(
            existingPerson = null,
            publisherPersonId = publisherPersonId,
            congregationId = congregationId,
            currentPersonId = currentPersonId,
            stage = stage,
            onSave = { viewModel.save(it); showToast("Record added.") },
            onDismiss = { showCreateDialog = false },
            viewModel = viewModel,
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

@Composable
private fun ForwardStatusBadge(person: InterestedPerson, viewModel: PipelineViewModel) {
    val requestFlow = remember(person.id) { viewModel.forwardRequestFor(person) }
    val request by requestFlow.collectAsStateWithLifecycle(initialValue = null)
    val r = request ?: return
    val (text, color) = when (r.status) {
        ForwardRequestStatus.PENDING -> "Forward status: Pending (${r.toCongregationNameSnapshot})" to MaterialTheme.colorScheme.tertiary
        ForwardRequestStatus.ACCEPTED -> "Forward status: Accepted (${r.toCongregationNameSnapshot})" to MaterialTheme.colorScheme.primary
        ForwardRequestStatus.DECLINED -> "Forward status: Declined" to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun PublisherForwardStatusBadge(person: InterestedPerson, viewModel: PipelineViewModel) {
    val requestFlow = remember(person.id) { viewModel.publisherForwardRequestFor(person) }
    val request by requestFlow.collectAsStateWithLifecycle(initialValue = null)
    val r = request ?: return
    val (text, color) = when (r.status) {
        ForwardRequestStatus.PENDING -> "Forward status: Pending (${r.toPublisherNameSnapshot})" to MaterialTheme.colorScheme.tertiary
        ForwardRequestStatus.ACCEPTED -> "Forward status: Accepted (${r.toPublisherNameSnapshot})" to MaterialTheme.colorScheme.primary
        ForwardRequestStatus.DECLINED -> "Forward status: Declined" to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** The same create/edit form (spec's full Searching field list) for every
 * stage — spec: "the Publisher can directly add from Return Visit and Bible
 * Study Module... use the same entity as the Searching Fields." A brand-new
 * record ([existingPerson] null) is created with [PipelineStage] set to
 * whichever [stage] this dialog was opened from — "if it save under Return
 * Visit Module then the status will be automatically 'Return Visit'" — so
 * adding from the Bible Study screen skips straight to Bible Study without
 * a separate [MOVE TO...] step, same for Return Visit. Editing an existing
 * record ([existingPerson] non-null) never changes its stage here — [stage]
 * is unused in that path — a record's stage still only ever advances via
 * [MOVE TO RETURN VISIT]/[MOVE TO BIBLE STUDY] on [PipelinePersonDetailScreen]. */
@Composable
private fun PipelinePersonDialog(
    existingPerson: InterestedPerson?,
    publisherPersonId: String,
    congregationId: String,
    currentPersonId: String,
    stage: PipelineStage,
    onSave: (InterestedPerson) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PipelineViewModel,
) {
    var name by remember { mutableStateOf(existingPerson?.name.orEmpty()) }
    var spouse by remember { mutableStateOf(existingPerson?.spouse.orEmpty()) }
    var address by remember { mutableStateOf(existingPerson?.address.orEmpty()) }
    var children by remember { mutableStateOf(existingPerson?.children.orEmpty()) }
    var religion by remember { mutableStateOf(existingPerson?.religion.orEmpty()) }
    var ageText by remember { mutableStateOf(existingPerson?.ageYears?.toString().orEmpty()) }
    var placeOrigin by remember { mutableStateOf(existingPerson?.placeOrigin.orEmpty()) }
    var language by remember { mutableStateOf(existingPerson?.language.orEmpty()) }
    var literaturePlace by remember { mutableStateOf(existingPerson?.literaturePlace.orEmpty()) }
    var remarks by remember { mutableStateOf(existingPerson?.remarks.orEmpty()) }
    var notes by remember { mutableStateOf(existingPerson?.notes.orEmpty()) }
    var gender by remember { mutableStateOf(existingPerson?.gender) }
    var image by remember { mutableStateOf(existingPerson?.primarySupportingImage) }
    val originalCoordinates = remember(existingPerson) {
        existingPerson?.takeIf { it.hasGpsLocation }?.let { CoordinatesValue(it.gpsLat!!, it.gpsLng!!, it.gpsAccuracy) }
    }
    var coordinates by remember { mutableStateOf(originalCoordinates) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingPerson == null) "New ${stage.label()} Record" else "Edit Record") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditSectionHeader("Personal Information")
                OutlinedTextField(value = name, onValueChange = { name = it.uppercase() }, label = { Text("Name") }, singleLine = true, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                Row {
                    Gender.entries.forEach { g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = gender == g, onClick = { gender = g })
                            Text(g.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                OutlinedTextField(value = spouse, onValueChange = { spouse = it.uppercase() }, label = { Text("Spouse (optional)") }, singleLine = true, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it.uppercase() }, label = { Text("Address") }, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = children, onValueChange = { children = it.uppercase() }, label = { Text("Children (optional)") }, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = religion, onValueChange = { religion = it.uppercase() }, label = { Text("Religion (optional)") }, singleLine = true, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ageText, onValueChange = { ageText = it.filter { c -> c.isDigit() } }, label = { Text("Age (optional)") }, singleLine = true, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = placeOrigin, onValueChange = { placeOrigin = it.uppercase() }, label = { Text("Place Origin (optional)") }, singleLine = true, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = language, onValueChange = { language = it.uppercase() }, label = { Text("Language (optional)") }, singleLine = true, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = literaturePlace, onValueChange = { literaturePlace = it.uppercase() }, label = { Text("Literature Place (optional)") }, singleLine = true, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks (optional)") }, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())

                EditSectionHeader("Coordinates (optional)")
                CoordinatesEditorField(coordinates = coordinates, onChange = { coordinates = it }, viewModel = viewModel)

                EditSectionHeader("Supporting Information")
                SupportingImageSection(currentImage = image, onImageConfirmed = { image = it }, onClear = { image = null })

                if (existingPerson != null) {
                    EditSectionHeader("System Information")
                    ReadOnlyField("Record ID", existingPerson.id)
                    ReadOnlyField("Status", existingPerson.status.name)
                    ReadOnlyField("Stage", existingPerson.pipelineStage.label())
                    ReadOnlyField("Date Created", formatRecordTimestamp(existingPerson.createdAt))
                    ReadOnlyField("Created By", existingPerson.createdByPersonId.ifBlank { "—" })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && address.isNotBlank() && gender != null) {
                        val coordinatesChanged = coordinates != originalCoordinates
                        val now = System.currentTimeMillis()
                        val base = existingPerson ?: InterestedPerson(
                            publisherPersonId = publisherPersonId,
                            congregationId = congregationId,
                            createdAt = now,
                            createdByPersonId = currentPersonId,
                            // "If it save under Return Visit Module then the
                            // status will be automatically in 'Return
                            // Visit'" — a brand-new record starts at
                            // whichever stage this dialog was opened from,
                            // not always Searching.
                            pipelineStage = stage,
                            stageEnteredAt = now,
                        )
                        onSave(
                            base.copy(
                                name = name.trim(),
                                gender = gender,
                                spouse = spouse.trim().ifBlank { null },
                                address = address.trim(),
                                children = children.trim().ifBlank { null },
                                religion = religion.trim().ifBlank { null },
                                ageYears = ageText.toIntOrNull(),
                                placeOrigin = placeOrigin.trim().ifBlank { null },
                                language = language.trim().ifBlank { null },
                                literaturePlace = literaturePlace.trim().ifBlank { null },
                                remarks = remarks.trim().ifBlank { null },
                                notes = notes.trim().ifBlank { null },
                                supportingImages = listOfNotNull(image),
                                gpsLat = coordinates?.lat,
                                gpsLng = coordinates?.lng,
                                gpsAccuracy = coordinates?.accuracyMeters ?: existingPerson?.gpsAccuracy.takeIf { !coordinatesChanged },
                                gpsCapturedAt = if (coordinatesChanged) coordinates?.let { now } else existingPerson?.gpsCapturedAt,
                                gpsCapturedBy = if (coordinatesChanged) coordinates?.let { currentPersonId } else existingPerson?.gpsCapturedBy,
                                gpsUpdatedAt = if (coordinatesChanged) coordinates?.let { now } else existingPerson?.gpsUpdatedAt,
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

@Composable
private fun CoordinatesEditorField(coordinates: CoordinatesValue?, onChange: (CoordinatesValue?) -> Unit, viewModel: PipelineViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf<LatLng?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }

    fun runCapture() {
        captureError = null
        isCapturing = true
        coroutineScope.launch {
            val result = viewModel.captureCurrentLocation()
            isCapturing = false
            if (result == null) captureError = "Could not get a GPS fix. Make sure location is turned on and try again." else pendingCapture = result
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCapture() else captureError = "Location permission is required to capture GPS."
    }
    fun startCapture() {
        if (viewModel.hasLocationPermission()) runCapture() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val capture = pendingCapture
    when {
        capture != null -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("New Location Captured", style = MaterialTheme.typography.labelLarge)
                Text("Latitude: ${"%.6f".format(capture.lat)}", style = MaterialTheme.typography.bodyMedium)
                Text("Longitude: ${"%.6f".format(capture.lng)}", style = MaterialTheme.typography.bodyMedium)
                if (capture.accuracyMeters != null) Text("Accuracy: ${capture.accuracyMeters.toInt()} meters", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { onChange(CoordinatesValue(capture.lat, capture.lng, capture.accuracyMeters)); pendingCapture = null }) { Text("Confirm") }
                    TextButton(onClick = { pendingCapture = null }) { Text("Cancel") }
                }
            }
        }
        isCapturing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
            Text("Getting current location…")
        }
        coordinates != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Latitude: ${"%.6f".format(coordinates.lat)}", style = MaterialTheme.typography.bodyMedium)
            Text("Longitude: ${"%.6f".format(coordinates.lng)}", style = MaterialTheme.typography.bodyMedium)
            if (coordinates.accuracyMeters != null) Text("Accuracy: ${coordinates.accuracyMeters.toInt()} meters", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { startCapture() }) { Text("Edit Location") }
                OutlinedButton(onClick = { onChange(null) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Clear") }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { startCapture() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Capture Current Location")
            }
            OutlinedButton(onClick = { showManualEntry = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Enter Coordinates Manually")
            }
        }
    }
    if (captureError != null) Text(captureError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (showManualEntry) {
        ManualCoordinatesDialog(initial = coordinates, onConfirm = { onChange(it); showManualEntry = false }, onDismiss = { showManualEntry = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PipelinePersonDetailScreen(
    person: InterestedPerson,
    currentPersonId: String,
    congregationName: String,
    stage: PipelineStage,
    onBack: () -> Unit,
    viewModel: PipelineViewModel,
) {
    // Bug fix: startVisitSync() returns a cold Flow (a callbackFlow wrapping
    // the actual Firestore listener registration) — calling it without
    // collecting is a no-op, the listener never actually registers. Must be
    // .collect()ed to do anything.
    LaunchedEffect(person.id) { viewModel.startVisitSync(person.id).collect {} }
    val visitsFlow = remember(person.id) { viewModel.visitsFor(person.id) }
    val visits by visitsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val livePersonFlow = remember(person.id) {
        viewModel.peopleFor(person.publisherPersonId, stage).map { list -> list.firstOrNull { it.id == person.id } ?: person }
    }
    val livePerson by livePersonFlow.collectAsStateWithLifecycle(initialValue = person)
    var showAddVisit by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var showForwardToPublisherDialog by remember { mutableStateOf(false) }
    var selectedVisit by remember { mutableStateOf<Visit?>(null) }
    var pendingEditVisit by remember { mutableStateOf<Visit?>(null) }
    var pendingDeleteVisit by remember { mutableStateOf<Visit?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val sortedVisits = remember(visits) { visits.sortedByDescending { it.visitDate } }
    val createdByName by remember(livePerson.createdByPersonId) { viewModel.personName(livePerson.createdByPersonId) }.collectAsStateWithLifecycle(initialValue = null)
    val forwardRequestFlow = remember(livePerson.pendingForwardRequestId) { viewModel.forwardRequestFor(livePerson) }
    val forwardRequest by forwardRequestFlow.collectAsStateWithLifecycle(initialValue = null)
    val publisherForwardRequestFlow = remember(livePerson.pendingPublisherForwardRequestId) { viewModel.publisherForwardRequestFor(livePerson) }
    val publisherForwardRequest by publisherForwardRequestFlow.collectAsStateWithLifecycle(initialValue = null)
    val showToast = rememberActionToast()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(livePerson.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit") } },
            )
        },
        floatingActionButton = {
            // "Add Visit" only applies once there's an actual visit history to
            // keep — Searching has none yet (spec's own module description).
            if (stage != PipelineStage.SEARCHING) {
                FloatingActionButton(onClick = { showAddVisit = true }) { Icon(Icons.Rounded.Add, contentDescription = "Log Visit") }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                EditSectionHeader("Personal Information")
                Text("Name: ${livePerson.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Gender: ${livePerson.gender?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Spouse: ${livePerson.spouse ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Address: ${livePerson.address}", style = MaterialTheme.typography.bodyMedium)
                Text("Children: ${livePerson.children ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Religion: ${livePerson.religion ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Age: ${livePerson.ageYears ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Place Origin: ${livePerson.placeOrigin ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Language: ${livePerson.language ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Literature Place: ${livePerson.literaturePlace ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Congregation: $congregationName", style = MaterialTheme.typography.bodyMedium)
                Text("Remarks: ${livePerson.remarks ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                SupportingImagePreview(livePerson.primarySupportingImage)
            }
            item {
                EditSectionHeader("Location", modifier = Modifier.padding(top = 16.dp))
                GpsLocationSection(person = livePerson, currentPersonId = currentPersonId, viewModel = viewModel)
            }
            item {
                EditSectionHeader("Notes", modifier = Modifier.padding(top = 16.dp))
                Text(livePerson.notes ?: "No notes recorded.", style = MaterialTheme.typography.bodyMedium)
            }
            // Stage-advance / Forward actions — Searching module's spec:
            // "Every record saved were having a button next to their names
            // [MOVE TO RETURN VISIT MODULE, FORWARD TO OTHER CONGREGATION]".
            // "FORWARD TO OTHER CONGREGATION" (same [ForwardRequest] flow) is
            // offered at every stage; "FORWARD TO OTHER PUBLISHER" only at
            // Return Visit/Bible Study, per spec. Stacked full-width buttons
            // (not side-by-side) — two long labels in one Row used to overflow
            // past the screen edge on a normal phone width, which read as
            // "not presentable."
            item {
                EditSectionHeader("Actions", modifier = Modifier.padding(top = 16.dp))
                PipelineActionButtons(
                    stage = stage,
                    forwardRequest = forwardRequest,
                    publisherForwardRequest = publisherForwardRequest,
                    onAdvanceStage = { newStage ->
                        viewModel.advanceStage(livePerson, newStage, currentPersonId)
                        showToast("Moved to ${newStage.label()}.")
                    },
                    onShowForwardDialog = { showForwardDialog = true },
                    onShowForwardToPublisherDialog = { showForwardToPublisherDialog = true },
                )
            }
            if (stage != PipelineStage.SEARCHING) {
                item { EditSectionHeader("System Information", modifier = Modifier.padding(top = 16.dp))
                    Text("Date Created: ${formatRecordTimestamp(livePerson.createdAt)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Created By: ${createdByName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                }
                item { EditSectionHeader("Visit History", modifier = Modifier.padding(top = 16.dp)) }
                if (sortedVisits.isEmpty()) {
                    item { Text("No visits logged yet.", style = MaterialTheme.typography.bodySmall) }
                }
                itemsIndexed(sortedVisits, key = { _, visit -> visit.id }) { index, visit ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedVisit = visit }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(dateFormat.format(Date(visit.visitDate)), style = MaterialTheme.typography.titleSmall)
                                    if (index == 0) Text("  •  Latest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(visit.outcome.name.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                                if (visit.topicDiscussed != null) Text("Remarks/Topic: ${visit.topicDiscussed}", style = MaterialTheme.typography.bodySmall)
                                val visitorName by remember(visit.publisherPersonId) { viewModel.personName(visit.publisherPersonId) }.collectAsStateWithLifecycle(initialValue = null)
                                Text("${stage.visitorLabel()}: ${visitorName ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row {
                                IconButton(onClick = { pendingEditVisit = visit }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit visit") }
                                IconButton(onClick = { pendingDeleteVisit = visit }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete visit") }
                            }
                        }
                    }
                }
            } else {
                item { EditSectionHeader("System Information", modifier = Modifier.padding(top = 16.dp))
                    Text("Date Created: ${formatRecordTimestamp(livePerson.createdAt)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Created By: ${createdByName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    selectedVisit?.let { visit -> VisitDetailDialog(visit = visit, stage = stage, dateFormat = dateFormat, onDismiss = { selectedVisit = null }, viewModel = viewModel) }

    if (showAddVisit) {
        AddVisitDialog(
            existingVisit = null,
            interestedPersonId = person.id,
            publisherPersonId = person.publisherPersonId,
            currentPersonId = currentPersonId,
            stage = stage,
            onSave = { viewModel.saveVisit(it); showToast("Visit logged.") },
            onDismiss = { showAddVisit = false },
        )
    }

    val toEditVisit = pendingEditVisit
    if (toEditVisit != null) {
        AddVisitDialog(
            existingVisit = toEditVisit,
            interestedPersonId = person.id,
            publisherPersonId = person.publisherPersonId,
            currentPersonId = currentPersonId,
            stage = stage,
            onSave = { viewModel.saveVisit(it); showToast("Visit updated.") },
            onDismiss = { pendingEditVisit = null },
        )
    }

    // "Delete (Permanently)" — deleteVisit has always been a real, hard
    // delete (Visit carries no RecordStatus/inactive concept to soft-delete
    // into); this was previously wired straight to the trash icon with no
    // confirmation at all, so a stray tap wiped a Visit History entry with
    // zero chance to back out. A confirmation now guards it, matching every
    // other permanent-delete flow in this app.
    val toDeleteVisit = pendingDeleteVisit
    if (toDeleteVisit != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteVisit = null },
            title = { Text("Delete Visit?") },
            text = { Text("This will permanently delete the visit logged on ${dateFormat.format(Date(toDeleteVisit.visitDate))}. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteVisit(person.id, toDeleteVisit.id)
                    showToast("Visit deleted.")
                    pendingDeleteVisit = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteVisit = null }) { Text("Cancel") } },
        )
    }

    if (showEditDialog) {
        PipelinePersonDialog(
            existingPerson = livePerson,
            publisherPersonId = person.publisherPersonId,
            congregationId = livePerson.congregationId,
            currentPersonId = currentPersonId,
            // Irrelevant for an edit (existingPerson != null short-circuits
            // the new-record construction below) — passed only because
            // this stage-aware dialog now always needs one.
            stage = stage,
            onSave = { viewModel.save(it); showToast("Record saved.") },
            onDismiss = { showEditDialog = false },
            viewModel = viewModel,
        )
    }

    if (showForwardDialog) {
        ForwardToCongregationDialog(
            person = livePerson,
            ownCongregationName = congregationName,
            currentPersonId = currentPersonId,
            onDismiss = { showForwardDialog = false },
            viewModel = viewModel,
        )
    }

    if (showForwardToPublisherDialog) {
        ForwardToPublisherDialog(
            person = livePerson,
            currentPersonId = currentPersonId,
            onDismiss = { showForwardToPublisherDialog = false },
            viewModel = viewModel,
        )
    }
}

/** The Actions section's buttons — stacked full-width, each with a leading
 * icon and a mixed-case label, rather than the previous side-by-side
 * ALL-CAPS buttons ("FORWARD TO OTHER CONGREGATION" next to "FORWARD TO
 * OTHER PUBLISHER" in one Row) that overflowed past the screen edge on a
 * normal phone width. */
@Composable
private fun PipelineActionButtons(
    stage: PipelineStage,
    forwardRequest: ForwardRequest?,
    publisherForwardRequest: PublisherForwardRequest?,
    onAdvanceStage: (PipelineStage) -> Unit,
    onShowForwardDialog: () -> Unit,
    onShowForwardToPublisherDialog: () -> Unit,
) {
    val iconModifier = Modifier.size(18.dp).padding(end = 8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val nextStage = when (stage) {
            PipelineStage.SEARCHING -> PipelineStage.RETURN_VISIT
            PipelineStage.RETURN_VISIT -> PipelineStage.BIBLE_STUDY
            PipelineStage.BIBLE_STUDY -> null
        }
        if (nextStage != null) {
            Button(onClick = { onAdvanceStage(nextStage) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = iconModifier)
                Text("Move to ${nextStage.label()}")
            }
        }
        OutlinedButton(
            onClick = onShowForwardDialog,
            enabled = forwardRequest?.status != ForwardRequestStatus.PENDING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = iconModifier)
            Text("Forward to Other Congregation")
        }
        // "Use the same logic in transferring to other publisher in Return
        // Visit and Bible Study" — Searching now offers the exact same
        // "Forward to Other Publisher" flow (receiving publisher accepts/
        // declines, no Service Overseer step) Return Visit/Bible Study
        // already had; forwardToPublisher()/the receiving side's accept
        // flow were already entirely stage-agnostic (never reads or
        // assumes pipelineStage), so this was purely a UI gate, not a data-
        // layer change.
        OutlinedButton(
            onClick = onShowForwardToPublisherDialog,
            enabled = publisherForwardRequest?.status != ForwardRequestStatus.PENDING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Forward, contentDescription = null, modifier = iconModifier)
            Text("Forward to Other Publisher")
        }
        ForwardStatusLine(forwardRequest)
        PublisherForwardStatusLine(publisherForwardRequest)
    }
}

@Composable
private fun ForwardStatusLine(r: ForwardRequest?) {
    if (r == null) return
    Text(
        when (r.status) {
            ForwardRequestStatus.PENDING -> "Forward to congregation: Pending — sent to ${r.toCongregationNameSnapshot}"
            ForwardRequestStatus.ACCEPTED -> "Forward to congregation: Accepted by ${r.toCongregationNameSnapshot} — assigned to ${r.assignedToPublisherNameSnapshot ?: "—"}"
            ForwardRequestStatus.DECLINED -> "Forward to congregation: Declined by ${r.toCongregationNameSnapshot}"
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PublisherForwardStatusLine(r: PublisherForwardRequest?) {
    if (r == null) return
    Text(
        when (r.status) {
            ForwardRequestStatus.PENDING -> "Forward to publisher: Pending — sent to ${r.toPublisherNameSnapshot}"
            ForwardRequestStatus.ACCEPTED -> "Forward to publisher: Accepted by ${r.toPublisherNameSnapshot}"
            ForwardRequestStatus.DECLINED -> "Forward to publisher: Declined by ${r.toPublisherNameSnapshot}"
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

/** "FORWARD TO OTHER CONGREGATION" spec flow — search field over
 * name/language, filtered client-side (the congregation list is already
 * app-wide mirrored, and never large enough to warrant a server-side query). */
@Composable
private fun ForwardToCongregationDialog(
    person: InterestedPerson,
    ownCongregationName: String,
    currentPersonId: String,
    onDismiss: () -> Unit,
    viewModel: PipelineViewModel,
) {
    val congregationsFlow = remember(person.congregationId) { viewModel.otherCongregations(person.congregationId) }
    val congregations by congregationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val fromPublisherName by remember(person.publisherPersonId) { viewModel.personName(person.publisherPersonId) }.collectAsStateWithLifecycle(initialValue = null)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Congregation?>(null) }
    val filtered = remember(congregations, query) {
        if (query.isBlank()) congregations
        else congregations.filter { c -> c.name.contains(query, ignoreCase = true) || c.languages.any { it.contains(query, ignoreCase = true) } }
    }
    val showToast = rememberActionToast()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward to Other Congregation") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; selected = null },
                    label = { Text("Search by congregation name or language") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selected = c; query = c.name },
                            colors = if (selected?.id == c.id) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium)
                                if (c.languages.isNotEmpty()) Text(c.languages.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (filtered.isEmpty()) item { Text("No matching congregation.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = selected
                    if (target != null) {
                        viewModel.forward(person, target, ownCongregationName, fromPublisherName ?: "—", currentPersonId)
                        showToast("Forward request sent to ${target.name}.")
                        onDismiss()
                    }
                },
                enabled = selected != null,
            ) { Text("Send Request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "FORWARD TO OTHER PUBLISHER" spec flow — search field over publisher
 * name, scoped to the record's own congregation (this flow never crosses
 * congregations); the receiving publisher themselves accepts/declines it
 * (see [PublisherForwardRequestsScreen]), so there's no "assign to" step
 * here — picking who to send it to *is* the whole dialog. */
@Composable
private fun ForwardToPublisherDialog(
    person: InterestedPerson,
    currentPersonId: String,
    onDismiss: () -> Unit,
    viewModel: PipelineViewModel,
) {
    val publishersFlow = remember(person.congregationId, person.publisherPersonId) {
        viewModel.otherPublishers(person.congregationId, person.publisherPersonId)
    }
    val publishers by publishersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val fromPublisherName by remember(person.publisherPersonId) { viewModel.personName(person.publisherPersonId) }.collectAsStateWithLifecycle(initialValue = null)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Person?>(null) }
    val filtered = remember(publishers, query) {
        if (query.isBlank()) publishers else publishers.filter { it.fullName.contains(query, ignoreCase = true) }
    }
    val showToast = rememberActionToast()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward to Other Publisher") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; selected = null },
                    label = { Text("Search by publisher name") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { p ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selected = p; query = p.fullName },
                            colors = if (selected?.id == p.id) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
                        ) {
                            Text(p.fullName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                        }
                    }
                    if (filtered.isEmpty()) item { Text("No other publisher available in your congregation.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = selected
                    if (target != null) {
                        viewModel.forwardToPublisher(person, target, fromPublisherName ?: "—", currentPersonId)
                        showToast("Forward request sent to ${target.fullName}.")
                        onDismiss()
                    }
                },
                enabled = selected != null,
            ) { Text("Send Request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

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
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(160.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun GpsLocationSection(person: InterestedPerson, currentPersonId: String, viewModel: PipelineViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf<LatLng?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    val showToast = rememberActionToast()

    fun runCapture() {
        captureError = null
        isCapturing = true
        coroutineScope.launch {
            val result = viewModel.captureCurrentLocation()
            isCapturing = false
            if (result == null) captureError = "Could not get a GPS fix. Make sure location is turned on and try again." else pendingCapture = result
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
            capture != null -> Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("New Location Captured", style = MaterialTheme.typography.titleSmall)
                    ClickableCoordinatesText(lat = capture.lat, lng = capture.lng, label = person.name.ifBlank { null })
                    if (capture.accuracyMeters != null) Text("Accuracy: ${capture.accuracyMeters.toInt()} meters", style = MaterialTheme.typography.bodyMedium)
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveGpsLocation(person, capture.lat, capture.lng, capture.accuracyMeters, currentPersonId)
                                showToast("Location saved.")
                                pendingCapture = null
                            },
                        ) { Text("Confirm & Save") }
                        OutlinedButton(onClick = { pendingCapture = null }) { Text("Cancel") }
                    }
                }
            }
            isCapturing -> Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Text("Getting current location…")
            }
            person.hasGpsLocation -> Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("GPS Location Captured", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
                    }
                    ClickableCoordinatesText(lat = person.gpsLat!!, lng = person.gpsLng!!, label = person.name.ifBlank { null })
                    if (person.gpsAccuracy != null) Text("Accuracy: ${person.gpsAccuracy.toInt()} meters", style = MaterialTheme.typography.bodyMedium)
                    if (person.gpsCapturedAt != null) Text("Captured: ${formatRecordTimestamp(person.gpsCapturedAt)}", style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { startCapture() }) { Text("Edit Location") }
                        OutlinedButton(onClick = { showClearConfirm = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Clear Location") }
                    }
                }
            }
            else -> Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No location captured", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { startCapture() }) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Capture Current Location")
                }
                OutlinedButton(onClick = { showManualEntry = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Enter Coordinates Manually")
                }
            }
        }
        if (captureError != null) Text(captureError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
    }

    if (showManualEntry) {
        ManualCoordinatesDialog(
            initial = person.takeIf { it.hasGpsLocation }?.let { CoordinatesValue(it.gpsLat!!, it.gpsLng!!, it.gpsAccuracy) },
            onConfirm = { value ->
                viewModel.saveGpsLocation(person, value.lat, value.lng, value.accuracyMeters, currentPersonId)
                showToast("Location saved.")
                showManualEntry = false
            },
            onDismiss = { showManualEntry = false },
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear GPS Location?") },
            text = { Text("This will remove the saved GPS coordinates from this record.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearGpsLocation(person, currentPersonId)
                        showToast("Location cleared.")
                        showClearConfirm = false
                    },
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVisitDialog(
    /** Non-null makes this an edit of an existing Visit History entry
     * (spec: "allow the publisher to Add, edit and delete... the Visit
     * History") — pre-fills every field from it and preserves its id/
     * createdAt/createdByPersonId on save, only ever changing the fields
     * this form actually edits. */
    existingVisit: Visit?,
    interestedPersonId: String,
    publisherPersonId: String,
    currentPersonId: String,
    stage: PipelineStage,
    onSave: (Visit) -> Unit,
    onDismiss: () -> Unit,
) {
    var visitDate by remember { mutableStateOf(existingVisit?.visitDate) }
    var topic by remember { mutableStateOf(existingVisit?.topicDiscussed.orEmpty()) }
    var outcome by remember { mutableStateOf(existingVisit?.outcome ?: VisitOutcome.NOT_AT_HOME) }
    var minutesText by remember { mutableStateOf(existingVisit?.timeConsumedMinutes?.toString().orEmpty()) }
    var followUpDate by remember { mutableStateOf(existingVisit?.followUpDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingVisit == null) "Log Visit" else "Edit Visit") },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateTimeField(label = "Visit Date/Time", valueMillis = visitDate, onValueChange = { visitDate = it })
                OutlinedTextField(value = topic, onValueChange = { topic = it.uppercase() }, label = { Text("Remarks / Topic Discussed (optional)") }, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                VisitOutcomeDropdown(selected = outcome, onSelected = { outcome = it })
                OutlinedTextField(value = minutesText, onValueChange = { minutesText = it.filter { c -> c.isDigit() } }, label = { Text("Time Consumed (minutes)") }, singleLine = true, visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                DateTimeField(label = "Follow-up Date (optional)", valueMillis = followUpDate, onValueChange = { followUpDate = it })
                if (followUpDate != null) TextButton(onClick = { followUpDate = null }) { Text("Clear Follow-up Date") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = visitDate
                    val minutes = minutesText.toIntOrNull()
                    if (date != null && minutes != null) {
                        val base = existingVisit ?: Visit(
                            interestedPersonId = interestedPersonId,
                            publisherPersonId = publisherPersonId,
                            createdAt = System.currentTimeMillis(),
                            createdByPersonId = currentPersonId,
                        )
                        onSave(
                            base.copy(
                                visitDate = date,
                                visitTime = date,
                                topicDiscussed = topic.trim().ifBlank { null },
                                outcome = outcome,
                                timeConsumedMinutes = minutes,
                                followUpDate = followUpDate,
                            )
                        )
                        onDismiss()
                    }
                },
            ) { Text(if (existingVisit == null) "Save" else "Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun VisitDetailDialog(visit: Visit, stage: PipelineStage, dateFormat: SimpleDateFormat, onDismiss: () -> Unit, viewModel: PipelineViewModel) {
    val visitorName by remember(visit.publisherPersonId) { viewModel.personName(visit.publisherPersonId) }.collectAsStateWithLifecycle(initialValue = null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Visit Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadOnlyField("Visit Date", dateFormat.format(Date(visit.visitDate)))
                ReadOnlyField("Status", visit.outcome.name.replace('_', ' '))
                ReadOnlyField("Time Consumed", "${visit.timeConsumedMinutes / 60}h ${visit.timeConsumedMinutes % 60}m")
                ReadOnlyField("Remarks / Topic Discussed", visit.topicDiscussed ?: "—")
                ReadOnlyField(stage.visitorLabel(), visitorName ?: "—")
                ReadOnlyField("Follow-up Date", visit.followUpDate?.let { dateFormat.format(Date(it)) } ?: "—")
                ReadOnlyField("Logged", formatRecordTimestamp(visit.createdAt))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitOutcomeDropdown(selected: VisitOutcome, onSelected: (VisitOutcome) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VisitOutcome.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.name.replace('_', ' ')) }, onClick = { onSelected(s); expanded = false })
            }
        }
    }
}
