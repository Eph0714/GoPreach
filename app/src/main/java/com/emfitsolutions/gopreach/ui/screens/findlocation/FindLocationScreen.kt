package com.emfitsolutions.gopreach.ui.screens.findlocation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.location.formatCoordinatesDms
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.SavedLocation
import com.emfitsolutions.gopreach.ui.components.RoundIconActionButton
import com.emfitsolutions.gopreach.ui.components.rememberActionToast

/** A way to get to the destination — mirrors Google Maps' own `travelmode`
 * values, covering both "by walking" and "different kinds of vehicle" (car,
 * bicycle, public transit) per the feature request. */
private enum class TravelMode(val label: String, val icon: ImageVector, val travelModeParam: String) {
    WALKING("Walking", Icons.AutoMirrored.Rounded.DirectionsWalk, "walking"),
    DRIVING("Car", Icons.Rounded.DirectionsCar, "driving"),
    BICYCLING("Bicycle", Icons.AutoMirrored.Rounded.DirectionsBike, "bicycling"),
    TRANSIT("Transit", Icons.Rounded.DirectionsBus, "transit"),
}

/**
 * "Find Location" — a Publisher types in a destination's GPS coordinates by
 * hand, and picks a travel mode (walking, car, bicycle, or public transit) to
 * jump straight into Google Maps' own turn-by-turn navigation for the
 * fastest route there, starting from wherever the device currently is.
 *
 * There's no bundled Google Directions API key in this app, so the actual
 * route calculation/"fastest route" comparison across modes is deliberately
 * handed off to the Google Maps app (or its web fallback) already on the
 * device, the same way [com.emfitsolutions.gopreach.ui.screens.sharelocation
 * .ShareLocationScreen] already opens a bare `geo:` intent rather than
 * drawing its own map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindLocationScreen(
    currentPersonId: String,
    onBack: () -> Unit,
    viewModel: FindLocationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val showToast = rememberActionToast()
    // "Make the Latitude and Longitude in the same text field" — one field,
    // comma-separated ("14.5995, 120.9842"), rather than two.
    var coordinatesText by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedLocation?>(null) }

    val savedLocationsFlow = remember(currentPersonId) { viewModel.savedLocationsFor(currentPersonId) }
    val savedLocations by savedLocationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    fun submit() {
        val parts = coordinatesText.trim().split(",").map { it.trim() }
        val lat = parts.getOrNull(0)?.toDoubleOrNull()
        val lng = parts.getOrNull(1)?.toDoubleOrNull()
        errorText = when {
            parts.size != 2 || lat == null || lng == null -> "Enter latitude and longitude separated by a comma, e.g. 14.5995, 120.9842."
            lat < -90.0 || lat > 90.0 -> "Latitude must be between -90 and 90."
            lng < -180.0 || lng > 180.0 -> "Longitude must be between -180 and 180."
            else -> null
        }
        destination = if (errorText == null) lat!! to lng!! else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Enter the GPS coordinates of where you want to go, then pick how you're getting there to open the fastest route.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = coordinatesText,
                onValueChange = { coordinatesText = it; errorText = null },
                label = { Text("Latitude, Longitude") },
                placeholder = { Text("e.g. 14.5995, 120.9842") },
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorText != null) {
                Text(errorText.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) {
                Text("FIND ROUTE")
            }

            destination?.let { (lat, lng) ->
                val address by produceState<String?>(initialValue = null, lat, lng) {
                    value = viewModel.addressFor(lat, lng)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Destination",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        Text("Coordinates: ${formatCoordinatesDms(lat, lng)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Location: ${address ?: "Resolving address…"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // "The publisher can save the location and can
                        // assign a 'Searching Record', 'Return Visit'
                        // record or 'Bible Study' Record or simply save the
                        // coordinates with remarks" — both actions are
                        // offered side by side; neither one requires the
                        // other.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = { showSaveDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("Save Location")
                            }
                            OutlinedButton(onClick = { showAssignDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Assignment, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("Assign to Record")
                            }
                        }
                    }
                }

                Text("Choose how you're traveling:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TravelMode.entries.forEach { mode ->
                        RoundIconActionButton(
                            label = mode.label,
                            icon = mode.icon,
                            onClick = { openDirections(context, lat, lng, mode.travelModeParam) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "Opens Google Maps for turn-by-turn directions and the fastest route in the mode you picked, starting from your current location.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (savedLocations.isNotEmpty()) {
                HorizontalDivider()
                Text("Saved Locations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                savedLocations.forEach { saved ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(saved.remarks.ifBlank { "Saved Location" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(formatCoordinatesDms(saved.lat, saved.lng), style = MaterialTheme.typography.bodySmall)
                            }
                            Row {
                                TextButton(onClick = {
                                    coordinatesText = "${saved.lat}, ${saved.lng}"
                                    destination = saved.lat to saved.lng
                                    errorText = null
                                }) { Text("Use") }
                                IconButton(onClick = { pendingDelete = saved }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete saved location")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val destinationForSave = destination
    if (showSaveDialog && destinationForSave != null) {
        SaveLocationDialog(
            lat = destinationForSave.first,
            lng = destinationForSave.second,
            onSave = { remarks ->
                viewModel.saveLocation(currentPersonId, destinationForSave.first, destinationForSave.second, remarks)
                showToast("Location saved.")
                showSaveDialog = false
            },
            onCancel = { showSaveDialog = false },
        )
    }

    if (showAssignDialog && destinationForSave != null) {
        AssignToRecordDialog(
            currentPersonId = currentPersonId,
            lat = destinationForSave.first,
            lng = destinationForSave.second,
            onAssigned = { person ->
                viewModel.assignToRecord(person, destinationForSave.first, destinationForSave.second, currentPersonId)
                showToast("Location assigned to \"${person.name}\".")
                showAssignDialog = false
            },
            onDismiss = { showAssignDialog = false },
            viewModel = viewModel,
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Saved Location?") },
            text = { Text("This removes \"${toDelete.remarks.ifBlank { "this location" }}\" from your saved locations.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSavedLocation(toDelete.id)
                    showToast("Saved location deleted.")
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

/**
 * "Result Found: Coordinate: Lat/Lng, Remarks: [ ], [Save] [Cancel]" — the
 * spec's exact worked example, shown right after a destination is found. The
 * remark is required (spec's example is descriptive, not optional — an
 * unlabeled saved coordinate is useless in a list of many).
 */
