package com.emfitsolutions.gopreach.ui.screens.territories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
 * row as a labeled pin on one scatter-plot "map" instead — see
 * [TerritoryScatterMap]'s own doc comment for why that's drawn directly in
 * Compose rather than an embedded WebView/tile map or Google Maps Compose
 * (no bundled Maps API key anywhere in this app, and a WebView-hosted map
 * depends on a CDN/tile server actually being reachable, which turned out
 * not to reliably render at all on some devices/networks).
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
                TerritoryScatterMap(rows = filtered, modifier = Modifier.weight(1f).fillMaxWidth())
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
 * "Map View" — every row plotted as a labeled pin (Name always visible;
 * Status/Location on tap) over a plain grid, positioned by projecting each
 * record's own lat/lng into the box's own pixel space. Deliberately drawn
 * directly in Compose rather than an embedded WebView/tile map: a tile-based
 * map (Leaflet/OSM, Google Maps Compose, anything with real cartography)
 * depends on a CDN and a tile server actually being reachable over the
 * network *and* the device's WebView component behaving — that turned out
 * not to reliably render at all on some devices/networks ("I cannot see
 * anything in map view"), and this app has never bundled a Google Maps API
 * key to begin with (every other screen that touches a map hands routing
 * off to the device's own Maps app instead — see [com.emfitsolutions
 * .gopreach.ui.screens.findlocation.FindLocationScreen]'s own doc comment).
 * A pure-Compose scatter plot has no network dependency at all, so it
 * always renders; tapping a pin still offers "Open in Maps" for real
 * turn-by-turn/satellite imagery via the device's own Maps app.
 */
@Composable
private fun TerritoryScatterMap(rows: List<TerritoryMapRow>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val points = remember(rows) {
        rows.mapNotNull { row ->
            val lat = row.person.gpsLat
            val lng = row.person.gpsLng
            if (lat != null && lng != null) Triple(row, lat, lng) else null
        }
    }
    var selected by remember { mutableStateOf<TerritoryMapRow?>(null) }

    if (points.isEmpty()) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(
                "None of the matching records have a saved GPS coordinate yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    // The bounding box of every point, padded 20% on each side so a pin
    // never sits flush against the edge — and widened to a minimum span so
    // a single point (or several nearly on top of each other) doesn't
    // divide by (near) zero.
    val minLat = points.minOf { it.second }
    val maxLat = points.maxOf { it.second }
    val minLng = points.minOf { it.third }
    val maxLng = points.maxOf { it.third }
    val latSpan = (maxLat - minLat).coerceAtLeast(0.01)
    val lngSpan = (maxLng - minLng).coerceAtLeast(0.01)
    val latCenter = (minLat + maxLat) / 2.0
    val lngCenter = (minLng + maxLng) / 2.0
    val paddedLatSpan = latSpan * 1.4
    val paddedLngSpan = lngSpan * 1.4
    val latLow = latCenter - paddedLatSpan / 2.0
    val latHigh = latCenter + paddedLatSpan / 2.0
    val lngLow = lngCenter - paddedLngSpan / 2.0
    val lngHigh = lngCenter + paddedLngSpan / 2.0

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFFE8EEE4))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        points.forEach { (row, lat, lng) ->
            val xFrac = ((lng - lngLow) / (lngHigh - lngLow)).coerceIn(0.0, 1.0)
            val yFrac = (1.0 - (lat - latLow) / (latHigh - latLow)).coerceIn(0.0, 1.0)
            val xDp = with(density) { (xFrac * widthPx).toFloat().toDp() }
            val yDp = with(density) { (yFrac * heightPx).toFloat().toDp() }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(x = xDp - 12.dp, y = yDp - 28.dp)
                    .clickable { selected = row },
            ) {
                Icon(
                    Icons.Rounded.LocationOn,
                    contentDescription = row.person.name,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    row.person.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp),
                )
            }
        }
    }

    val toShow = selected
    if (toShow != null) {
        val lat = toShow.person.gpsLat
        val lng = toShow.person.gpsLng
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(toShow.person.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Status: ${toShow.person.pipelineStage.statusLabel()}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Location: ${toShow.resolvedLocation ?: (if (lat != null && lng != null) formatCoordinatesDms(lat, lng) else "—")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Congregation: ${toShow.congregationName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                if (lat != null && lng != null) {
                    TextButton(onClick = { openCoordinatesInMaps(context, lat, lng, toShow.person.name); selected = null }) { Text("Open in Maps") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}
