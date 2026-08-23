package com.emfitsolutions.gopreach.ui.screens.sharelocation

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingLocationAction { ENABLE_SHARING, REFRESH }

/**
 * Spec §6.1 — Share Location. [canShareOwnLocation] gates the "share my
 * location" toggle to publishers only, per spec ("Publisher: can share own
 * location while preaching"); every role can view whoever's currently sharing
 * within their own scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLocationScreen(
    currentPersonId: String,
    visibleCongregationId: String?,
    visibleGroupId: String?,
    canShareOwnLocation: Boolean,
    onBack: () -> Unit,
    viewModel: ShareLocationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isSharing by viewModel.isSharing.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val rowsFlow = remember(visibleCongregationId, visibleGroupId) {
        viewModel.rowsFor(visibleCongregationId, visibleGroupId, currentPersonId)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // "Enable sharing" and "refresh my own coordinates" both need the same
    // permission, so one launcher covers both — which action triggered it is
    // tracked so the right thing happens once the user responds to the
    // system dialog, rather than always defaulting to one of the two.
    var pendingAction by remember { mutableStateOf<PendingLocationAction?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            when (pendingAction) {
                PendingLocationAction.ENABLE_SHARING -> viewModel.toggleSharing(true, currentPersonId, visibleCongregationId, visibleGroupId)
                PendingLocationAction.REFRESH -> viewModel.refreshMyLocation()
                null -> Unit
            }
        }
        pendingAction = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (canShareOwnLocation) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Share my location while preaching", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isSharing,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                pendingAction = PendingLocationAction.ENABLE_SHARING
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                viewModel.toggleSharing(false, currentPersonId, visibleCongregationId, visibleGroupId)
                            }
                        },
                    )
                }

                MyCurrentLocationCard(
                    hasPermission = viewModel.hasLocationPermission(),
                    location = myLocation,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        if (viewModel.hasLocationPermission()) {
                            viewModel.refreshMyLocation()
                        } else {
                            pendingAction = PendingLocationAction.REFRESH
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }

            if (rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No one is currently sharing their location.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.person.id }) { row ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Updated ${dateFormat.format(Date(row.location.updatedAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = {
                                    val uri = Uri.parse("geo:${row.location.lat},${row.location.lng}?q=${row.location.lat},${row.location.lng}(${row.person.fullName})")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }) { Text("Open in Maps") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Share Location – Show Current Coordinates" spec §1. Deliberately
 * independent of the sharing toggle above — "where am I right now" and "is
 * my group allowed to see where I am" are two different questions, and a
 * Publisher may want the former without the latter.
 */
@Composable
private fun MyCurrentLocationCard(
    hasPermission: Boolean,
    location: MyLocationState?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("My Current Location", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
            }
            when {
                // Spec §1: "If location permission is disabled, provide an
                // appropriate message directing the user to enable it" —
                // rather than a silent no-op or a raw platform error.
                !hasPermission -> Text(
                    "Location permission is turned off. Tap Refresh Location to grant it and see your current coordinates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                location != null -> {
                    Text("Latitude: ${"%.6f".format(location.fix.lat)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Longitude: ${"%.6f".format(location.fix.lng)}", style = MaterialTheme.typography.bodyMedium)
                    if (location.fix.accuracyMeters != null) {
                        Text("Accuracy: ${location.fix.accuracyMeters.toInt()} meters", style = MaterialTheme.typography.bodyMedium)
                    }
                    // Spec §1: "clearly indicate when the location was
                    // successfully captured" — a timestamp, not just numbers.
                    Text(
                        "Captured at ${timeFormat.format(Date(location.capturedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Text(
                    "Not captured yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.padding(top = 8.dp)) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                }
                Text("REFRESH LOCATION")
            }
        }
    }
}
