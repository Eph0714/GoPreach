package com.emfitsolutions.gopreach.ui.screens.sharelocation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.data.location.formatCoordinatesDms
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.ui.components.isValidLatitude
import com.emfitsolutions.gopreach.ui.components.isValidLongitude
import com.emfitsolutions.gopreach.ui.components.map.LeafletMapView
import com.emfitsolutions.gopreach.ui.components.map.MapLoadState
import com.emfitsolutions.gopreach.ui.components.map.rememberLeafletMapController

/** "Publisher Type Filter" — the exact four dropdown entries the spec asks
 * for, no more (deliberately not every [PublisherCategory] — Irregular/
 * Inactive/Reproof/Removed aren't in the spec's own list). */
private enum class PublisherTypeFilter(val label: String, val category: PublisherCategory?) {
    ALL("All Publisher", null),
    REGULAR_PIONEER("Regular Pioneer", PublisherCategory.REGULAR_PIONEER),
    AUXILIARY_PIONEER("Auxiliary Pioneer", PublisherCategory.AUXILIARY_PIONEER),
    UNBAPTIZED_PUBLISHER("Unbaptized Publisher", PublisherCategory.UNBAPTIZED_PUBLISHER),
}

private data class SharePoint(
    val id: String,
    val row: SharedLocationRow,
    val lat: Double,
    val lng: Double,
)

