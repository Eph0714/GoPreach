package com.emfitsolutions.gopreach.ui.screens.territoryassignments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.ui.components.map.LeafletMapView
import com.emfitsolutions.gopreach.ui.components.map.MapLoadState
import com.emfitsolutions.gopreach.ui.components.map.rememberLeafletMapController

/**
 * "If a barangay is selected show the boundary map" — a full-screen dialog
 * showing one barangay's real polygon boundary (same bundled OCHA/NAMRIA/PSA
 * data [com.emfitsolutions.gopreach.data.repository.TerritoryBoundaryRepository]
 * already supplies to Territory Map, just drawn on its own here instead of
 * inside that screen's full multi-layer map). Only Nueva Vizcaya is bundled
 * today — a barangay outside that coverage shows a plain "not available yet"
 * message rather than an error, the same graceful-miss convention every
 * other boundary lookup in this app already follows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarangayBoundaryDialog(
    municipality: String,
    barangayName: String,
    onDismiss: () -> Unit,
    viewModel: TerritoryAssignmentsViewModel = hiltViewModel(),
) {
    var geometryJson by remember(municipality, barangayName) { mutableStateOf<String?>(null) }
    var isLoading by remember(municipality, barangayName) { mutableStateOf(true) }

    LaunchedEffect(municipality, barangayName) {
        isLoading = true
        geometryJson = viewModel.boundaryGeometry(municipality, barangayName)
        isLoading = false
    }

    // usePlatformDefaultWidth = false — the one flag that lets a Dialog's
    // content actually span the full screen instead of being capped to
    // Android's default dialog max-width, same as every other full-screen
    // Dialog in this app.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(barangayName)
                            Text(municipality, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close")
                        }
                    },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    geometryJson == null -> Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No boundary map available for this barangay yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> BarangayBoundaryMap(geometryJson = geometryJson!!, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The actual map — real pan/zoom (unlike [com.emfitsolutions.gopreach.ui
 * .screens.findlocation.LocationPreviewMap]'s locked-down marker preview,
 * this dialog's whole purpose is examining one boundary, so interaction stays
 * on), one polygon drawn and the view fit to its bounds on load. Needs an
 * internet connection for map tiles, same as every other [LeafletMapView]
 * consumer — [MapLoadState.FAILED] below covers that case. */
@Composable
private fun BarangayBoundaryMap(geometryJson: String, modifier: Modifier = Modifier) {
    val html = remember(geometryJson) { buildBoundaryHtml(geometryJson) }
    val controller = rememberLeafletMapController()
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }

    Box(modifier = modifier) {
        LeafletMapView(
            html = html,
            mapGlobalVarName = "boundaryMap",
            controller = controller,
            onMarkerClick = {},
            onLoadStateChange = { loadState = it },
            onConsoleMessage = {},
            logTag = "BarangayBoundaryMap",
            modifier = Modifier.fillMaxSize(),
        )
        when (loadState) {
            MapLoadState.LOADING -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            MapLoadState.FAILED -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Map unavailable. Check your internet connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MapLoadState.LOADED -> Unit
        }
    }
}

/** Same "draw one polygon, fit bounds with a sane zoom floor/ceiling" shape
 * as Territory Map's own `setAreaBoundaryGeoJson` JS function — lifted as a
 * standalone page here rather than reused directly, since that function
 * lives embedded inside Territory Map's own much larger multi-layer HTML
 * builder (markers, Group color coding, search) with nothing to extract
 * cleanly from. [geometryJson] is trusted, already-validated GeoJSON from
 * [com.emfitsolutions.gopreach.data.repository.TerritoryBoundaryRepository]'s
 * bundled asset, not user input — safe to inline directly into the page. */
private fun buildBoundaryHtml(geometryJson: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css">
    <style>
      html, body { height: 100%; margin: 0; padding: 0; }
      #map { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
    </style>
    </head>
    <body>
    <div id="map"></div>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
    <script>
    try {
      // Bug fix ("show the map zoomed to the selected barangay, not the
      // whole world") — L.map(id) alone renders nothing until some call
      // actually sets a view; the previous version called setView([0,0], 2)
      // first (the whole-world zoom level) and only fit the real bounds in a
      // second step right after, so that initial world view still painted
      // for a frame before the zoom-in. The geometry (and therefore its
      // bounds) is already known synchronously here — no need to ever set a
      // throwaway world view at all. fitBounds is the map's first and only
      // view-setting call below.
      var map = L.map('map', { zoomControl: true });
      window.boundaryMap = map;
      // "Make a choice for map view: satellite, 3D, and other" — true 3D
      // needs real WebGL vector rendering, which this exact device's WebView
      // was already confirmed (see TerritoryMapScreen.kt's own comment on
      // the reverted OpenFreeMap/MapLibre attempt) to silently paint nothing
      // despite reporting a working WebGL context — not offered here for
      // that reason. Satellite / Standard / Night are all plain raster tiles
      // (same proven-reliable approach as before) and switch via Leaflet's
      // own built-in layers control (top-right icon). Satellite is plain
      // imagery only — no road-line overlay — per explicit request to drop
      // the white hybrid-layer lines and show "just a real map".
      var satelliteLayer = L.tileLayer('https://api.tomtom.com/map/1/tile/sat/main/{z}/{x}/{y}.jpg?key=${BuildConfig.TOMTOM_API_KEY}', {
        maxZoom: 22, attribution: '&copy; TomTom'
      });
      var standardLayer = L.tileLayer('https://api.tomtom.com/map/1/tile/basic/main/{z}/{x}/{y}.png?key=${BuildConfig.TOMTOM_API_KEY}', {
        maxZoom: 22, attribution: '&copy; TomTom'
      });
      var nightLayer = L.tileLayer('https://api.tomtom.com/map/1/tile/basic/night/{z}/{x}/{y}.png?key=${BuildConfig.TOMTOM_API_KEY}', {
        maxZoom: 22, attribution: '&copy; TomTom'
      });
      satelliteLayer.addTo(map);
      L.control.layers(
        { 'Satellite': satelliteLayer, 'Standard': standardLayer, 'Night': nightLayer },
        null,
        { position: 'topright' }
      ).addTo(map);
      var geometry = $geometryJson;
      var layer = L.geoJSON(geometry, {
        style: { color: '#D32F2F', weight: 3, fillColor: '#D32F2F', fillOpacity: 0.2 }
      }).addTo(map);
      var bounds = layer.getBounds();
      if (bounds.isValid()) {
        map.fitBounds(bounds.pad(0.15), { maxZoom: 17 });
      } else {
        // No valid geometry bounds (shouldn't happen — this HTML is only
        // ever built once a non-null geometry came back) — fall back to
        // some view rather than leaving the map blank/uninitialized.
        map.setView([0, 0], 2);
      }
    } catch (e) {
      console.error('Boundary map failed: ' + e);
    }
    </script>
    </body>
    </html>
""".trimIndent()
