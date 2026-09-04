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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.LocationSharingSettings
import androidx.compose.ui.text.font.FontWeight
import com.emfitsolutions.gopreach.ui.components.ClickableCoordinatesText
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingLocationAction { ENABLE_SHARING, REFRESH }

/**
 * Spec §6.1 — Share Location. [canShareOwnLocation] gates the "share my
 * location" toggle to publishers only, per spec ("Publisher: can share own
 * location while preaching"); every role can view whoever's currently sharing
 * within their own scope. [canManageLocationSettings] additionally shows the
 * "SHARE LOCATION SETTINGS" gear (Super-Admin/Admin/Service Overseer/
 * Coordinator Elder/Regular Elder, own congregation for anyone but
 * Super-Admin).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLocationScreen(
    currentPersonId: String,
    visibleCongregationId: String?,
    canShareOwnLocation: Boolean,
    canManageLocationSettings: Boolean = false,
    ownCongregationId: String? = null,
    onBack: () -> Unit,
    viewModel: ShareLocationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isSharingFlow = remember(currentPersonId) { viewModel.isSharingFor(currentPersonId) }
    val isSharing by isSharingFlow.collectAsStateWithLifecycle(initialValue = false)
    val isStarting by viewModel.isStarting.collectAsStateWithLifecycle()
    LaunchedEffect(currentPersonId) { viewModel.observeOwnSharedLocation(currentPersonId) }
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val rowsFlow = remember(visibleCongregationId, searchQuery) {
        viewModel.rowsFor(visibleCongregationId, currentPersonId, searchQuery)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    val showToast = rememberActionToast()

    // "Enable sharing" and "refresh my own coordinates" both need the same
    // permission, so one launcher covers both — which action triggered it is
    // tracked so the right thing happens once the user responds to the
    // system dialog, rather than always defaulting to one of the two.
    var pendingAction by remember { mutableStateOf<PendingLocationAction?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            when (pendingAction) {
                PendingLocationAction.ENABLE_SHARING -> viewModel.toggleSharing(true, currentPersonId, visibleCongregationId, null)
                PendingLocationAction.REFRESH -> viewModel.refreshMyLocation { success ->
                    if (!success) showToast("Unable to update your location. Please try again.")
                }
                null -> Unit
            }
        } else {
            // "Provide clear instructions... rather than allowing the
            // application to fail silently" — a denied permission used to
            // just leave the Switch off with zero explanation.
            showToast("Location permission is required to share your location.")
        }
        pendingAction = null
    }

    // "Check whether GPS/location services are enabled" — shared by Start
    // Sharing and Refresh Location, since both need a real fix; a specific
    // message here instead of a generic failure once getCurrentLocation()
    // predictably comes back null.
    fun withLocationServicesEnabled(thenRun: () -> Unit) {
        if (viewModel.isLocationServicesEnabled()) {
            thenRun()
        } else {
            showToast("Location services are disabled. Please enable GPS to continue.")
        }
    }

    fun startSharing() {
        withLocationServicesEnabled {
            if (viewModel.hasLocationPermission()) {
                viewModel.toggleSharing(true, currentPersonId, visibleCongregationId, null)
            } else {
                pendingAction = PendingLocationAction.ENABLE_SHARING
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // "Location sharing started successfully." — fired once sharing is
    // genuinely confirmed live (isSharing flips true), not the instant the
    // toggle is tapped; the status card's "Starting location sharing…" text
    // already covers the in-between wait (see [viewModel.isStarting]).
    var wasSharing by remember { mutableStateOf(false) }
    LaunchedEffect(isSharing) {
        if (isSharing && !wasSharing) showToast("Location sharing started successfully.")
        wasSharing = isSharing
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
                actions = {
                    if (canManageLocationSettings) {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Share Location Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (canShareOwnLocation) {
                // A colored status card — surfaceVariant off, primaryContainer
                // on — makes "am I currently sharing" readable at a glance
                // rather than only from the Switch's own tiny visual state.
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSharing || isStarting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when {
                                        isSharing -> "You are sharing your location"
                                        // "The publisher cannot open Share
                                        // Location fast" — this responds the
                                        // instant the toggle is tapped, well
                                        // before the first real fix confirms,
                                        // so it never reads as unresponsive.
                                        isStarting -> "Starting location sharing…"
                                        else -> "Share my location while preaching"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (isSharing) {
                                    Text(
                                        "Visible to other publishers in your congregation",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else if (isStarting) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                        Text(
                                            "Getting your location…",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 6.dp),
                                        )
                                    }
                                }
                            }
                            Switch(
                                checked = isSharing || isStarting,
                                enabled = !isStarting,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        // "There must be a pop up message that
                                        // the user will allow the app to share
                                        // location coordinates" — an app-level
                                        // consent step, separate from (and
                                        // shown before) the OS location-
                                        // permission prompt below.
                                        showConsentDialog = true
                                    } else {
                                        viewModel.toggleSharing(false, currentPersonId, visibleCongregationId, null)
                                        showToast("Location sharing stopped successfully.")
                                    }
                                },
                            )
                        }
                        if (!isSharing && !isStarting) {
                            Text(
                                "Only other publishers in your congregation see it, and it stops on its own after the configured time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                MyCurrentLocationCard(
                    hasPermission = viewModel.hasLocationPermission(),
                    location = myLocation,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        withLocationServicesEnabled {
                            if (viewModel.hasLocationPermission()) {
                                viewModel.refreshMyLocation { success ->
                                    if (!success) showToast("Unable to update your location. Please try again.")
                                }
                            } else {
                                pendingAction = PendingLocationAction.REFRESH
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search: Name, Status, Group" + if (visibleCongregationId == null) ", Congregation" else "") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Text(
                "Sharing now (${rows.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

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
                        val address by produceState<String?>(initialValue = null, row.location.lat, row.location.lng) {
                            value = viewModel.addressFor(row.location.lat, row.location.lng)
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val uri = Uri.parse(
                                    "geo:${row.location.lat},${row.location.lng}?q=${row.location.lat},${row.location.lng}(${row.person.fullName})",
                                )
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    row.groupName?.let { "${row.person.fullName} ($it)" } ?: row.person.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (row.category != null) {
                                    Text("Status: ${row.category.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Congregation: ${row.congregationName}", style = MaterialTheme.typography.bodySmall)
                                ClickableCoordinatesText(
                                    lat = row.location.lat,
                                    lng = row.location.lng,
                                    label = row.person.fullName,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Location: ${address ?: "Resolving address…"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Updated ${dateFormat.format(Date(row.location.updatedAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text("Share Your Location?") },
            text = {
                Text(
                    "GoPreach will share your live location coordinates with other publishers in your congregation while you're preaching. " +
                        "Sharing stops automatically after the configured time, or whenever you turn it off. Allow location sharing?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConsentDialog = false
                        startSharing()
                    },
                ) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { showConsentDialog = false }) { Text("Cancel") } },
        )
    }

    if (showSettingsDialog) {
        LocationSharingSettingsDialog(
            fixedCongregationId = ownCongregationId,
            congregations = viewModel.congregations.collectAsStateWithLifecycle().value,
            currentPersonId = currentPersonId,
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
        )
    }
}

/** "SHARE LOCATION SETTINGS" — Location Sharing Time + Accuracy Radius, per
 * congregation. [fixedCongregationId] null means Super-Admin (picks any
 * congregation); non-null means already scoped to that one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSharingSettingsDialog(
    fixedCongregationId: String?,
    congregations: List<Congregation>,
    currentPersonId: String,
    viewModel: ShareLocationViewModel,
    onDismiss: () -> Unit,
) {
    var pickedCongregationId by remember { mutableStateOf(fixedCongregationId ?: congregations.firstOrNull()?.id) }
    val congregationId = fixedCongregationId ?: pickedCongregationId

    val settings by (
        if (congregationId != null) viewModel.settingsFor(congregationId)
        else kotlinx.coroutines.flow.flowOf(LocationSharingSettings())
        ).collectAsStateWithLifecycle(initialValue = LocationSharingSettings())

    var durationText by remember(congregationId, settings.sharingDurationMinutes) { mutableStateOf(settings.sharingDurationMinutes.toString()) }
    var accuracyText by remember(congregationId, settings.accuracyRadiusMeters) { mutableStateOf(settings.accuracyRadiusMeters.toString()) }
    val showToast = rememberActionToast()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val duration = durationText.toIntOrNull()
        val accuracy = accuracyText.toIntOrNull()
        val message = requiredFieldsMessage(
            "Congregation" to (congregationId != null),
            "Location Sharing Time" to (duration != null && duration > 0),
            "Accuracy Radius" to (accuracy != null && accuracy > 0),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        viewModel.saveSettings(
            LocationSharingSettings(
                congregationId = congregationId!!,
                sharingDurationMinutes = duration!!,
                accuracyRadiusMeters = accuracy!!,
            ),
            currentPersonId,
        )
        showToast("Location sharing settings saved.")
        onDismiss()
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = "Share Location Settings",
        onConfirm = ::submit,
        confirmLabel = "Save",
        errorMessage = errorMessage,
        maxContentHeight = 420.dp,
    ) {
                if (fixedCongregationId == null) {
                    CongregationSettingsDropdown(
                        congregations = congregations,
                        selectedId = pickedCongregationId,
                        onSelected = { pickedCongregationId = it },
                    )
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                    label = { Text("Location Sharing Time (minutes)") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The publisher can share their location for this many minutes before it automatically stops.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = accuracyText,
                    onValueChange = { accuracyText = it.filter { c -> c.isDigit() } },
                    label = { Text("Accuracy Radius (meters)") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "A GPS fix less accurate than this is never shared — the last good position stays visible until a better one arrives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CongregationSettingsDropdown(congregations: List<Congregation>, selectedId: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
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
            congregations.forEach { congregation ->
                DropdownMenuItem(text = { Text(congregation.name) }, onClick = { onSelected(congregation.id); expanded = false })
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
                    ClickableCoordinatesText(lat = location.fix.lat, lng = location.fix.lng)
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
