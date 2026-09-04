package com.emfitsolutions.gopreach.ui.screens.territories

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.emfitsolutions.gopreach.data.location.formatCoordinatesDms
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.ui.components.isValidLatitude
import com.emfitsolutions.gopreach.ui.components.isValidLongitude
import com.emfitsolutions.gopreach.ui.components.openCoordinatesInMaps

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
    onBack: () -> Unit,
    viewModel: TerritoryMapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
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
                TerritoryLiveMap(rows = filtered, modifier = Modifier.weight(1f).fillMaxWidth())
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
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerritoryLiveMap(rows: List<TerritoryMapRow>, modifier: Modifier = Modifier) {
    // STEP 4 — validate every coordinate before it ever reaches the map:
    // not null, numeric (guaranteed by the Double type itself, but NaN/
    // Infinite still slip through arithmetic and aren't valid geographic
    // points), and within real lat/lng range. A record that fails this
    // never reaches Leaflet at all — it's counted separately instead, so
    // one bad row can't take the whole map down.
    val points = remember(rows) {
        rows.mapNotNull { row ->
            val lat = row.person.gpsLat
            val lng = row.person.gpsLng
            if (lat != null && lng != null && lat.isFinite() && lng.isFinite() && isValidLatitude(lat) && isValidLongitude(lng)) {
                Triple(row, lat, lng)
            } else {
                null
            }
        }
    }
    val invalidCount = rows.size - points.size

    // STEP 8 — diagnostics: same information a `debug` panel would show,
    // just in Logcat rather than on-screen (this app's other diagnostic UI —
    // SyncStatusButton, etc. — is text/user-facing, not a raw debug dump).
    LaunchedEffect(rows) {
        Log.d(TAG, "Records retrieved: ${rows.size}; valid GPS: ${points.size}; invalid/missing GPS: $invalidCount")
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

    // Bug fix ("I cannot see any actual map"): loading used to happen
    // directly inside AndroidView's `update` lambda, which Compose re-runs
    // on *every* recomposition of this composable — and writing `loadState`
    // there is itself read by the loading/error overlay below, so each
    // write triggered another recomposition, which re-ran `update`, which
    // reloaded the page again, forever. The map never got a chance to
    // finish loading — visually indistinguishable from "nothing renders."
    // A `LaunchedEffect` keyed on the content that should actually trigger
    // a (re)load — not on every recomposition — fixes that: it only re-runs
    // when [html], [reloadToken], or the WebView instance itself changes.
    LaunchedEffect(html, reloadToken, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        loadState = MapLoadState.LOADING
        // A stable https base URL (rather than null/about:blank) so the CDN
        // script/tile requests aren't treated as mixed content.
        webView.loadDataWithBaseURL("https://gopreach.app/", html, "text/html", "UTF-8", null)
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
                    Text("Valid GPS locations: ${points.size}", style = MaterialTheme.typography.bodySmall)
                    Text("Invalid/missing GPS: $invalidCount", style = MaterialTheme.typography.bodySmall)
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

private fun buildTerritoryMapHtml(points: List<Triple<TerritoryMapRow, Double, Double>>): String {
    val pointsJson = points.joinToString(",", prefix = "[", postfix = "]") { (row, lat, lng) ->
        val location = row.resolvedLocation ?: formatCoordinatesDms(lat, lng)
        val coords = formatCoordinatesDms(lat, lng)
        """{lat:$lat,lng:$lng,name:"${jsEscape(row.person.name)}",status:"${jsEscape(row.person.pipelineStage.statusLabel())}",location:"${jsEscape(location)}",coords:"${jsEscape(coords)}",congregation:"${jsEscape(row.congregationName)}"}"""
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
          html, body, #map { height: 100%; margin: 0; padding: 0; }
          .territory-label { background: rgba(255,255,255,0.9); border: none; box-shadow: 0 1px 3px rgba(0,0,0,0.3); padding: 1px 6px; font-size: 12px; }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/leaflet.markercluster.js"></script>
        <script>
          var points = $pointsJson;
          // Exposed on window (not just a local `var`) so the Android side
          // can reach it via evaluateJavascript for the invalidateSize()
          // fix — see TerritoryLiveMap's own comment on why that's needed.
          var map = L.map('map', { zoomControl: true });
          window.territoryMap = map;
          L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
          }).addTo(map);
          // Clusters nearby markers into one numbered bubble that expands on
          // tap/zoom — keeps a congregation with many records close together
          // (a housing subdivision, say) from turning into an unreadable
          // pile of overlapping pins.
          var cluster = L.markerClusterGroup();
          var markers = [];
          points.forEach(function(p) {
            var marker = L.marker([p.lat, p.lng]);
            marker.bindTooltip(p.name, { permanent: true, direction: 'right', offset: [8, 0], className: 'territory-label' });
            marker.bindPopup(
              '<b>' + p.name + '</b><br>' +
              'Status: ' + p.status + '<br>' +
              'Location: ' + p.location + '<br>' +
              'Congregation: ' + p.congregation + '<br>' +
              'Coordinates: ' + p.coords
            );
            cluster.addLayer(marker);
            markers.push(marker);
          });
          map.addLayer(cluster);
          if (points.length === 1) {
            map.setView([points[0].lat, points[0].lng], 16);
          } else {
            var group = L.featureGroup(markers);
            map.fitBounds(group.getBounds().pad(0.2));
          }
          // JS-side fallback for the exact same "container wasn't its final
          // size yet when L.map() ran" issue the Android side's onSizeChanged/
          // onPageFinished hooks already cover — belt-and-suspenders across
          // both layers rather than trusting only one of them to always win
          // the race on every device.
          window.addEventListener('resize', function() { map.invalidateSize(); });
          setTimeout(function() { map.invalidateSize(); }, 100);
          setTimeout(function() { map.invalidateSize(); }, 500);
          setTimeout(function() { map.invalidateSize(); }, 1500);
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun jsEscape(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
