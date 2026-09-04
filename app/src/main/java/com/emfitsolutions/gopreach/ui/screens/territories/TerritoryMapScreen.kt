package com.emfitsolutions.gopreach.ui.screens.territories

import android.Manifest
import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.emfitsolutions.gopreach.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.formatCoordinatesDms
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.ui.components.isValidLatitude
import com.emfitsolutions.gopreach.ui.components.isValidLongitude
import com.emfitsolutions.gopreach.ui.components.openCoordinatesInMaps
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "TerritoryMap"

private enum class TerritoryViewMode(val label: String) { LIST("List View"), MAP("Map View") }

/**
 * "Territory Module" — a read-only directory of every Searching/Return
 * Visit/Bible Study record that has a saved GPS location, searchable by
 * name or location in [TerritoryViewMode.LIST]; [TerritoryViewMode.MAP] is
 * the primary, full-screen experience — see [TerritoryLiveMap]'s own doc
 * comment for the redesign this screen defers to it for. List View's rows
 * tap out to Google Maps (or whatever the device offers for a `geo:` URI),
 * same as every other saved coordinate in this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryMapScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    canSeePublisherLocations: Boolean,
    // "Clicking coordinates should open the Territory Map centered on the
    // Publisher's latest location" — non-null only when reached via Share
    // Location's own "open in Territory Map" action (see
    // Destinations.territoryMapFocusedOn); every other entry point leaves
    // these null and this screen behaves exactly as before.
    focusLat: Double? = null,
    focusLng: Double? = null,
    focusName: String? = null,
    onBack: () -> Unit,
    viewModel: TerritoryMapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // "For publisher account they can see other publishers that share their
    // location in the map" — only collected (and only ever asked of the
    // repository) when [canSeePublisherLocations] actually allows it, so an
    // unauthorized role's screen never even subscribes to every other
    // publisher's live position.
    val publisherRowsFlow = remember(fixedCongregationId, currentPersonId, canSeePublisherLocations) {
        if (canSeePublisherLocations) viewModel.publisherRowsFor(fixedCongregationId, currentPersonId) else flowOf(emptyList())
    }
    val publisherRows by publisherRowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    // "Full-Screen Map View... the map occupies the entire available
    // screen" — Map View is now the default landing mode; List View is
    // still reachable (existing functionality preserved per spec §17.11)
    // via the toggle in the top bar's own actions instead of a persistent
    // segmented-button row that used to eat vertical space in both modes.
    var viewMode by remember { mutableStateOf(TerritoryViewMode.MAP) }

    // Matches the resolved (reverse-geocoded) address once it's in, but also
    // the person's own typed address and raw coordinates, so a search never
    // has to wait on that network lookup landing for every row first.
    val filtered = remember(rows, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            rows
        } else {
            rows.filter { row ->
                row.person.name.contains(query, ignoreCase = true) ||
                    row.person.address.contains(query, ignoreCase = true) ||
                    row.congregationName.contains(query, ignoreCase = true) ||
                    row.resolvedLocation?.contains(query, ignoreCase = true) == true ||
                    (row.person.gpsLat != null && row.person.gpsLng != null &&
                        formatCoordinatesDms(row.person.gpsLat, row.person.gpsLng).contains(query, ignoreCase = true))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Territory Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewMode = if (viewMode == TerritoryViewMode.MAP) TerritoryViewMode.LIST else TerritoryViewMode.MAP }) {
                        Icon(
                            if (viewMode == TerritoryViewMode.MAP) Icons.Rounded.ViewList else Icons.Rounded.Map,
                            contentDescription = if (viewMode == TerritoryViewMode.MAP) "Switch to List View" else "Switch to Map View",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (viewMode == TerritoryViewMode.MAP) {
            // Full-bleed — no search bar, no segmented row, nothing between
            // the top bar and the map itself; every remaining control floats
            // over the map (see TerritoryLiveMap).
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                TerritoryLiveMap(
                    rows = rows,
                    publisherRows = publisherRows,
                    canSeePublisherLocations = canSeePublisherLocations,
                    getCurrentLocation = viewModel::currentLocation,
                    hasLocationPermission = viewModel::hasLocationPermission,
                    focusLat = focusLat,
                    focusLng = focusLng,
                    focusName = focusName,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by Name or Location") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (rows.isEmpty()) "No saved locations yet — every Searching, Return Visit, and Bible Study record with a saved location will show up here." else "No matches for \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.person.id }) { row ->
                            val lat = row.person.gpsLat
                            val lng = row.person.gpsLng
                            Card(
                                modifier = Modifier.fillMaxWidth().let {
                                    if (lat != null && lng != null) it.clickable { openCoordinatesInMaps(context, lat, lng, row.person.name) } else it
                                },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // "Make every person/location immediately
                                    // identifiable without relying only on
                                    // text labels" — same per-category emoji
                                    // Map View's own markers/legend use.
                                    Text(emojiFor(row.person.pipelineStage.toMapPointKind()), style = MaterialTheme.typography.titleMedium)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(row.person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Status: ${row.person.pipelineStage.statusLabel()}", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Location: ${row.resolvedLocation ?: (if (lat != null && lng != null) formatCoordinatesDms(lat, lng) else "—")}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text("Congregation: ${row.congregationName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun PipelineStage.statusLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

/** The Territory Map's own filter — "the dropdown must contain exactly
 * these options." A `null` [MapFilterOption] (nothing picked yet, or after
 * Refresh) is the default/original view: every pipeline record, no
 * publishers — not itself one of the seven, since the dropdown lists
 * exactly these seven and no more. */
private enum class MapFilterOption(val label: String, val emoji: String) {
    // "Add 'All' in the category" — every classification (including
    // Publishers, when [canSeePublisherLocations] allows it) shown at once.
    ALL("All", "🗂️"),
    MY_LOCATION("My Location", "📍"),
    BIBLE_STUDY("Bible Study", "📖"),
    RETURN_VISIT("Return Visit", "🔄"),
    // "Publisher – All/My Congregation" and "Nearest Publisher" are no
    // longer offered in the dropdown (see the DropdownMenu below, which
    // always skips both) — kept here only because [applySelection]'s
    // Publisher-kind filtering logic and the "open in Territory Map from
    // Share Location" focus effect still reuse it internally.
    PUBLISHERS("Publisher – All Congregation", "👤"),
    NEAREST_PUBLISHER("Nearest Publisher", "👤"),
    NEAREST_BIBLE_STUDY("Nearest Bible Study", "📖"),
    NEAREST_RETURN_VISIT("Nearest Return Visit", "🔄"),
}

