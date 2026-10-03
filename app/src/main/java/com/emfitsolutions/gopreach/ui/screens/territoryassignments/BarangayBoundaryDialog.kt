package com.emfitsolutions.gopreach.ui.screens.territoryassignments

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.ui.components.map.LeafletMapView
import com.emfitsolutions.gopreach.ui.components.map.MapLoadState
import com.emfitsolutions.gopreach.ui.components.map.rememberLeafletMapController
import kotlinx.coroutines.launch
import java.util.Locale

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var mapCommand by remember(municipality, barangayName) { mutableStateOf<MapCommand?>(null) }
    var distanceLabel by remember(municipality, barangayName) { mutableStateOf<String?>(null) }

    LaunchedEffect(municipality, barangayName) {
        isLoading = true
        geometryJson = viewModel.boundaryGeometry(municipality, barangayName)
        isLoading = false
    }

    // "Add my location, then compare the distance to the selected barangay"
    // — one fused-location fix (same provider Share Location already uses,
    // via viewModel.currentLocation()), pushed into the already-loaded map
    // page as a marker + dashed line to the boundary's center. Distance
    // itself is computed in JS (Leaflet's own map.distance(), true WGS84
    // great-circle) and handed back through the one JS->Android bridge
    // [LeafletMapView] already exposes (AndroidBridge.showDetails), rather
    // than re-deriving a polygon centroid in Kotlin from raw GeoJSON.
    fun locateMe() {
        if (!viewModel.isLocationServicesEnabled()) {
            scope.launch { snackbarHostState.showSnackbar("Location services are disabled. Please enable GPS to continue.") }
            return
        }
        scope.launch {
            val fix = viewModel.currentLocation()
            if (fix == null) {
                snackbarHostState.showSnackbar("Could not get your current location. Make sure location is turned on and try again.")
            } else {
                mapCommand = MapCommand.ShowMyLocation(fix.lat, fix.lng)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            locateMe()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Location permission is required to show your location.") }
        }
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (!isLoading && geometryJson != null) {
                    FloatingActionButton(
                        onClick = {
                            if (viewModel.hasLocationPermission()) {
                                locateMe()
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = "Show my location")
                    }
                }
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
                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        BarangayBoundaryMap(
                            geometryJson = geometryJson!!,
                            command = mapCommand,
                            onDistanceComputed = { meters ->
                                distanceLabel = "📍 ${formatDistance(meters)} from $barangayName"
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        distanceLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One-shot instruction pushed down into the already-loaded map page —
 * [mapCommand] changing (even to an equal-looking new instance) is what
 * [BarangayBoundaryMap] keys its `LaunchedEffect` on to re-run the JS call,
 * so tapping "my location" again after it already ran still works. */
private sealed class MapCommand {
    data class ShowMyLocation(val lat: Double, val lng: Double) : MapCommand()
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)

/** The actual map — real pan/zoom (unlike [com.emfitsolutions.gopreach.ui
 * .screens.findlocation.LocationPreviewMap]'s locked-down marker preview,
 * this dialog's whole purpose is examining one boundary, so interaction stays
 * on), one polygon drawn and the view fit to its bounds on load. Needs an
 * internet connection for map tiles, same as every other [LeafletMapView]
 * consumer — [MapLoadState.FAILED] below covers that case. */
@Composable
private fun BarangayBoundaryMap(
    geometryJson: String,
    command: MapCommand?,
    onDistanceComputed: (meters: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val html = remember(geometryJson) { buildBoundaryHtml(geometryJson) }
    val controller = rememberLeafletMapController()
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }

    // Re-runs whenever [command] changes (including tapping "my location"
    // again) — only once the page has actually finished loading, since
    // `window.showMyLocation` doesn't exist in the page until then.
    LaunchedEffect(command, loadState) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        when (command) {
            is MapCommand.ShowMyLocation ->
                controller.evaluateJavascript("if (window.showMyLocation) { window.showMyLocation(${command.lat}, ${command.lng}); }")
            null -> Unit
        }
    }

    Box(modifier = modifier) {
        LeafletMapView(
            html = html,
            mapGlobalVarName = "boundaryMap",
            controller = controller,
            // The one JS->Android bridge LeafletMapView exposes, repurposed
            // here to carry the distance figure JS already computed via
            // Leaflet's own map.distance() — see buildBoundaryHtml's
            // window.showMyLocation for the "DISTANCE:<meters>" message it sends.
            onMarkerClick = { id ->
                id.removePrefix("DISTANCE:").toDoubleOrNull()?.let(onDistanceComputed)
            },
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
      var boundaryCenter = bounds.isValid() ? bounds.getCenter() : null;
      if (bounds.isValid()) {
        map.fitBounds(bounds.pad(0.15), { maxZoom: 17 });
      } else {
        // No valid geometry bounds (shouldn't happen — this HTML is only
        // ever built once a non-null geometry came back) — fall back to
        // some view rather than leaving the map blank/uninitialized.
        map.setView([0, 0], 2);
      }
      // "Add my location, then compare the distance to the selected
      // barangay" — called from Kotlin (via the controller) once a fresh GPS
      // fix comes back. Distance to the boundary's own center (not its
      // nearest edge — simpler, and a reasonable "how far to this barangay"
      // figure for a Service Overseer/Secretary planning territory
      // assignments) uses Leaflet's own map.distance(), a true WGS84
      // great-circle distance, not a flat-plane approximation.
      var myLocationMarker = null;
      var myLocationLine = null;
      window.showMyLocation = function(lat, lng) {
        if (!boundaryCenter) return;
        if (myLocationMarker) { map.removeLayer(myLocationMarker); }
        if (myLocationLine) { map.removeLayer(myLocationLine); }
        var myLatLng = L.latLng(lat, lng);
        var pin = L.divIcon({
          className: '',
          html: '<div style="width:18px;height:18px;border-radius:50%;background:#1a73e8;border:3px solid #ffffff;box-shadow:0 1px 4px rgba(0,0,0,.5);"></div>',
          iconSize: [18, 18], iconAnchor: [9, 9]
        });
        myLocationMarker = L.marker(myLatLng, { icon: pin }).addTo(map).bindPopup('My Location');
        myLocationLine = L.polyline([myLatLng, boundaryCenter], { color: '#1a73e8', weight: 2, dashArray: '6, 6' }).addTo(map);
        var group = L.featureGroup([myLocationMarker, layer]);
        map.fitBounds(group.getBounds().pad(0.2), { maxZoom: 17 });
        var distanceMeters = map.distance(myLatLng, boundaryCenter);
        if (window.AndroidBridge) { window.AndroidBridge.showDetails('DISTANCE:' + distanceMeters); }
      };
    } catch (e) {
      console.error('Boundary map failed: ' + e);
    }
    </script>
    </body>
    </html>
""".trimIndent()
