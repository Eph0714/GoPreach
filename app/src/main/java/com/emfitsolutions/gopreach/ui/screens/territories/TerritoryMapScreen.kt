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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "TerritoryMap"

private enum class TerritoryViewMode(val label: String) { LIST("List View"), MAP("Map View") }

/**
 * "Territory Module" — no longer a Territory Master File CRUD (Add/Edit/
 * Delete a Territory entity); a read-only directory of every Searching/
 * Return Visit/Bible Study record that has a saved GPS location, searchable
 * by name or location, e.g. "Name: Richard Ortega. Status: Bible Study.
 * Location: F5M2+57Q, 1, Bayombong, Nueva Vizcaya." List View's rows tap
 * out to Google Maps (or whatever the device offers for a `geo:` URI), same
 * as every other saved coordinate in this app; Map View plots every visible
 * row as a labeled, pinch-zoomable pin on one real embedded map instead —
 * see [TerritoryLiveMap]'s own doc comment for why that's a WebView running
 * Leaflet over OpenStreetMap tiles rather than Google Maps Compose (no
 * bundled Google Maps API key anywhere in this app).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryMapScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    canSeePublisherLocations: Boolean,
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
    var viewMode by remember { mutableStateOf(TerritoryViewMode.LIST) }

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
            )
        },
    ) { padding ->
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

            // "The Territory Map has an option. (List View) or (Map View)."
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TerritoryViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TerritoryViewMode.entries.size),
                        icon = { Icon(if (mode == TerritoryViewMode.LIST) Icons.Rounded.ViewList else Icons.Rounded.Map, contentDescription = null) },
                    ) { Text(mode.label) }
                }
            }

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
            } else if (viewMode == TerritoryViewMode.MAP) {
                TerritoryLiveMap(
                    rows = filtered,
                    publisherRows = publisherRows,
                    canSeePublisherLocations = canSeePublisherLocations,
                    isAllCongregations = fixedCongregationId == null,
                    getCurrentLocation = viewModel::currentLocation,
                    hasLocationPermission = viewModel::hasLocationPermission,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
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
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

private fun PipelineStage.statusLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

/**
 * "Map View" — a real, pinch-zoomable embedded map (spec: "show the real
 * map... allow the user to zoom in and out"), every visible row plotted as
 * a pin with its name shown as a permanent label beside it (tap the pin for
 * Status/Location too). A `WebView` running Leaflet over OpenStreetMap
 * tiles, not Google Maps Compose — this app has never bundled a Google Maps
 * API key (every other screen that touches a map hands routing off to the
 * device's own Maps app instead — see [com.emfitsolutions.gopreach.ui
 * .screens.findlocation.FindLocationScreen]'s own doc comment) — and
 * Leaflet+OSM needs no key or new Gradle dependency at all, just the CDN
 * scripts loaded inside the page HTML this generates. Leaflet's own touch
 * handling gives pinch-to-zoom, double-tap-to-zoom, and its usual +/-
 * on-screen buttons for free once the page actually loads; [webViewClient]
 * surfaces a "Map failed to load" retry state instead of a silent blank
 * screen if the tiles/scripts genuinely can't be reached (e.g. no
 * connection), rather than leaving the Publisher staring at nothing with no
 * explanation.
 */