@Composable
private fun SaveLocationDialog(
    lat: Double,
    lng: Double,
    onSave: (remarks: String) -> Unit,
    onCancel: () -> Unit,
) {
    var remarks by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Result Found") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Coordinate: ${formatCoordinatesDms(lat, lng)}", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    placeholder = { Text("e.g. House of Emilio Aguinaldo") },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(remarks) }, enabled = remarks.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** "The publisher can... assign a 'Searching Record', 'Return Visit' record
 * or 'Bible Study' Record" — pick which of the three stages, then which of
 * the Publisher's own active records at that stage, to write the found
 * coordinate onto (see [FindLocationViewModel.assignToRecord]). */
@Composable
private fun AssignToRecordDialog(
    currentPersonId: String,
    lat: Double,
    lng: Double,
    onAssigned: (InterestedPerson) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FindLocationViewModel,
) {
    var stage by remember { mutableStateOf(PipelineStage.SEARCHING) }
    val recordsFlow = remember(currentPersonId, stage) { viewModel.recordsFor(currentPersonId, stage) }
    val records by recordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to Record") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Coordinate: ${formatCoordinatesDms(lat, lng)}", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PipelineStage.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = stage == entry,
                            onClick = { stage = entry },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = PipelineStage.entries.size),
                        ) { Text(entry.assignLabel()) }
                    }
                }
                if (records.isEmpty()) {
                    Text(
                        "No ${stage.assignLabel()} records yet under your name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    records.forEach { person ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { onAssigned(person) }) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(person.address, style = MaterialTheme.typography.bodySmall)
                                if (person.hasGpsLocation) {
                                    Text(
                                        "Already has a saved location — tapping will replace it.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun PipelineStage.assignLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

/** Launches Google Maps (falling back to any browser if the app isn't
 * installed) already positioned on turn-by-turn directions for [mode] from
 * the device's current location to ([lat], [lng]) — the same
 * `ACTION_VIEW`-a-`Uri` pattern [com.emfitsolutions.gopreach.ui.screens
 * .sharelocation.ShareLocationScreen] already uses for its `geo:` links. */
private fun openDirections(context: android.content.Context, lat: Double, lng: Double, mode: String) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=$mode")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // No app/browser can handle it (e.g. a stripped-down test device) —
        // nothing sensible to fall back to short of a Toast; swallow rather
        // than crash the Main Form.
    }
}