/**
 * "Shared Location Module — List View and Map View... use the same working
 * map implementation and functionality as the Territory Map Module" — built
 * on [LeafletMapView], the exact same reusable WebView wrapper Territory Map
 * itself is built on (see that composable's own doc comment for every
 * container-sizing/lifecycle/duplicate-init fix that implies), so this map
 * can't go blank for a different reason than Territory Map's already-solved
 * one. [rows] is the *same* list List View shows (own-scoped/searched
 * already, one shared source of truth for both views); this composable only
 * adds the Publisher-type dropdown filter on top of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLocationMapView(
    rows: List<SharedLocationRow>,
    onOpenTerritoryMap: (lat: Double, lng: Double, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // STEP — validate every coordinate before it ever reaches the map, same
    // as Territory Map: not null (SharedLocation.lat/lng are non-nullable
    // Doubles here, but NaN/Infinite still slip through arithmetic), and
    // within real lat/lng range.
    val allPoints = remember(rows) {
        rows.mapNotNull { row ->
            val lat = row.location.lat
            val lng = row.location.lng
            if (lat.isFinite() && lng.isFinite() && isValidLatitude(lat) && isValidLongitude(lng)) {
                SharePoint(id = row.person.id, row = row, lat = lat, lng = lng)
            } else {
                null
            }
        }
    }
    val invalidCount = rows.size - allPoints.size

    var selectedFilter by remember { mutableStateOf(PublisherTypeFilter.ALL) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val filteredPoints = remember(allPoints, selectedFilter) {
        val category = selectedFilter.category
        if (category == null) allPoints else allPoints.filter { it.row.category == category }
    }

    var selectedRowId by remember { mutableStateOf<String?>(null) }
    val selectedRow = remember(selectedRowId, allPoints) { allPoints.firstOrNull { it.id == selectedRowId }?.row }

    val html = remember(allPoints) { buildShareLocationMapHtml(allPoints) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }
    val consoleMessages = remember { mutableStateListOf<String>() }
    val controller = rememberLeafletMapController()

    // Filter Behavior spec: "When the user changes the dropdown selection:
    // filter immediately, update markers dynamically without a manual page
    // reload, remove non-matching markers, refresh while keeping the map
    // functional." Same id-list-based `applyFilter` pattern as Territory
    // Map's own kind-based one — no full HTML reload on a filter change,
    // only ever on [allPoints] itself changing (new/updated shared
    // locations arriving live).
    LaunchedEffect(loadState, filteredPoints) {
        if (loadState == MapLoadState.LOADED) {
            val idsCsv = filteredPoints.joinToString(",") { jsEscapeShare(it.id) }
            controller.evaluateJavascript("if (window.applyFilter) { window.applyFilter('$idsCsv'); }")
        }
    }

    // "System message for actions" — mirrors Territory Map's own "no
    // locations found for the selected category" Snackbar, fired once per
    // filter change rather than persistently (the inline empty-state text
    // below already covers "still empty" for as long as it stays that way).
    LaunchedEffect(selectedFilter) {
        if (loadState == MapLoadState.LOADED && filteredPoints.isEmpty() && allPoints.isNotEmpty()) {
            snackbarHostState.showSnackbar("No Publisher matches the selected filter.")
        }
    }

    Box(modifier = modifier) {
        LeafletMapView(
            html = html,
            mapGlobalVarName = "shareLocationMap",
            controller = controller,
            reloadToken = reloadToken,
            onMarkerClick = { id -> selectedRowId = id },
            onLoadStateChange = { loadState = it },
            onConsoleMessage = { consoleMessages.add(it) },
            logTag = "ShareLocationMap",
            modifier = Modifier.fillMaxSize(),
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
                OutlinedButton(onClick = { reloadToken++ }) { Text("Retry") }
            }
        } else if (filteredPoints.isEmpty()) {
            // "Display an appropriate message if no Publisher matches the
            // selected filter" — and, distinctly, if no one is sharing at
            // all yet.
            Card(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Text(
                    if (allPoints.isEmpty()) "No one is currently sharing their location." else "No Publisher matches the selected filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (loadState == MapLoadState.LOADED && invalidCount > 0) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp).padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Text(
                    "$invalidCount publisher${if (invalidCount == 1) "" else "s"} without a valid GPS location ${if (invalidCount == 1) "isn't" else "aren't"} shown on the map.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // "Add a dropdown filter that allows users to filter Publishers by
        // category" — same floating-Surface-over-the-map design language as
        // Territory Map's own category dropdown.
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
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(selectedFilter.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }) {
                    PublisherTypeFilter.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                filterMenuExpanded = false
                                selectedFilter = option
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }

    if (selectedRow != null) {
        SharePointDetailsSheet(
            row = selectedRow,
            onDismiss = { selectedRowId = null },
            onOpenTerritoryMap = {
                onOpenTerritoryMap(selectedRow.location.lat, selectedRow.location.lng, selectedRow.person.fullName)
                selectedRowId = null
            },
        )
    }
}

/** "Clicking or tapping a marker should display relevant Publisher
 * information" — Name/Group, Category, Congregation, Last Updated, and
 * clickable coordinates that open Territory Map, same field set List View's
 * own row already shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharePointDetailsSheet(
    row: SharedLocationRow,
    onDismiss: () -> Unit,
    onOpenTerritoryMap: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1A73E8)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👤", style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text(
                        row.groupName?.let { "${row.person.fullName} ($it)" } ?: row.person.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        row.category?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Publisher",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DetailRow(label = "Congregation", value = row.congregationName)
            DetailRow(label = "Coordinates", value = formatCoordinatesDms(row.location.lat, row.location.lng))
            DetailRow(label = "Last Updated", value = formatShareRelativeTime(row.location.updatedAt))
            Button(onClick = onOpenTerritoryMap, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("View in Territory Map")
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

/** "Last Updated: Just now" — same coarse human buckets as Territory Map's
 * own `formatRelativeTime` (private to that file, so duplicated here rather
 * than exported for a four-line function). */
private fun formatShareRelativeTime(updatedAtMillis: Long): String {
    val minutes = (System.currentTimeMillis() - updatedAtMillis) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        minutes < 24 * 60 -> "${minutes / 60} hour${if (minutes / 60 == 1L) "" else "s"} ago"
        else -> "${minutes / (24 * 60)} day${if (minutes / (24 * 60) == 1L) "" else "s"} ago"
    }
}

