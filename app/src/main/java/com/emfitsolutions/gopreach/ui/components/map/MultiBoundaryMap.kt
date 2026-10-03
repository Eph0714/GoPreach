package com.emfitsolutions.gopreach.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.emfitsolutions.gopreach.BuildConfig
import com.google.gson.Gson

/**
 * Leaflet/TomTom-tiles fallback for [TomTomBoundaryMap] — same bundled
 * boundary data, same TomTom raster tiles already proven elsewhere in this
 * app (see [com.emfitsolutions.gopreach.ui.screens.territoryassignments
 * .BarangayBoundaryDialog]'s own `buildBoundaryHtml`), used wherever
 * [NativeMapSupport.isSupported] is false. Draws every boundary as its own
 * polygon and fits the view to all of them combined, rather than the single-
 * polygon case that dialog handles.
 */
@Composable
fun MultiBoundaryMap(
    boundaries: List<NamedBoundary>,
    modifier: Modifier = Modifier,
) {
    val html = remember(boundaries) { buildMultiBoundaryHtml(boundaries) }
    val controller = rememberLeafletMapController()
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }

    Box(modifier = modifier) {
        LeafletMapView(
            html = html,
            mapGlobalVarName = "multiBoundaryMap",
            controller = controller,
            onMarkerClick = {},
            onLoadStateChange = { loadState = it },
            onConsoleMessage = {},
            logTag = "MultiBoundaryMap",
            modifier = Modifier.fillMaxSize(),
        )
        when (loadState) {
            MapLoadState.LOADING -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            MapLoadState.FAILED -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
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

private fun buildMultiBoundaryHtml(boundaries: List<NamedBoundary>): String {
    // Each geometry is already-validated GeoJSON straight from
    // TerritoryBoundaryRepository's bundled asset (never user input) — safe
    // to inline directly, same trust boundary BarangayBoundaryDialog's own
    // buildBoundaryHtml documents. Gson.toJson on the plain name string here
    // is just correct JS-string escaping, not re-serializing trusted data.
    val gson = Gson()
    val layersJs = boundaries.joinToString(",\n") { boundary ->
        "{ name: ${gson.toJson(boundary.name)}, geometry: ${boundary.geometryJson} }"
    }
    return """
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
          var map = L.map('map', { zoomControl: true });
          window.multiBoundaryMap = map;
          L.tileLayer('https://api.tomtom.com/map/1/tile/basic/main/{z}/{x}/{y}.png?key=${BuildConfig.TOMTOM_API_KEY}', {
            maxZoom: 22, attribution: '&copy; TomTom'
          }).addTo(map);
          var boundaries = [$layersJs];
          var allLayers = [];
          boundaries.forEach(function(b) {
            var layer = L.geoJSON(b.geometry, {
              style: { color: '#D32F2F', weight: 3, fillColor: '#D32F2F', fillOpacity: 0.08 }
            }).bindPopup(b.name).addTo(map);
            allLayers.push(layer);
          });
          if (allLayers.length > 0) {
            var group = L.featureGroup(allLayers);
            var bounds = group.getBounds();
            if (bounds.isValid()) {
              map.fitBounds(bounds.pad(0.15), { maxZoom: 17 });
            } else {
              map.setView([0, 0], 2);
            }
          } else {
            map.setView([0, 0], 2);
          }
        } catch (e) {
          console.error('Multi-boundary map failed: ' + e);
        }
        </script>
        </body>
        </html>
    """.trimIndent()
}
