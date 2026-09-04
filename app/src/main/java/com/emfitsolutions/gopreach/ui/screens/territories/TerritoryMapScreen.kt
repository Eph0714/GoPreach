package com.emfitsolutions.gopreach.ui.screens.territories

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Card
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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.location.formatCoordinatesDms
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.ui.components.openCoordinatesInMaps

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
    val points = remember(rows) {
        rows.mapNotNull { row ->
            val lat = row.person.gpsLat
            val lng = row.person.gpsLng
            if (lat != null && lng != null) Triple(row, lat, lng) else null
        }
    }

    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "None of the matching records have a saved GPS coordinate yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val html = remember(points) { buildTerritoryMapHtml(points) }
    // Bumped to force the AndroidView's `update` to actually reload the
    // WebView on a manual retry — reassigning the same [html] string alone
    // wouldn't trigger a reload since `update` only re-runs when one of its
    // own read values changes.
    var reloadToken by remember { mutableIntStateOf(0) }
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
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
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            // Only the top-level page failing counts as the
                            // map itself failing — a single missed sub-
                            // resource (one map tile timing out, say)
                            // shouldn't flip the whole view into an error
                            // state when the map is otherwise usable.
                            if (request?.isForMainFrame == true) loadState = MapLoadState.FAILED
                        }
                    }
                }
            },
            update = { webView ->
                loadState = MapLoadState.LOADING
                // A stable https base URL (rather than null/about:blank) so
                // the CDN script/tile requests aren't treated as mixed
                // content.
                @Suppress("UNUSED_EXPRESSION") reloadToken
                webView.loadDataWithBaseURL("https://gopreach.app/", html, "text/html", "UTF-8", null)
            },
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
                    "Map failed to load. Check your internet connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { reloadToken++ }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Retry")
                }
            }
        }
    }
}

private enum class MapLoadState { LOADING, LOADED, FAILED }

private fun buildTerritoryMapHtml(points: List<Triple<TerritoryMapRow, Double, Double>>): String {
    val pointsJson = points.joinToString(",", prefix = "[", postfix = "]") { (row, lat, lng) ->
        val location = row.resolvedLocation ?: formatCoordinatesDms(lat, lng)
        """{lat:$lat,lng:$lng,name:"${jsEscape(row.person.name)}",status:"${jsEscape(row.person.pipelineStage.statusLabel())}",location:"${jsEscape(location)}"}"""
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css">
        <style>
          html, body, #map { height: 100%; margin: 0; padding: 0; }
          .territory-label { background: rgba(255,255,255,0.9); border: none; box-shadow: 0 1px 3px rgba(0,0,0,0.3); padding: 1px 6px; font-size: 12px; }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
        <script>
          var points = $pointsJson;
          var map = L.map('map', { zoomControl: true });
          L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
          }).addTo(map);
          var markers = [];
          points.forEach(function(p) {
            var marker = L.marker([p.lat, p.lng]).addTo(map);
            marker.bindTooltip(p.name, { permanent: true, direction: 'right', offset: [8, 0], className: 'territory-label' });
            marker.bindPopup('<b>' + p.name + '</b><br>Status: ' + p.status + '<br>Location: ' + p.location);
            markers.push(marker);
          });
          if (points.length === 1) {
            map.setView([points[0].lat, points[0].lng], 16);
          } else {
            var group = L.featureGroup(markers);
            map.fitBounds(group.getBounds().pad(0.2));
          }
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun jsEscape(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