/**
 * "Map View" — a real, full-screen, pinch-zoomable embedded map. A
 * `WebView` running Leaflet over OpenStreetMap tiles, not Google Maps
 * Compose — this app has never bundled a Google Maps API key (every other
 * screen that touches a map hands routing off to the device's own Maps app
 * instead — see [com.emfitsolutions.gopreach.ui.screens.findlocation
 * .FindLocationScreen]'s own doc comment) — and Leaflet+OSM needs no key or
 * new Gradle dependency at all, just the CDN scripts loaded inside the page
 * HTML this generates.
 *
 * "Publishers, Bible Studies, and Return Visits are separate
 * classifications. A person having a GPS location does not automatically
 * make that person a Publisher." — enforced structurally: [MapPoint.kind]
 * is set once, at construction, from the record's *own* type ([pipelinePoints]
 * from [TerritoryMapRow.person]'s [PipelineStage], [publisherPoints] only
 * from an actively-sharing, [com.emfitsolutions.gopreach.data.model.PublisherCategory.REGULAR_PUBLISHER]
 * [TerritoryPublisherRow] — see [TerritoryMapViewModel.publisherRowsFor]'s
 * own doc comment for that filter). Nothing downstream (the dropdown, the
 * marker style, the bottom sheet) can blur that line — there is no path
 * from "has coordinates" to "counts as a Publisher."
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerritoryLiveMap(
    rows: List<TerritoryMapRow>,
    publisherRows: List<TerritoryPublisherRow>,
    canSeePublisherLocations: Boolean,
    getCurrentLocation: suspend () -> LatLng?,
    hasLocationPermission: () -> Boolean,
    focusLat: Double? = null,
    focusLng: Double? = null,
    focusName: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // STEP 4 — validate every coordinate before it ever reaches the map:
    // not null, numeric (guaranteed by the Double type itself, but NaN/
    // Infinite still slip through arithmetic and aren't valid geographic
    // points), and within real lat/lng range. A record that fails this
    // never reaches Leaflet at all — it's counted separately instead, so
    // one bad row can't take the whole map down.
    val pipelinePoints = remember(rows) {
        rows.mapNotNull { row ->
            val lat = row.person.gpsLat
            val lng = row.person.gpsLng
            if (lat != null && lng != null && lat.isFinite() && lng.isFinite() && isValidLatitude(lat) && isValidLongitude(lng)) {
                MapPoint(
                    id = "pipeline_${row.person.id}",
                    // "Status must control the marker" — read fresh from the
                    // record's own current pipelineStage every time [rows]
                    // recomposes (a live Firestore listener), so a reverse/
                    // forward status move immediately swaps the marker's
                    // icon rather than ever retaining a stale one.
                    kind = row.person.pipelineStage.toMapPointKind(),
                    lat = lat,
                    lng = lng,
                    name = row.person.name,
                    status = row.person.pipelineStage.statusLabel(),
                    location = row.resolvedLocation ?: formatCoordinatesDms(lat, lng),
                    congregation = row.congregationName,
                    coords = formatCoordinatesDms(lat, lng),
                    updatedAt = null,
                    isCurrentlySharing = null,
                )
            } else {
                null
            }
        }
    }
    // "Only Regular Publishers enrolled in the congregation and actively
    // sharing their location may appear" — [publisherRows] already enforces
    // that upstream (see TerritoryMapViewModel.publisherRowsFor); hidden by
    // default here too (opt-in via the dropdown's own "Publisher – All
    // Congregation"/"Nearest Publisher" entries), never automatic.
    val publisherPoints = remember(publisherRows) {
        publisherRows.mapNotNull { row ->
            val lat = row.lat
            val lng = row.lng
            if (lat.isFinite() && lng.isFinite() && isValidLatitude(lat) && isValidLongitude(lng)) {
                MapPoint(
                    id = "publisher_${row.person.id}",
                    kind = MapPointKind.PUBLISHER,
                    lat = lat,
                    lng = lng,
                    name = row.person.fullName,
                    status = row.category?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Publisher",
                    location = "—",
                    congregation = row.congregationName,
                    coords = formatCoordinatesDms(lat, lng),
                    updatedAt = row.updatedAt,
                    isCurrentlySharing = row.isCurrentlySharing,
                )
            } else {
                null
            }
        }
    }
    val points = remember(pipelinePoints, publisherPoints) { pipelinePoints + publisherPoints }
    val invalidCount = rows.size - pipelinePoints.size

    // STEP 8 — diagnostics: same information a `debug` panel would show,
    // just in Logcat rather than on-screen (this app's other diagnostic UI —
    // SyncStatusButton, etc. — is text/user-facing, not a raw debug dump).
    LaunchedEffect(rows, publisherRows) {
        Log.d(TAG, "Records retrieved: ${rows.size}; valid GPS: ${pipelinePoints.size}; invalid/missing GPS: $invalidCount; publishers sharing: ${publisherPoints.size}")
    }

    // "Add the user current location in the map view" — fetched on demand
    // (the "My Location" filter, or automatically the first time a "Nearest…"
    // filter needs it), not on every screen open, so this never surprises
    // anyone with a permission prompt they didn't ask for.
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingPermissionFilter by remember { mutableStateOf<MapFilterOption?>(null) }
    var selectedFilter by remember { mutableStateOf<MapFilterOption?>(null) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    // "Be collapsible/minimizable... not cover important map controls" —
    // starts collapsed so it never obscures the map on first load; the
    // "LEGEND" chip is always visible to expand it again.
    var legendExpanded by remember { mutableStateOf(false) }
    var selectedPointId by remember { mutableStateOf<String?>(null) }
    // "Show details in all categories in territory map even 'My Location'" —
    // a synthetic point built from the live GPS fix, id "me", so tapping the
    // "you are here" dot opens the exact same bottom sheet every other
    // marker already does, without making it a real member of [points]
    // (which would risk it being counted by a dropdown filter/nearest search
    // it was never meant to participate in).
    val myLocationPoint = remember(myLocation) {
        myLocation?.let { fix ->
            MapPoint(
                id = "me",
                kind = MapPointKind.ME,
                lat = fix.lat,
                lng = fix.lng,
                name = "My Location",
                status = "Your Current Location",
                location = "—",
                congregation = "",
                coords = formatCoordinatesDms(fix.lat, fix.lng),
                updatedAt = null,
                isCurrentlySharing = null,
            )
        }
    }
    val selectedPoint = remember(selectedPointId, points, myLocationPoint) {
        if (selectedPointId == "me") myLocationPoint else points.firstOrNull { it.id == selectedPointId }
    }

    val html = remember(points) { buildTerritoryMapHtml(points) }
    // Bumped on a manual "Retry" tap to force a reload of the same [html].
    var reloadToken by remember { mutableIntStateOf(0) }
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // "Add debugging checks" — captured on-device (no adb/Logcat access
    // needed to report back what actually happened) rather than only
    // written to Logcat; a real JS-side failure (a CDN script genuinely
    // unreachable, a syntax error, anything Leaflet itself threw) shows up
    // here verbatim instead of this screen staying a silent, unexplained
    // blank.
    val consoleMessages = remember { mutableStateListOf<String>() }
    var showDiagnostics by remember { mutableStateOf(false) }

    // Bug fix #3 ("still not working" after both prior fixes — confirmed
    // live on an emulator: the outer Compose Box was correctly sized (a
    // sibling IconButton aligned to its bottom-end landed at the real
    // screen bottom), yet Leaflet's own container measured 0 height even
    // after invalidateSize(), and on-device accessibility dumped the
    // Leaflet attribution control at a ~0px-tall rect near the *top* of the
    // page — not the bottom of the real container. That's not a CSS bug:
    // it means Chromium itself locked in a stale near-zero viewport at the
    // exact moment loadDataWithBaseURL() ran, and never redid that internal
    // layout afterward — the same "measure once, never again" trap Leaflet
    // has, just one layer deeper, in the WebView engine itself. No amount
    // of invalidateSize()/CSS can fix a viewport WebView never recomputes.
    // Waiting for `containerReady` (the AndroidView's first real, non-zero
    // Compose-measured size) before ever calling loadDataWithBaseURL means
    // Chromium's very first layout pass already sees the correct size, the
    // same way a properly-sized browser window would.
    var containerReady by remember { mutableStateOf(false) }

    // Pushes a fresh GPS fix into the page as a distinct "You are here"
    // marker ([buildTerritoryMapHtml]'s `window.setMyLocation`) — shared by
    // every filter below that needs one, so a fix obtained once during this
    // screen's lifetime never needs asking twice. Surfaces the two specific
    // failure states §14 calls out ("Location permission is required…",
    // "GPS is currently disabled…") as Snackbars rather than silently doing
    // nothing.
    suspend fun ensureMyLocation(): LatLng? {
        myLocation?.let { return it }
        if (!hasLocationPermission()) {
            snackbarHostState.showSnackbar("Location permission is required to display your current position.")
            return null
        }
        val fix = getCurrentLocation()
        if (fix == null) {
            snackbarHostState.showSnackbar("GPS is currently disabled. Please enable location services.")
            return null
        }
        myLocation = fix
        webViewRef?.evaluateJavascript("if (window.setMyLocation) { window.setMyLocation(${fix.lat}, ${fix.lng}); }", null)
        return fix
    }

    // "The selected option determines what markers and information are
    // displayed on the map" + "§12 Automatic Map Behavior" — the one place
    // every dropdown entry (and Refresh, and a fresh page load) routes
    // through, so the camera/filter/empty-state logic for each option lives
    // in exactly one spot.
    suspend fun applySelection(option: MapFilterOption?) {
        val webView = webViewRef
        selectedFilter = option
        selectedPointId = null
        when (option) {
            null -> {
                // "Original state" — every pipeline stage, no publishers.
                val kinds = listOf(MapPointKind.SEARCHING, MapPointKind.RETURN_VISIT, MapPointKind.BIBLE_STUDY)
                webView?.evaluateJavascript("if (window.applyFilter) { window.applyFilter('${kinds.joinToString(",") { it.jsValue }}'); }", null)
            }
            MapFilterOption.ALL -> {
                // "Add 'All' in the category" — every classification at
                // once, including Publishers (an empty list, and so a no-op
                // here, when [canSeePublisherLocations] doesn't allow it).
                val kinds = listOf(MapPointKind.SEARCHING, MapPointKind.RETURN_VISIT, MapPointKind.BIBLE_STUDY, MapPointKind.PUBLISHER)
                webView?.evaluateJavascript("if (window.applyFilter) { window.applyFilter('${kinds.joinToString(",") { it.jsValue }}'); }", null)
            }
            MapFilterOption.MY_LOCATION -> {
                val fix = ensureMyLocation() ?: return
                // "Do not treat the logged-in user's location as a Bible
                // Study or Return Visit" — shows nothing from [points] at
                // all while this is selected, only the distinct "you are
                // here" marker (never a member of [points] to begin with).
                webView?.evaluateJavascript("if (window.applyFilter) { window.applyFilter(''); }", null)
                webView?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.setView([${fix.lat}, ${fix.lng}], 16); }", null)
            }
            MapFilterOption.BIBLE_STUDY, MapFilterOption.RETURN_VISIT, MapFilterOption.PUBLISHERS -> {
                val kind = when (option) {
                    MapFilterOption.BIBLE_STUDY -> MapPointKind.BIBLE_STUDY
                    MapFilterOption.RETURN_VISIT -> MapPointKind.RETURN_VISIT
                    else -> MapPointKind.PUBLISHER
                }
                if (points.none { it.kind == kind }) {
                    snackbarHostState.showSnackbar("No locations found for the selected category.")
                }
                webView?.evaluateJavascript("if (window.applyFilter) { window.applyFilter('${kind.jsValue}'); }", null)
            }
            MapFilterOption.NEAREST_PUBLISHER, MapFilterOption.NEAREST_BIBLE_STUDY, MapFilterOption.NEAREST_RETURN_VISIT -> {
                val kind = when (option) {
                    MapFilterOption.NEAREST_PUBLISHER -> MapPointKind.PUBLISHER
                    MapFilterOption.NEAREST_BIBLE_STUDY -> MapPointKind.BIBLE_STUDY
                    else -> MapPointKind.RETURN_VISIT
                }
                val fix = ensureMyLocation() ?: return
                val candidates = points.filter { it.kind == kind }
                if (candidates.isEmpty()) {
                    snackbarHostState.showSnackbar("No locations found for the selected category.")
                    webView?.evaluateJavascript("if (window.applyFilter) { window.applyFilter(''); }", null)
                    return
                }
                val nearest = candidates.sortedBy { haversineMeters(fix.lat, fix.lng, it.lat, it.lng) }.take(3)
                webView?.evaluateJavascript("if (window.applyFilter) { window.applyFilter('${kind.jsValue}'); }", null)
                webView?.evaluateJavascript("if (window.focusNearby) { window.focusNearby(${fix.lat}, ${fix.lng}, [${nearest.joinToString(",") { "'${jsEscape(it.id)}'" }}]); }", null)
                selectedPointId = nearest.first().id
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val option = pendingPermissionFilter
        pendingPermissionFilter = null
        if (granted && option != null) {
            scope.launch { applySelection(option) }
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("Location permission is required to display your current position.") }
        }
    }

    fun selectFilter(option: MapFilterOption?) {
        val needsLocation = option == MapFilterOption.MY_LOCATION ||
            option == MapFilterOption.NEAREST_PUBLISHER || option == MapFilterOption.NEAREST_BIBLE_STUDY || option == MapFilterOption.NEAREST_RETURN_VISIT
        if (needsLocation && myLocation == null && !hasLocationPermission()) {
            pendingPermissionFilter = option
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            scope.launch { applySelection(option) }
        }
    }

    // Bug fix ("I cannot see any actual map"): loading used to happen
    // directly inside AndroidView's `update` lambda, which Compose re-runs
    // on *every* recomposition of this composable — and writing `loadState`
    // there is itself read by the loading/error overlay below, so each
    // write triggered another recomposition, which re-ran `update`, which
    // reloaded the page again, forever. The map never got a chance to
    // finish loading — visually indistinguishable from "nothing renders."
    // A `LaunchedEffect` keyed on the content that should actually trigger
    // a (re)load — not on every recomposition — fixes that: it only re-runs
    // when [html], [reloadToken], the WebView instance, or [containerReady]
    // actually changes value (a Boolean flipping false->true once, never
    // back, so this can't reintroduce the reload loop above).
    LaunchedEffect(html, reloadToken, webViewRef, containerReady) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!containerReady) return@LaunchedEffect
        loadState = MapLoadState.LOADING
        // Posted rather than called inline: even after containerReady confirms
        // Compose/Android have assigned this View its final bounds, WebView's
        // own Chromium renderer process syncs its internal viewport size via a
        // separate, asynchronous channel that can still lag one full draw
        // pass behind the Java-side bounds — on-device evidence (below).
        // Posting to the next message-loop iteration guarantees at least one
        // real Android layout+draw traversal has completed with the correct
        // size before any content loads into it.
        webView.post {
            // A stable https base URL (rather than null/about:blank) so the CDN
            // script/tile requests aren't treated as mixed content.
            webView.loadDataWithBaseURL("https://gopreach.app/", html, "text/html", "UTF-8", null)
        }
    }

    // Re-applies whatever's currently selected once the (re)loaded page is
    // actually ready for JS calls — covers both the very first load and any
    // later reload triggered by [points] changing underneath (new/updated
    // records arriving live), so a live update never silently reverts the
    // map to "everything" out from under an active filter.
    LaunchedEffect(loadState, webViewRef) {
        if (loadState == MapLoadState.LOADED) {
            if (myLocation != null) {
                val fix = myLocation!!
                webViewRef?.evaluateJavascript("if (window.setMyLocation) { window.setMyLocation(${fix.lat}, ${fix.lng}); }", null)
            }
            applySelection(selectedFilter)
        }
    }

    // "Clicking coordinates should open the Territory Map centered on the
    // Publisher's latest location" — runs once, the first time the map
    // finishes loading with a focus target actually present (see
    // TerritoryMapScreen's own `focusLat`/`focusLng`, only ever non-null
    // when reached via Share Location's "open in Territory Map" action).
    // Switches to the Publisher filter first, since the target would
    // otherwise be hidden by the default pipeline-only view, then pans/
    // zooms there — highlighting the closest matching marker (should
    // always be the exact same publisher, since both screens read the same
    // underlying SharedLocation data) when one's found within a
    // realistic GPS-noise radius, or just centering the camera there
    // otherwise (the coordinates are still the latest available either way).
    var hasAppliedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(loadState, webViewRef, focusLat, focusLng) {
        if (loadState != MapLoadState.LOADED || hasAppliedFocus) return@LaunchedEffect
        val lat = focusLat
        val lng = focusLng
        if (lat == null || lng == null) return@LaunchedEffect
        hasAppliedFocus = true
        applySelection(MapFilterOption.PUBLISHERS)
        val nearest = points.filter { it.kind == MapPointKind.PUBLISHER }.minByOrNull { haversineMeters(lat, lng, it.lat, it.lng) }
        if (nearest != null && haversineMeters(lat, lng, nearest.lat, nearest.lng) < 100) {
            selectedPointId = nearest.id
            webViewRef?.evaluateJavascript("if (window.focusNearby) { window.focusNearby($lat, $lng, ['${jsEscape(nearest.id)}']); }", null)
        } else {
            webViewRef?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.setView([$lat, $lng], 17); }", null)
        }
    }

    // "Highlight the selected marker" — covers both a manual tap (which
    // already highlights itself immediately in JS, see the marker click
    // handlers above) and a Nearest-X auto-selection (set directly in
    // [applySelection], never via a JS tap), plus clears the highlight the
    // instant the bottom sheet is dismissed (selectedPointId -> null).
    LaunchedEffect(selectedPointId, webViewRef, loadState) {
        if (loadState == MapLoadState.LOADED) {
            val idJs = selectedPointId?.let { "'${jsEscape(it)}'" } ?: "null"
            webViewRef?.evaluateJavascript("if (window.setSelectedMarker) { window.setSelectedMarker($idJs); }", null)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            // Bug fix #2 ("still not working" after the reload-loop fix):
            // Leaflet measures its container's pixel size exactly once, the
            // moment `L.map(...)` runs, and never re-measures on its own —
            // if this WebView's own Android View hadn't been given its
            // final layout size yet at that exact instant (a real race:
            // the page can finish loading from cached CDN resources before
            // Compose/the Android View system has finished measuring this
            // AndroidView), Leaflet permanently thinks the viewport is 0×0
            // and never requests a single tile — a genuinely blank map
            // forever, even though the WebView/HTML/CSS are all otherwise
            // fine. `onSizeChanged` fires with this View's *actual* settled
            // pixel size every time Compose lays it out (including the
            // first time), and telling Leaflet to `invalidateSize()` right
            // then — its own official fix for exactly this — makes it
            // re-measure and actually start requesting tiles.
            modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    containerReady = true
                    webViewRef?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.invalidateSize(); }", null)
                }
            },
            factory = { ctx ->
                // Lets a debug build be inspected live from a PC via
                // chrome://inspect (USB debugging) — the single most direct
                // way to see the *actual* browser-side error when the
                // on-device diagnostics below aren't enough on their own.
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Leaflet handles pinch/double-tap zoom itself via touch
                    // events (see the `user-scalable=no` viewport meta in the
                    // generated HTML) — the WebView's own native zoom would
                    // otherwise fight Leaflet's for the same gesture.
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    // A WebView embedded via Compose interop can render
                    // solid black/blank on some devices under hardware-
                    // accelerated layering — a known WebView quirk, not
                    // specific to this page. Software layering is slightly
                    // slower to draw but reliably shows the actual page.
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    // "Display a compact information card/bottom sheet" —
                    // a marker tap calls back into Kotlin with just its id;
                    // the native ModalBottomSheet below looks the rest up
                    // from [points] itself, rather than round-tripping every
                    // field back out through the bridge as strings.
                    // @JavascriptInterface methods run on the WebView's own
                    // JS thread, not the UI thread — `self.post {}` (View.post
                    // always targets the UI thread's own Handler regardless
                    // of the calling thread) is what makes it safe to touch
                    // Compose state from here.
                    val self = this
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun showDetails(id: String) {
                                self.post { selectedPointId = id }
                            }
                        },
                        "AndroidBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loadState = MapLoadState.LOADED
                            // Belt-and-suspenders alongside onSizeChanged
                            // above and the JS-side setTimeout fallbacks in
                            // the generated page itself (see
                            // buildTerritoryMapHtml) — three independent
                            // triggers for the same one-line fix, since a
                            // blank Leaflet map from this exact cause is
                            // otherwise silent and easy to still hit on some
                            // device/timing combination.
                            view?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.invalidateSize(); }", null)
                            // Bug fix #4 (on-device evidence: the WebView's
                            // own native Android View bounds were confirmed
                            // correct via accessibility dump — [0,674][1080,
                            // 2337], real height — yet Leaflet's container
                            // still measured 0 height inside the page itself,
                            // even after invalidateSize() and even minutes
                            // later. That rules out a timing race entirely:
                            // Chromium's renderer process — which runs
                            // out-of-process from this app and syncs its own
                            // internal viewport size over a separate,
                            // asynchronous channel from the Java-side View
                            // bounds — never actually received the real size
                            // at all, not just "not yet." Calling
                            // requestLayout() alone is a no-op here since the
                            // View's Java-side size genuinely hasn't changed;
                            // the standard, well-documented fix for exactly
                            // this WebView symptom is forcing a real (if
                            // momentary) size change, which is the only thing
                            // that actually triggers WebView's internal
                            // onSizeChanged → Chromium resize path again.
                            view?.let { wv ->
                                val realHeight = wv.height
                                val lp = wv.layoutParams
                                if (realHeight > 0 && lp != null) {
                                    lp.height = realHeight - 1
                                    wv.layoutParams = lp
                                    wv.requestLayout()
                                    wv.post {
                                        lp.height = realHeight
                                        wv.layoutParams = lp
                                        wv.requestLayout()
                                        wv.postDelayed({
                                            wv.evaluateJavascript("if (window.territoryMap) { window.territoryMap.invalidateSize(); }", null)
                                        }, 50)
                                    }
                                }
                            }
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            // Only the top-level page failing counts as the
                            // map itself failing — a single missed sub-
                            // resource (one map tile timing out, say)
                            // shouldn't flip the whole view into an error
                            // state when the map is otherwise usable.
                            if (request?.isForMainFrame == true) {
                                loadState = MapLoadState.FAILED
                                val detail = "Main frame load error: ${error?.errorCode} ${error?.description} (${request.url})"
                                Log.e(TAG, detail)
                                consoleMessages.add(detail)
                            }
                        }
                    }
                    // Surfaces real browser-side JS errors (a CDN script
                    // that 404'd, a Leaflet exception, anything) directly
                    // on-device — see [consoleMessages]'s own doc comment.
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            val line = "${consoleMessage.messageLevel()}: ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                            Log.d(TAG, "WebView console: $line")
                            consoleMessages.add(line)
                            if (consoleMessages.size > 30) consoleMessages.removeAt(0)
                            return true
                        }
                    }
                }.also { webViewRef = it }
            },
            update = {},
        )

        if (loadState == MapLoadState.LOADING) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (loadState == MapLoadState.FAILED) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Unable to load the map. Check your internet connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                consoleMessages.lastOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { reloadToken++ }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Retry")
                    }
                    OutlinedButton(onClick = { showDiagnostics = true }) { Text("Details") }
                }
            }
        }

        if (loadState == MapLoadState.LOADED && invalidCount > 0) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp).padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Text(
                    "$invalidCount record${if (invalidCount == 1) "" else "s"} without a valid GPS location ${if (invalidCount == 1) "isn't" else "aren't"} shown on the map.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // "Add a prominent dropdown/filter control at the top of the map" —
        // floats directly over the map itself rather than taking its own
        // layout row, matching "keep only essential floating controls."
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { filterMenuExpanded = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // "When a category is selected, display the appropriate
                    // icon" — the generic Tune icon before anything's picked
                    // (there's no one category yet to represent), the exact
                    // same emoji every marker/legend/bottom-sheet entry for
                    // that category already uses once one is.
                    val currentFilter = selectedFilter
                    if (currentFilter != null) {
                        Text(currentFilter.emoji, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        selectedFilter?.label ?: "All Records",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }) {
                    MapFilterOption.entries.forEach { option ->
                        // "Remove 'Publisher – My Congregation' and 'Nearest
                        // Publisher' from the dropdown list" — always hidden
                        // now, regardless of [canSeePublisherLocations]; the
                        // new "All" entry (below) is how Publishers still
                        // show up on the map.
                        if (option == MapFilterOption.PUBLISHERS || option == MapFilterOption.NEAREST_PUBLISHER) return@forEach
                        DropdownMenuItem(
                            leadingIcon = { Text(option.emoji, style = MaterialTheme.typography.titleMedium) },
                            text = { Text(option.label) },
                            onClick = {
                                filterMenuExpanded = false
                                selectFilter(option)
                            },
                        )
                    }
                }
            }
        }

        // "Add a floating Refresh button" — resets the dropdown back to its
        // unselected/original state and re-fetches location data.
        SmallFloatingActionButton(
            onClick = {
                myLocation = null
                scope.launch {
                    applySelection(null)
                    snackbarHostState.showSnackbar("Territory map updated successfully.")
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 76.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh map")
        }

        // Always available, not just on failure — lets whoever's testing
        // this confirm exactly what's happening (records/points counts,
        // load state, any console error) even when the map *looks* like
        // it's working but markers still aren't showing up right, without
        // needing adb/Logcat access to report it back accurately.
        IconButton(
            onClick = { showDiagnostics = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) {
            Icon(Icons.Rounded.Info, contentDescription = "Map diagnostics", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // "Add a compact Map Legend floating over the map" — bottom-start,
        // clear of Leaflet's own zoom controls (top-left) and the dropdown
        // (top-center) and Refresh/diagnostics (bottom-end), so nothing
        // floating ever overlaps another control.
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(10.dp).widthIn(max = 220.dp)) {
                Row(
                    modifier = Modifier.clickable { legendExpanded = !legendExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Icon(
                        if (legendExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (legendExpanded) "Collapse legend" else "Expand legend",
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (legendExpanded) {
                    // Same icon+category-name pairing used by every marker,
                    // the bottom sheet, and the dropdown — "use the same
                    // marker icons shown on the map."
                    listOf(MapPointKind.ME, MapPointKind.PUBLISHER, MapPointKind.BIBLE_STUDY, MapPointKind.RETURN_VISIT, MapPointKind.SEARCHING).forEach { kind ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text(emojiFor(kind), style = MaterialTheme.typography.bodyMedium)
                            Text(categoryLabelFor(kind), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }

    if (selectedPoint != null) {
        MapPointDetailsSheet(
            point = selectedPoint,
            myLocation = myLocation,
            onDismiss = { selectedPointId = null },
            onOpenInMaps = { openCoordinatesInMaps(context, selectedPoint.lat, selectedPoint.lng, selectedPoint.name) },
        )
    }

    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("Map Diagnostics") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Records retrieved: ${rows.size}", style = MaterialTheme.typography.bodySmall)
                    Text("Valid GPS locations: ${pipelinePoints.size}", style = MaterialTheme.typography.bodySmall)
                    Text("Invalid/missing GPS: $invalidCount", style = MaterialTheme.typography.bodySmall)
                    Text("Publishers sharing location: ${publisherPoints.size}", style = MaterialTheme.typography.bodySmall)
                    Text("Map status: ${loadState.name}", style = MaterialTheme.typography.bodySmall)
                    if (consoleMessages.isEmpty()) {
                        Text("No console messages yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Console log:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        consoleMessages.forEach { line ->
                            Text(line, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) { Text("Close") }
            },
        )
    }
}

/** "Display a compact information card/bottom sheet" — the exact field set
 * and wording from the spec's own three worked examples, built from
 * whichever [MapPoint] was last tapped ([TerritoryLiveMap.selectedPointId]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapPointDetailsSheet(
    point: MapPoint,
    myLocation: LatLng?,
    onDismiss: () -> Unit,
    onOpenInMaps: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            // "Keep the category icon visible" — the same emoji as the
            // marker itself, legend, and dropdown, shown large above the
            // name (spec's own worked examples: "👤 / Juan Dela Cruz").
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(markerColorFor(point.kind)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emojiFor(point.kind), style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text(point.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(categoryLabelFor(point.kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(point.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))

            if (point.kind == MapPointKind.ME) {
                // "You are here" — its own coordinates in place of the
                // Location/Distance rows every other kind shows (a distance
                // from yourself to yourself is meaningless).
                DetailRow(label = "Coordinates", value = point.coords)
            } else {
                DetailRow(
                    label = "Location",
                    value = when {
                        point.kind == MapPointKind.PUBLISHER && point.isCurrentlySharing == true -> "Currently Sharing"
                        point.kind == MapPointKind.PUBLISHER -> "Last Known Location"
                        else -> "Registered Location"
                    },
                )
                if (myLocation != null) {
                    DetailRow(label = "Distance", value = formatDistance(haversineMeters(myLocation.lat, myLocation.lng, point.lat, point.lng)))
                }
                if (point.kind == MapPointKind.PUBLISHER && point.updatedAt != null) {
                    DetailRow(label = "Last Updated", value = formatRelativeTime(point.updatedAt))
                }
            }

            Button(onClick = onOpenInMaps, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Open in Maps")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun markerColorFor(kind: MapPointKind): Color = when (kind) {
    MapPointKind.PUBLISHER -> Color(0xFF1A73E8)
    MapPointKind.BIBLE_STUDY -> Color(0xFF8E24AA)
    MapPointKind.RETURN_VISIT -> Color(0xFFFB8C00)
    MapPointKind.SEARCHING -> Color(0xFF616161)
    MapPointKind.ME -> Color(0xFF34A853)
}

/** "The icon identifies the person's current classification; GPS location
 * does not determine classification" — the one place every one of the five
 * category emoji is defined, reused verbatim by the map markers (via
 * [buildTerritoryMapHtml], which gets the same string embedded into the
 * page), the legend, the bottom sheet, the dropdown, and List View's own
 * rows, so it's structurally impossible for two screens to disagree about
 * which icon means what. */
