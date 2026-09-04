package com.emfitsolutions.gopreach.ui.screens.sharelocation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.LocationSharingSettings
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Shared Location Module — List View and Map View... same design,
 * behavior, and functionality already implemented in the Territory Map
 * Module" — same toggle concept/wording/icon convention as Territory Map's
 * own `TerritoryViewMode` (a distinct, private enum rather than a shared
 * one: two independent, unrelated screens that happen to offer the same two
 * modes shouldn't be coupled just because their labels currently match). */
private enum class ShareLocationViewMode(val label: String) { LIST("List View"), MAP("Map View") }

/**
 * "Simplify Share Location Module with Automatic Real-Time Updates" spec —
 * §6.1 Share Location. [canShareOwnLocation] gates the "share my location"
 * toggle to publishers only, per spec ("Publisher: can share own location
 * while preaching"); every role can view whoever's currently sharing within
 * their own scope. [canManageLocationSettings] additionally shows the
 * "SHARE LOCATION SETTINGS" gear (Super-Admin/Admin/Service Overseer/
 * Coordinator Elder/Regular Elder, own congregation for anyone but
 * Super-Admin). [onOpenTerritoryMap] is how a coordinate pair (this
 * publisher's own, or any "sharing now" row's) opens GoPreach's own
 * Territory Map centered on that point — internal navigation, deliberately
 * not an external Maps app, per this spec's "clicking should open the app's
 * own Map View" requirement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLocationScreen(
    currentPersonId: String,
    currentPersonName: String,
    visibleCongregationId: String?,
    canShareOwnLocation: Boolean,
    canManageLocationSettings: Boolean = false,
    ownCongregationId: String? = null,
    onBack: () -> Unit,
    onOpenTerritoryMap: (lat: Double, lng: Double, name: String) -> Unit,
    viewModel: ShareLocationViewModel = hiltViewModel(),
) {
    val isSharingFlow = remember(currentPersonId) { viewModel.isSharingFor(currentPersonId) }
    val isSharing by isSharingFlow.collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(currentPersonId) { viewModel.observeOwnSharedLocation(currentPersonId) }
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val rowsFlow = remember(visibleCongregationId, searchQuery) {
        viewModel.rowsFor(visibleCongregationId, currentPersonId, searchQuery)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy – h:mm a", Locale.getDefault()) }
    // "The default view can be List View" — same [rows] backs both views
    // (already scoped/searched identically), so switching never loses the
    // active search/filter.
    var viewMode by remember { mutableStateOf(ShareLocationViewMode.LIST) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    val showToast = rememberActionToast()

    // "Do not show repetitive notifications every time the automatic
    // 5-minute update occurs" — this one-time flag is reset whenever a new
    // sharing session starts, and consumed the first time a real fix lands
    // for that session, so the "Your current location has been updated."
    // toast fires exactly once per ON period, never on the later 5-minute
    // refreshes that follow it silently.
    var hasShownFirstLocationToast by remember { mutableStateOf(false) }
    var wasSharing by remember { mutableStateOf(isSharing) }
    LaunchedEffect(isSharing) {
        if (isSharing && !wasSharing) hasShownFirstLocationToast = false
        wasSharing = isSharing
    }
    LaunchedEffect(isSharing, myLocation?.capturedAt) {
        if (isSharing && myLocation != null && !hasShownFirstLocationToast) {
            hasShownFirstLocationToast = true
            showToast("Your current location has been updated.")
        }
    }

    // "Immediately display a clear system action message... Immediately
    // change the button/status to Location Sharing ON" — both happen right
    // here, synchronously with the call that actually activates sharing
    // (toggleSharing sets its own optimistic override before doing anything
    // else — see that function's doc comment), not deferred until a GPS fix
    // or server round-trip confirms anything.
    fun activateSharing() {
        viewModel.toggleSharing(true, currentPersonId, visibleCongregationId, null)
        showToast("Location sharing is now active.")
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            activateSharing()
        } else {
            // "Provide clear instructions... rather than allowing the
            // application to fail silently" — a denied permission used to
            // just leave the Switch off with zero explanation.
            showToast("Location permission is required to share your location.")
        }
    }

    fun startSharing() {
        if (viewModel.isLocationServicesEnabled()) {
            if (viewModel.hasLocationPermission()) {
                activateSharing()
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            showToast("Location services are disabled. Please enable GPS to continue.")
        }
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
                    // "Provide clear buttons, tabs, or a toggle for
                    // switching between List View and Map View" — same
                    // icon-toggle convention as Territory Map's own top-bar
                    // action.
                    IconButton(onClick = { viewMode = if (viewMode == ShareLocationViewMode.MAP) ShareLocationViewMode.LIST else ShareLocationViewMode.MAP }) {
                        Icon(
                            if (viewMode == ShareLocationViewMode.MAP) Icons.Rounded.ViewList else Icons.Rounded.Map,
                            contentDescription = if (viewMode == ShareLocationViewMode.MAP) "Switch to List View" else "Switch to Map View",
                        )
                    }
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
                // "Show only essential location information: Publisher Name,
                // Sharing Status, Latitude, Longitude, Last Updated. Do not
                // display unnecessary map previews or complicated location
                // controls." — a colored status card, nothing more.
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSharing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(currentPersonName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // [isSharing] already reflects the tap itself
                                // (an optimistic override — see
                                // ShareLocationViewModel.toggleSharing), not
                                // whatever GPS/server round-trip is still
                                // happening underneath.
                                if (isSharing) "🟢 Location Sharing: ON" else "Location Sharing: OFF",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = isSharing,
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
                                        // "Stop all scheduled location updates
                                        // immediately when Location Sharing is
                                        // turned OFF" — same optimistic
                                        // override, the other way.
                                        viewModel.toggleSharing(false, currentPersonId, visibleCongregationId, null)
                                        showToast("Location sharing stopped successfully.")
                                    }
                                },
                            )
                        }
                        if (isSharing && myLocation != null) {
                            InternalCoordinatesText(
                                lat = myLocation!!.fix.lat,
                                lng = myLocation!!.fix.lng,
                                onClick = { onOpenTerritoryMap(myLocation!!.fix.lat, myLocation!!.fix.lng, currentPersonName) },
                            )
                            Text(
                                "Last Updated: ${dateFormat.format(Date(myLocation!!.capturedAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (isSharing) {
                            Text(
                                "Acquiring your current location…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Only other publishers in your congregation see it, and it stops on its own after the configured time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }

            Text(
                "Sharing now (${rows.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).padding(top = if (canShareOwnLocation) 0.dp else 8.dp),
            )

            if (viewMode == ShareLocationViewMode.MAP) {
                // "Use the same working map implementation and functionality
                // as the Territory Map Module... ensure the map loads
                // correctly and does not display a blank screen" — built on
                // the exact same [com.emfitsolutions.gopreach.ui.components
                // .map.LeafletMapView] wrapper Territory Map itself uses.
                ShareLocationMapView(
                    rows = rows,
                    onOpenTerritoryMap = onOpenTerritoryMap,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search: Name, Status, Group" + if (visibleCongregationId == null) ", Congregation" else "") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        row.groupName?.let { "${row.person.fullName} ($it)" } ?: row.person.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    if (row.category != null) {
                                        Text("Status: ${row.category.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Congregation: ${row.congregationName}", style = MaterialTheme.typography.bodySmall)
                                    InternalCoordinatesText(
                                        lat = row.location.lat,
                                        lng = row.location.lng,
                                        onClick = { onOpenTerritoryMap(row.location.lat, row.location.lng, row.person.fullName) },
                                    )
                                    Text(
                                        "Last Updated: ${dateFormat.format(Date(row.location.updatedAt))}",
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

/** "Make the coordinates clickable... open the app's own Map View, centered
 * on the Publisher's latest location" — deliberately separate from the
 * shared [com.emfitsolutions.gopreach.ui.components.ClickableCoordinatesText]
 * component (which the rest of the app uses to open an *external* Maps app):
 * this screen's coordinates navigate to GoPreach's own Territory Map
 * instead, so the two must not share an implementation. */
@Composable
private fun InternalCoordinatesText(
    lat: Double,
    lng: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Text(
        "Latitude: $lat   Longitude: $lng",
        style = style,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = modifier.clickable(onClick = onClick),
    )
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