private fun jsEscapeShare(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

/** Same proven Leaflet+OpenStreetMap+marker-cluster structure as Territory
 * Map's own `buildTerritoryMapHtml` — cluster group, tile-error diagnostics,
 * `fitToVisible`, an `applyFilter` that shows/hides by id (Territory Map's
 * own is by category "kind"; this map only ever shows one kind of marker —
 * Publisher — so filtering by id list is simpler and equivalent), and the
 * same triple `setTimeout` invalidateSize/fit fallback. */
private fun buildShareLocationMapHtml(points: List<SharePoint>): String {
    val pointsJson = points.joinToString(",", prefix = "[", postfix = "]") { p ->
        val label = p.row.groupName?.let { "${p.row.person.fullName} ($it)" } ?: p.row.person.fullName
        """{id:"${jsEscapeShare(p.id)}",lat:${p.lat},lng:${p.lng},name:"${jsEscapeShare(label)}"}"""
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
          html, body { height: 100%; margin: 0; padding: 0; }
          #map { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
          .share-label { background: rgba(255,255,255,0.9); border: none; box-shadow: 0 1px 3px rgba(0,0,0,0.3); padding: 1px 6px; font-size: 12px; }
          .share-marker { background: transparent; border: none; }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/leaflet.markercluster.js"></script>
        <script>
        try {
          var points = $pointsJson;
          var map = L.map('map', { zoomControl: true });
          window.shareLocationMap = map;
          var tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
          }).addTo(map);
          var tileErrorCount = 0;
          tiles.on('tileerror', function(e) {
            tileErrorCount++;
            console.error('Tile load failed (' + tileErrorCount + '): ' + (e.tile && e.tile.src));
          });

          function buildIcon(selected) {
            var size = selected ? 34 : 26;
            var fontSize = selected ? 16 : 13;
            var border = selected ? '3px solid #ffd600' : '2px solid #ffffff';
            var html = '<div style="width:' + size + 'px;height:' + size + 'px;border-radius:50%;background:#1a73e8;border:' + border +
              ';box-shadow:0 1px 3px rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center;font-size:' + fontSize + 'px;line-height:1;">👤</div>';
            return L.divIcon({ className: 'share-marker', html: html, iconSize: [size, size], iconAnchor: [size / 2, size / 2] });
          }

          var selectedMarkerId = null;
          window.setSelectedMarker = function(id) {
            if (selectedMarkerId && selectedMarkerId !== id && markersById[selectedMarkerId]) {
              markersById[selectedMarkerId].setIcon(buildIcon(false));
            }
            selectedMarkerId = id || null;
            if (id && markersById[id]) { markersById[id].setIcon(buildIcon(true)); }
          };

          var cluster = L.markerClusterGroup();
          var markers = [];
          var markersById = {};

          points.forEach(function(p) {
            var marker = L.marker([p.lat, p.lng], { icon: buildIcon(false) });
            marker.bindTooltip('👤 ' + p.name, { permanent: true, direction: 'right', offset: [8, 0], className: 'share-label' });
            marker.on('click', function() {
              window.setSelectedMarker(p.id);
              if (window.AndroidBridge) { AndroidBridge.showDetails(p.id); }
            });
            marker._id = p.id;
            marker._inCluster = true;
            cluster.addLayer(marker);
            markers.push(marker);
            markersById[p.id] = marker;
          });
          map.addLayer(cluster);

          var lastVisible = markers.slice();
          function fitToVisible(list) {
            if (list.length === 1) {
              map.setView(list[0].getLatLng(), 16);
            } else if (list.length > 1) {
              map.fitBounds(L.featureGroup(list).getBounds().pad(0.2));
            } else {
              map.setView([0, 0], 2);
            }
          }
          fitToVisible(lastVisible);

          // "Update the map markers dynamically without requiring a manual
          // page reload... remove markers that do not match the selected
          // category" — [idsCsv] is the exact set of point ids that should
          // stay visible, computed in Kotlin from whichever Publisher-type
          // filter is selected.
          window.applyFilter = function(idsCsv) {
            var ids = idsCsv ? idsCsv.split(',') : [];
            var visible = [];
            markers.forEach(function(m) {
              var shouldShow = ids.indexOf(m._id) !== -1;
              if (shouldShow && !m._inCluster) { cluster.addLayer(m); m._inCluster = true; }
              if (!shouldShow && m._inCluster) { cluster.removeLayer(m); m._inCluster = false; }
              if (shouldShow) visible.push(m);
            });
            lastVisible = visible;
            fitToVisible(visible);
          };

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
          console.error('Share location map script threw: ' + e.message);
        }
        </script>
        </body>
        </html>
    """.trimIndent()
}