private fun emojiFor(kind: MapPointKind): String = when (kind) {
    MapPointKind.PUBLISHER -> "👤"
    MapPointKind.BIBLE_STUDY -> "📖"
    MapPointKind.RETURN_VISIT -> "🔄"
    MapPointKind.SEARCHING -> "⭐"
    MapPointKind.ME -> "📍"
}

private fun categoryLabelFor(kind: MapPointKind): String = when (kind) {
    MapPointKind.PUBLISHER -> "Publisher"
    MapPointKind.BIBLE_STUDY -> "Bible Study"
    MapPointKind.RETURN_VISIT -> "Return Visit"
    MapPointKind.SEARCHING -> "Searching Interested Person"
    MapPointKind.ME -> "My Location"
}

private fun PipelineStage.toMapPointKind(): MapPointKind = when (this) {
    PipelineStage.SEARCHING -> MapPointKind.SEARCHING
    PipelineStage.RETURN_VISIT -> MapPointKind.RETURN_VISIT
    PipelineStage.BIBLE_STUDY -> MapPointKind.BIBLE_STUDY
}

/** "Juan Dela Cruz — 350 meters away" — meters under 1km, one decimal of
 * kilometers beyond that. */
private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else "${"%.1f".format(meters / 1000)} km"

/** "Last Updated: Just now" — coarse, human buckets rather than a raw
 * timestamp; matches the spec's own worked example verbatim for the first
 * bucket. */
