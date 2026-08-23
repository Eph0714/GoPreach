package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** A plain lat/lng(/accuracy) value with no capture metadata attached — used
 * wherever coordinates are being edited locally (not yet persisted), unlike
 * [com.emfitsolutions.gopreach.data.model.InterestedPerson]'s own
 * gpsLat/gpsLng/gpsAccuracy/gpsCapturedAt/gpsCapturedBy/gpsUpdatedAt, which
 * only make sense once there's an actual record and actor to attribute a
 * capture to. */
data class CoordinatesValue(val lat: Double, val lng: Double, val accuracyMeters: Float? = null)

/** "Enter Coordinates Manually" spec — a real geographic coordinate, not
 * arbitrary text (spec §12: "do not store coordinates as arbitrary formatted
 * text"). */
fun isValidLatitude(value: Double): Boolean = value in -90.0..90.0
fun isValidLongitude(value: Double): Boolean = value in -180.0..180.0

/**
 * [ENTER COORDINATES MANUALLY] spec — two plain numeric fields with inline
 * range validation; Save is unreachable (the button simply doesn't confirm)
 * until both are valid, rather than accepting bad input and failing later.
 */
@Composable
fun ManualCoordinatesDialog(
    initial: CoordinatesValue?,
    onConfirm: (CoordinatesValue) -> Unit,
    onDismiss: () -> Unit,
) {
    var latText by remember { mutableStateOf(initial?.lat?.toString().orEmpty()) }
    var lngText by remember { mutableStateOf(initial?.lng?.toString().orEmpty()) }

    val lat = latText.toDoubleOrNull()
    val lng = lngText.toDoubleOrNull()
    val latError = latText.isNotBlank() && (lat == null || !isValidLatitude(lat))
    val lngError = lngText.isNotBlank() && (lng == null || !isValidLongitude(lng))
    val canConfirm = lat != null && lng != null && isValidLatitude(lat) && isValidLongitude(lng)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Coordinates Manually") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    placeholder = { Text("e.g. 16.500000") },
                    singleLine = true,
                    isError = latError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (latError) Text("Must be between -90 and 90.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("Longitude") },
                    placeholder = { Text("e.g. 121.500000") },
                    singleLine = true,
                    isError = lngError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (lngError) Text("Must be between -180 and 180.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (canConfirm) onConfirm(CoordinatesValue(lat!!, lng!!, null)) }, enabled = canConfirm) {
                Text("SAVE")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}
