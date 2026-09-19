package com.emfitsolutions.gopreach.ui.screens.findlocation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.ui.components.map.LeafletMapView
import com.emfitsolutions.gopreach.ui.components.map.MapLoadState
import com.emfitsolutions.gopreach.ui.components.map.rememberLeafletMapController

/**
 * A small, non-interactive map showing one found coordinate — the "Map/
 * Location Preview" on Find Location's LOCATION FOUND card. Reuses the shared
 * [LeafletMapView] WebView host (see its doc comment for why that's the one
 * place that handles Leaflet embedding reliably) with a minimal single-marker
 * page. Panning/zooming is switched off so the preview never fights the
 * surrounding scroll; exploring the area is what Look Around is for. Needs an
 * internet connection for map tiles — offline it falls back to a short note,
 * never blocking the actions beside it.
 */
@Composable
fun LocationPreviewMap(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    val html = remember(lat, lng) { buildPreviewHtml(lat, lng) }
    val controller = rememberLeafletMapController()
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }

    Box(modifier = modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))) {
        LeafletMapView(
            html = html,
            mapGlobalVarName = "previewMap",
            controller = controller,
            onMarkerClick = {},
            onLoadStateChange = { loadState = it },
            onConsoleMessage = {},
            logTag = "FindLocationPreview",
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
                    "Map preview unavailable. Check your internet connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MapLoadState.LOADED -> Unit
        }
    }
}

private fun buildPreviewHtml(lat: Double, lng: Double): String = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
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
      var map = L.map('map', {
        zoomControl: false, dragging: false, touchZoom: false, doubleClickZoom: false,
        scrollWheelZoom: false, boxZoom: false, keyboard: false
      }).setView([$lat, $lng], 17);
      window.previewMap = map;
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap contributors'
      }).addTo(map);
      var pin = L.divIcon({
        className: '',
        html: '<div style="width:22px;height:22px;border-radius:50%;background:#d93025;border:3px solid #ffffff;box-shadow:0 1px 4px rgba(0,0,0,.5);"></div>',
        iconSize: [22, 22], iconAnchor: [11, 11]
      });
      L.marker([$lat, $lng], { icon: pin, interactive: false }).addTo(map);
    } catch (e) {
      console.error('Preview map failed: ' + e);
    }
    </script>
    </body>
    </html>
""".trimIndent()