private fun formatRelativeTime(updatedAtMillis: Long): String {
    val minutes = (System.currentTimeMillis() - updatedAtMillis) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        minutes < 24 * 60 -> "${minutes / 60} hour${if (minutes / 60 == 1L) "" else "s"} ago"
        else -> "${minutes / (24 * 60)} day${if (minutes / (24 * 60) == 1L) "" else "s"} ago"
    }
}

private enum class MapLoadState { LOADING, LOADED, FAILED }

/** What kind of thing a [MapPoint] represents — drives both its marker style
 * ([buildTerritoryMapHtml]) and which dropdown entry controls its visibility.
 * "Publishers, Bible Studies, and Return Visits are separate
 * classifications" — this is the one place that classification is decided,
 * at construction (see [TerritoryLiveMap]'s `pipelinePoints`/`publisherPoints`),
 * never inferred later from "has coordinates." */
private enum class MapPointKind(val jsValue: String) {
    SEARCHING("SEARCHING"),
    RETURN_VISIT("RETURN_VISIT"),
    BIBLE_STUDY("BIBLE_STUDY"),
    PUBLISHER("PUBLISHER"),
    /** "Show details in all categories in territory map even 'My Location'"
     * — the "you are here" dot is otherwise never a member of [points] at
     * all (see [TerritoryLiveMap]'s own "do not treat the logged-in user's
     * location as a Bible Study or Return Visit"); this exists purely so
     * tapping it can open the same bottom sheet every other marker already
     * does, via a synthetic point built from [TerritoryLiveMap.myLocation]
     * (see `myLocationPoint`) rather than a real [TerritoryMapRow]/
     * [TerritoryPublisherRow]. */
    ME("ME"),
}