@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerritoryLiveMap(
    rows: List<TerritoryMapRow>,
    publisherRows: List<TerritoryPublisherRow>,
    canSeePublisherLocations: Boolean,
    isAllCongregations: Boolean,
    getCurrentLocation: suspend () -> LatLng?,
    hasLocationPermission: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    kind = when (row.person.pipelineStage) {
                        PipelineStage.SEARCHING -> MapPointKind.SEARCHING
                        PipelineStage.RETURN_VISIT -> MapPointKind.RETURN_VISIT
                        PipelineStage.BIBLE_STUDY -> MapPointKind.BIBLE_STUDY
                    },
                    lat = lat,
                    lng = lng,
                    name = row.person.name,
                    status = row.person.pipelineStage.statusLabel(),
                    location = row.resolvedLocation ?: formatCoordinatesDms(lat, lng),
                    congregation = row.congregationName,
                    coords = formatCoordinatesDms(lat, lng),
                )
            } else {
                null
            }
        }
    }
    // "For publisher account they can see other publishers that share their
    // location in the map" — a distinct marker style ([buildTerritoryMapHtml]),
    // hidden by default (same "original state" the Refresh button restores)
    // until the "Publishers..." filter chip below is switched on.
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

    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                if (invalidCount > 0) {
                    "Some records do not have valid GPS locations and cannot yet be displayed on the map."
                } else {
                    "None of the matching records have a saved GPS coordinate yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    // "Add a filter showing Map View (Bible Study, Return Visit, Publishers
    // in my congregation...)" — empty [selectedStages] is the default/
    // "original state" (every pipeline stage shown, same as before this
    // feature existed); selecting one or more narrows the map to just those.
    // [showPublishers] is its own independent toggle, off by default even
    // when [canSeePublisherLocations] is true — showing every other
    // publisher's live position is opt-in, not automatic.
    var selectedStages by remember { mutableStateOf(setOf<MapPointKind>()) }
    var showPublishers by remember { mutableStateOf(false) }
    val visibleKinds = remember(selectedStages, showPublishers) {
        val stages = selectedStages.ifEmpty { setOf(MapPointKind.SEARCHING, MapPointKind.RETURN_VISIT, MapPointKind.BIBLE_STUDY) }
        if (showPublishers) stages + MapPointKind.PUBLISHER else stages
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

    // "Add the user current location in the map view" — fetched on demand
    // (the "My Location" chip, or automatically the first time a "Show
    // Nearest..." action needs it), not on every screen open, so this never
    // surprises anyone with a permission prompt they didn't ask for.
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingPermissionAction by remember { mutableStateOf<PendingLocationAction?>(null) }

    // Pushes a fresh GPS fix into the page as a distinct "You are here"
    // marker ([buildTerritoryMapHtml]'s `window.setMyLocation`) — shared by
    // the "My Location" chip and every "Show Nearest..." action below, so a
    // fix obtained once during this screen's lifetime never needs asking
    // twice.
    suspend fun ensureMyLocation(): LatLng? {
        myLocation?.let { return it }
        val fix = getCurrentLocation() ?: return null
        myLocation = fix
        webViewRef?.evaluateJavascript("if (window.setMyLocation) { window.setMyLocation(${fix.lat}, ${fix.lng}); }", null)
        return fix
    }

    // "Show Nearest Publisher"/"Show Nearest Bible Study"/"Show Nearest
    // Return Visit" — narrows the filter to just that one kind (so the map
    // reads as "here's the nearest Bible Study," not "here's a Bible Study
    // buried among everything else"), then pans/zooms to and opens whichever
    // of [points] of that kind is closest to [myLocation] (haversine — small
    // distances, no need for anything fancier). Silently does nothing if a
    // fix genuinely can't be obtained (no signal, permission denied) or
    // there's no record of that kind to find — same "fail quiet, not
    // crash" convention as [openCoordinatesInMaps].
    suspend fun runPendingLocationAction(action: PendingLocationAction) {
        val fix = ensureMyLocation() ?: return
        when (action) {
            is PendingLocationAction.MyLocation -> {
                webViewRef?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.setView([${fix.lat}, ${fix.lng}], 16); }", null)
            }
            is PendingLocationAction.Nearest -> {
                when (action.kind) {
                    MapPointKind.PUBLISHER -> { showPublishers = true; selectedStages = emptySet() }
                    else -> { selectedStages = setOf(action.kind); showPublishers = false }
                }
                val nearest = points
                    .filter { it.kind == action.kind }
                    .minByOrNull { haversineMeters(fix.lat, fix.lng, it.lat, it.lng) }
                    ?: return
                webViewRef?.evaluateJavascript("if (window.focusPoint) { window.focusPoint('${jsEscape(nearest.id)}'); }", null)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted && action != null) {
            scope.launch { runPendingLocationAction(action) }
        }
    }

    fun requestLocationThen(action: PendingLocationAction) {
        if (hasLocationPermission()) {
            scope.launch { runPendingLocationAction(action) }
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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

    // Pushes the filter chips' current selection into the already-loaded
    // page (window.applyFilter — see buildTerritoryMapHtml) rather than
    // regenerating/reloading the whole WebView per toggle, which would
    // needlessly re-run every load-timing fix above for a plain filter tap.
    LaunchedEffect(visibleKinds, webViewRef, loadState) {
        if (loadState == MapLoadState.LOADED) {
            val csv = visibleKinds.joinToString(",") { it.jsValue }
            webViewRef?.evaluateJavascript("if (window.applyFilter) { window.applyFilter('$csv'); }", null)
        }
    }

    Column(modifier = modifier) {
        // "Add a filter showing Map View (Bible Study, Return Visit,
        // Publishers in my congregation (All Congregation for super admin),
        // show Nearest Publisher, show nearest Bible Study, show nearest
        // return Visit). Add a refresh map button." — bug fix ("I cannot see
        // other buttons in map view"): this used to be a *horizontally
        // scrollable* LazyRow, which technically had every chip reachable
        // but with zero visual signal beyond a sliver of the next chip
        // peeking off the right edge — easy to swipe right past without
        // ever noticing there was more. A wrapping FlowRow instead lays out
        // every chip that fits per line and wraps the rest onto additional
        // lines, so all of them are always on-screen at once, nothing
        // hidden behind a scroll a Publisher might never think to try.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { requestLocationThen(PendingLocationAction.MyLocation) },
                label = { Text("My Location") },
                leadingIcon = { Icon(Icons.Rounded.MyLocation, contentDescription = null) },
            )
            FilterChip(
                selected = selectedStages.contains(MapPointKind.BIBLE_STUDY),
                onClick = {
                    selectedStages = if (selectedStages.contains(MapPointKind.BIBLE_STUDY)) {
                        selectedStages - MapPointKind.BIBLE_STUDY
                    } else {
                        selectedStages + MapPointKind.BIBLE_STUDY
                    }
                },
                label = { Text("Bible Study") },
            )
            FilterChip(
                selected = selectedStages.contains(MapPointKind.RETURN_VISIT),
                onClick = {
                    selectedStages = if (selectedStages.contains(MapPointKind.RETURN_VISIT)) {
                        selectedStages - MapPointKind.RETURN_VISIT
                    } else {
                        selectedStages + MapPointKind.RETURN_VISIT
                    }
                },
                label = { Text("Return Visit") },
            )
            if (canSeePublisherLocations) {
                FilterChip(
                    selected = showPublishers,
                    onClick = { showPublishers = !showPublishers },
                    label = { Text(if (isAllCongregations) "Publishers (All Congregations)" else "Publishers in My Congregation") },
                )
                AssistChip(
                    onClick = { requestLocationThen(PendingLocationAction.Nearest(MapPointKind.PUBLISHER)) },
                    label = { Text("Nearest Publisher") },
                    leadingIcon = { Icon(Icons.Rounded.NearMe, contentDescription = null) },
                )
            }
            AssistChip(
                onClick = { requestLocationThen(PendingLocationAction.Nearest(MapPointKind.BIBLE_STUDY)) },
                label = { Text("Nearest Bible Study") },
                leadingIcon = { Icon(Icons.Rounded.NearMe, contentDescription = null) },
            )
            AssistChip(
                onClick = { requestLocationThen(PendingLocationAction.Nearest(MapPointKind.RETURN_VISIT)) },
                label = { Text("Nearest Return Visit") },
                leadingIcon = { Icon(Icons.Rounded.NearMe, contentDescription = null) },
            )
            // "It will return to original state" — every pipeline stage
            // shown, publishers hidden, whatever the last "Show Nearest…"
            // narrowed the view to cleared. Re-fitting the camera happens
            // for free: the [visibleKinds] LaunchedEffect above re-runs the
            // instant these two reset, which calls window.applyFilter
            // again, which always re-fits bounds to whatever's visible.
            AssistChip(
                onClick = {
                    selectedStages = emptySet()
                    showPublishers = false
                },
                label = { Text("Refresh") },
                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            )
        }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    // "Allow the user to click the coordinates in the maps
                    // view and then show the location" — the same
                    // openCoordinatesInMaps() every other screen's saved
                    // coordinates already use (opens Google Maps, or
                    // whatever the device offers for a `geo:` URI), exposed
                    // to the popup's JS via a thin bridge — content loaded
                    // here is always this screen's own generated HTML, never
                    // arbitrary/remote content, so exposing one narrow method
                    // isn't the security risk addJavascriptInterface usually is.
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun openInMaps(lat: Double, lng: Double, name: String) {
                                openCoordinatesInMaps(ctx, lat, lng, name)
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
        } else if (invalidCount > 0) {
            // Some records mapped fine; a note (not an error — the map is
            // working) that the rest are sitting out for now.
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
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
    }
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

private enum class MapLoadState { LOADING, LOADED, FAILED }

/** What kind of thing a [MapPoint] represents — drives both its marker style
 * ([buildTerritoryMapHtml]) and which filter chip controls its visibility. */
private enum class MapPointKind(val jsValue: String) {
    SEARCHING("SEARCHING"),
    RETURN_VISIT("RETURN_VISIT"),
    BIBLE_STUDY("BIBLE_STUDY"),
    PUBLISHER("PUBLISHER"),
}

/** One plottable dot on the Territory Map — a pipeline record ([MapPointKind.SEARCHING]/
 * [MapPointKind.RETURN_VISIT]/[MapPointKind.BIBLE_STUDY]) or a publisher
 * currently sharing their location ([MapPointKind.PUBLISHER]), unified into
 * one shape so [buildTerritoryMapHtml] only has to know one JSON structure. */
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
)

/** What to do once a GPS fix is actually in hand — requested via the
 * permission flow if needed, then run through [TerritoryLiveMap]'s
 * `runPendingLocationAction`. */
private sealed class PendingLocationAction {
    /** "My Location" chip — just center the map on the fix, no filter change. */
    data object MyLocation : PendingLocationAction()

    /** "Show Nearest Publisher/Bible Study/Return Visit" — see that
     * function's own doc comment. */
    data class Nearest(val kind: MapPointKind) : PendingLocationAction()
}

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
        """{id:"${jsEscape(p.id)}",kind:"${p.kind.jsValue}",lat:${p.lat},lng:${p.lng},name:"${jsEscape(p.name)}",status:"${jsEscape(p.status)}",location:"${jsEscape(p.location)}",coords:"${jsEscape(p.coords)}",congregation:"${jsEscape(p.congregation)}"}"""
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
          // Clusters nearby markers into one numbered bubble that expands on
          // tap/zoom — keeps a congregation with many records close together
          // (a housing subdivision, say) from turning into an unreadable
          // pile of overlapping pins. Publisher markers (kind PUBLISHER) get
          // their own green dot style rather than the default pin, so a
          // shared live position never reads as just another pipeline
          // record; "you are here" (see setMyLocation below) is a third,
          // distinct style, and never joins the cluster at all — it should
          // always stay visible regardless of the filter chips.
          var cluster = L.markerClusterGroup();
          var markers = [];
          var markersById = {};

          function buildPopup(p) {
            var popupEl = document.createElement('div');
            popupEl.innerHTML =
              '<b>' + p.name + '</b><br>' +
              'Status: ' + p.status + '<br>' +
              (p.location && p.location !== '—' ? 'Location: ' + p.location + '<br>' : '') +
              'Congregation: ' + p.congregation + '<br>';
            // Popup content built as a real DOM element (not an HTML string)
            // so the coordinates line can carry a genuine click listener with
            // p's actual lat/lng/name captured safely by closure — no string-
            // escaping footguns from splicing a name with a quote/apostrophe
            // into an inline onclick="..." attribute.
            var coordsLink = document.createElement('a');
            coordsLink.href = 'javascript:void(0)';
            coordsLink.textContent = 'Coordinates: ' + p.coords;
            coordsLink.style.color = '#1a73e8';
            coordsLink.style.textDecoration = 'underline';
            // "Click the coordinates, then show the location" — same
            // openCoordinatesInMaps() every other saved-coordinate spot in
            // the app already uses, via the AndroidBridge exposed on this
            // WebView (see TerritoryLiveMap's factory).
            coordsLink.addEventListener('click', function() {
              if (window.AndroidBridge) {
                AndroidBridge.openInMaps(p.lat, p.lng, p.name);
              }
            });
            popupEl.appendChild(coordsLink);
            return popupEl;
          }

          points.forEach(function(p) {
            var marker = (p.kind === 'PUBLISHER')
              ? L.circleMarker([p.lat, p.lng], { radius: 9, fillColor: '#34a853', color: '#ffffff', weight: 2, fillOpacity: 0.9 })
              : L.marker([p.lat, p.lng]);
            marker.bindTooltip(p.name, { permanent: true, direction: 'right', offset: [8, 0], className: 'territory-label' });
            marker.bindPopup(buildPopup(p));
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

          // "Add a filter showing Map View (Bible Study, Return Visit,
          // Publishers...)" — kindsCsv is the exact comma-separated set of
          // kinds that should stay visible; Kotlin always sends the full
          // default set explicitly for "show everything" (the Refresh
          // button's "original state"), never relying on an empty string
          // meaning "all." Markers already in/out of the cluster are left
          // alone (Leaflet has no cheap "is this already a member" check of
          // its own, hence the _inCluster flag) so toggling a filter twice
          // in a row never double-adds or double-removes a layer.
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

          // "Show Nearest Publisher/Bible Study/Return Visit" — Kotlin
          // already narrowed the filter to the matching kind and picked the
          // nearest record itself (it has the device's own GPS fix, which
          // this page never sees); this just pans/zooms to that one marker
          // and opens its popup once the pan/zoom animation has settled.
          window.focusPoint = function(id) {
            var m = markersById[id];
            if (!m) return;
            if (!m._inCluster) { cluster.addLayer(m); m._inCluster = true; }
            map.setView(m.getLatLng(), 17);
            setTimeout(function() { m.openPopup(); }, 350);
          };

          // "Add the user current location in the map view" — a distinct
          // blue dot, outside the cluster group (always visible regardless
          // of the filter above), added/updated once Android actually has a
          // GPS fix; never present until then.
          window.setMyLocation = function(lat, lng) {
            if (window.myLocationMarker) { map.removeLayer(window.myLocationMarker); }
            window.myLocationMarker = L.circleMarker([lat, lng], {
              radius: 8, fillColor: '#4285F4', color: '#ffffff', weight: 3, fillOpacity: 1
            }).addTo(map);
            window.myLocationMarker.bindPopup('<b>You are here</b>');
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