/** One plottable dot on the Territory Map — a pipeline record ([MapPointKind.SEARCHING]/
 * [MapPointKind.RETURN_VISIT]/[MapPointKind.BIBLE_STUDY]) or a publisher
 * currently sharing their location ([MapPointKind.PUBLISHER]), unified into
 * one shape so [buildTerritoryMapHtml] only has to know one JSON structure.
 * [updatedAt]/[isCurrentlySharing] are only ever non-null for [MapPointKind.PUBLISHER]. */
private data class MapPoint(
    val id: String,
    val kind: MapPointKind,
    val lat: Double,
    val lng: Double,
    val name: String,
    val status: String,
    val location: String,
    val congregation: String,
    val coords: String,
    val updatedAt: Long?,
    val isCurrentlySharing: Boolean?,
)

/** Great-circle distance in meters — plenty accurate for "which of these
 * handful of nearby records is closest," not meant for long-range routing. */
private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

private fun buildTerritoryMapHtml(points: List<MapPoint>): String {
    val pointsJson = points.joinToString(",", prefix = "[", postfix = "]") { p ->
        """{id:"${jsEscape(p.id)}",kind:"${p.kind.jsValue}",lat:${p.lat},lng:${p.lng},name:"${jsEscape(p.name)}"}"""
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.Default.css">
        <style>
          /* height:100% cascading from html->body->#map depends on every ancestor
             resolving to a *definite* pixel height; inside this WebView that chain
             was measuring #map at 0px tall (confirmed on-device: container
             clientWidth/Height=412/0) even though the WebView's own Android View
             had a real, non-zero height. Anchoring #map with all four absolute
             offsets sizes it directly from the viewport instead, which sidesteps
             that percentage-height cascade entirely. */
          html, body { height: 100%; margin: 0; padding: 0; }
          #map { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
          .territory-label { background: rgba(255,255,255,0.9); border: none; box-shadow: 0 1px 3px rgba(0,0,0,0.3); padding: 1px 6px; font-size: 12px; }
          .territory-marker { background: transparent; border: none; }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/leaflet.markercluster.js"></script>
        <script>
        try {
          var points = $pointsJson;
          // Exposed on window (not just a local `var`) so the Android side
          // can reach it via evaluateJavascript for the invalidateSize()
          // fix — see TerritoryLiveMap's own comment on why that's needed.
          var map = L.map('map', { zoomControl: true });
          window.territoryMap = map;
          var tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
          }).addTo(map);
          // A failed *tile image* load is silent by default — no
          // console.error, no page-level onReceivedError (that only fires
          // for the top-level HTML document, not an <img> Leaflet loads
          // dynamically afterward) — which is exactly the "map says LOADED,
          // zero console messages, still blank" combination this is meant
          // to catch: without this listener, a network/DNS/firewall issue
          // reaching *specifically* the tile server (while cdnjs, a
          // completely different host, loaded the Leaflet library itself
          // just fine) would have no visible symptom at all.
          var tileErrorCount = 0;
          tiles.on('tileerror', function(e) {
            tileErrorCount++;
            console.error('Tile load failed (' + tileErrorCount + '): ' + (e.tile && e.tile.src));
          });
          tiles.on('load', function() {
            console.log('Tile layer reported load complete. Errors so far: ' + tileErrorCount);
          });

          // "Assign a unique icon to every map category... do not rely
          // exclusively on icons [alone] — icon + color together, plus the
          // emoji itself carries its own meaning independent of color for
          // anyone who can't distinguish the colors. Selected markers grow
          // and gain a gold ring (§10 "Highlight the selected marker") —
          // same shape/emoji, just visually emphasized, so the category
          // identity never changes just because it's selected.
          function emojiForKind(kind) {
            return kind === 'PUBLISHER' ? '👤' : kind === 'BIBLE_STUDY' ? '📖' : kind === 'RETURN_VISIT' ? '🔄' : kind === 'ME' ? '📍' : '⭐';
          }
          function colorForKind(kind) {
            return kind === 'PUBLISHER' ? '#1a73e8' : kind === 'BIBLE_STUDY' ? '#8e24aa' : kind === 'RETURN_VISIT' ? '#fb8c00' : kind === 'ME' ? '#34a853' : '#616161';
          }
          function buildIcon(kind, selected) {
            var size = selected ? 34 : 26;
            var fontSize = selected ? 16 : 13;
            var border = selected ? '3px solid #ffd600' : '2px solid #ffffff';
            var html = '<div style="width:' + size + 'px;height:' + size + 'px;border-radius:50%;background:' + colorForKind(kind) + ';border:' + border +
              ';box-shadow:0 1px 3px rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center;font-size:' + fontSize + 'px;line-height:1;">' +
              emojiForKind(kind) + '</div>';
            return L.divIcon({ className: 'territory-marker', html: html, iconSize: [size, size], iconAnchor: [size / 2, size / 2] });
          }

          // "Highlight the selected marker" — tracks whichever marker (or
          // 'me') was last tapped/auto-selected and swaps only that one's
          // icon back and forth between its normal and selected style;
          // Kotlin also calls this directly (see the [selectedPointId]
          // LaunchedEffect) so a Nearest-X auto-selection highlights the
          // same way a manual tap does.
          var selectedMarkerId = null;
          window.setSelectedMarker = function(id) {
            if (selectedMarkerId && selectedMarkerId !== id) {
              var prev = selectedMarkerId === 'me' ? window.myLocationMarker : markersById[selectedMarkerId];
              if (prev) prev.setIcon(buildIcon(prev._kind, false));
            }
            selectedMarkerId = id || null;
            if (id) {
              var current = id === 'me' ? window.myLocationMarker : markersById[id];
              if (current) current.setIcon(buildIcon(current._kind, true));
            }
          };

          // Clusters nearby markers into one numbered bubble that expands on
          // tap/zoom — keeps a congregation with many records close together
          // (a housing subdivision, say) from turning into an unreadable
          // pile of overlapping pins. "You are here" (see setMyLocation) is
          // a separate marker outside this cluster entirely — it should
          // always stay visible regardless of the selected filter.
          var cluster = L.markerClusterGroup();
          var markers = [];
          var markersById = {};

          points.forEach(function(p) {
            var marker = L.marker([p.lat, p.lng], { icon: buildIcon(p.kind, false) });
            // "Icon + Text/Category" — the same emoji every legend/bottom-
            // sheet/dropdown entry for this category uses, prefixed onto
            // the permanent name label so identification never depends on
            // the icon alone even before a marker is tapped.
            marker.bindTooltip(emojiForKind(p.kind) + ' ' + p.name, { permanent: true, direction: 'right', offset: [8, 0], className: 'territory-label' });
            // "Display a compact information card/bottom sheet" — a tap
            // hands off to the native side (which already has every other
            // field for this point) rather than building an HTML popup here.
            marker.on('click', function() {
              window.setSelectedMarker(p.id);
              if (window.AndroidBridge) { AndroidBridge.showDetails(p.id); }
            });
            marker._kind = p.kind;
            marker._inCluster = true;
            cluster.addLayer(marker);
            markers.push(marker);
            markersById[p.id] = marker;
          });
          map.addLayer(cluster);

          var lastVisible = markers.slice();
          // Bug fix #4's own consequence: fitBounds()/setView() below compute
          // their zoom level from the container's size *at the instant they
          // run* — if that instant is still before the WebView's real size
          // has reached Chromium (the exact bug bounds-fix #4 works around on
          // the Android side), the very first fit is against a bogus 0-size
          // viewport and locks in a wrong zoom/center (in practice: zoomed
          // all the way out to Null Island) that later invalidateSize() calls
          // don't correct on their own — invalidateSize() only re-measures
          // the container, it doesn't re-fit the view to the markers. So
          // every place that already re-measures the container also re-fits
          // the view to the currently-visible markers, not just
          // invalidateSize() alone.
          function fitToVisible(list) {
            if (list.length === 1) {
              map.setView(list[0].getLatLng(), 16);
            } else if (list.length > 1) {
              map.fitBounds(L.featureGroup(list).getBounds().pad(0.2));
            }
          }
          fitToVisible(lastVisible);

          // "The selected option determines what markers... are displayed" —
          // kindsCsv is the exact comma-separated set of kinds that should
          // stay visible; an empty string hides every pipeline/publisher
          // marker (used by "My Location," which shows only the distinct
          // "you are here" dot). Markers already in/out of the cluster are
          // left alone (Leaflet has no cheap "is this already a member"
          // check of its own, hence the _inCluster flag) so re-applying the
          // same filter twice never double-adds or double-removes a layer.
          window.applyFilter = function(kindsCsv) {
            var kinds = kindsCsv ? kindsCsv.split(',') : [];
            var visible = [];
            markers.forEach(function(m) {
              var shouldShow = kinds.indexOf(m._kind) !== -1;
              if (shouldShow && !m._inCluster) { cluster.addLayer(m); m._inCluster = true; }
              if (!shouldShow && m._inCluster) { cluster.removeLayer(m); m._inCluster = false; }
              if (shouldShow) visible.push(m);
            });
            lastVisible = visible;
            fitToVisible(visible);
          };

          // "Automatically center the map around the user's location and
          // the nearest publisher(s)" — Kotlin already computed which ids
          // are nearest (it has the device's own GPS fix, which this page
          // never sees) and already called applyFilter for the matching
          // kind; this just tightens the camera to fit the user's own
          // position plus those specific nearby markers, rather than every
          // marker of that kind congregation-wide.
          window.focusNearby = function(lat, lng, ids) {
            var group = [L.marker([lat, lng])];
            ids.forEach(function(id) { if (markersById[id]) group.push(markersById[id]); });
            if (group.length === 1) {
              map.setView([lat, lng], 16);
            } else {
              map.fitBounds(L.featureGroup(group).getBounds().pad(0.3));
            }
          };

          // "Add the user current location in the map view" — a distinct
          // green dot, outside the cluster group (always visible regardless
          // of the selected filter), added/updated once Android actually
          // has a GPS fix; never present until then.
          window.setMyLocation = function(lat, lng) {
            if (window.myLocationMarker) { map.removeLayer(window.myLocationMarker); }
            window.myLocationMarker = L.marker([lat, lng], { icon: buildIcon('ME', selectedMarkerId === 'me'), zIndexOffset: 1000 }).addTo(map);
            window.myLocationMarker._kind = 'ME';
            // "Show details in all categories... even 'My Location'" — same
            // AndroidBridge.showDetails('me') hop every other marker's tap
            // already uses; Kotlin builds the matching synthetic point
            // itself (see TerritoryLiveMap's `myLocationPoint`).
            window.myLocationMarker.on('click', function() {
              window.setSelectedMarker('me');
              if (window.AndroidBridge) { AndroidBridge.showDetails('me'); }
            });
          };

          // JS-side fallback for the exact same "container wasn't its final
          // size yet when L.map() ran" issue the Android side's onSizeChanged/
          // onPageFinished hooks already cover — belt-and-suspenders across
          // both layers rather than trusting only one of them to always win
          // the race on every device.
          window.addEventListener('resize', function() { map.invalidateSize(); fitToVisible(lastVisible); });
          setTimeout(function() {
            map.invalidateSize();
            fitToVisible(lastVisible);
            var size = map.getSize();
            var container = document.getElementById('map');
            console.log('Diag: map size=' + size.x + 'x' + size.y + ', container clientWidth/Height=' + container.clientWidth + '/' + container.clientHeight + ', markers=' + markers.length);
          }, 100);
          setTimeout(function() { map.invalidateSize(); fitToVisible(lastVisible); }, 500);
          setTimeout(function() { map.invalidateSize(); fitToVisible(lastVisible); }, 1500);
        } catch (e) {
          console.error('Territory map script threw: ' + e.message);
        }
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun jsEscape(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
