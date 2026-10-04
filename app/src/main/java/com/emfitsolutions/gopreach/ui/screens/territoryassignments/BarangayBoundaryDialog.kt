package com.emfitsolutions.gopreach.ui.screens.territoryassignments

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.data.export.BoundaryKmlExporter
import com.emfitsolutions.gopreach.ui.components.map.LeafletMapView
import com.emfitsolutions.gopreach.ui.components.map.MapLoadState
import com.emfitsolutions.gopreach.ui.components.map.NamedBoundary
import com.emfitsolutions.gopreach.ui.components.map.NativeMapSupport
import com.emfitsolutions.gopreach.ui.components.map.TomTomBoundaryMap
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
    val context = LocalContext.current
    var mapCommand by remember(municipality, barangayName) { mutableStateOf<MapCommand?>(null) }
    var distanceLabel by remember(municipality, barangayName) { mutableStateOf<String?>(null) }
    var pointDistanceLabel by remember(municipality, barangayName) { mutableStateOf<String?>(null) }

    // "Show and hide my location" — [isLiveLocationOn] drives a continuous
    // subscription below, not a one-shot fetch, so toggling it on makes the
    // marker track the device live instead of freezing at one fix.
    var isLiveLocationOn by remember(municipality, barangayName) { mutableStateOf(false) }
    var isPickModeOn by remember(municipality, barangayName) { mutableStateOf(false) }

    LaunchedEffect(municipality, barangayName) {
        isLoading = true
        geometryJson = viewModel.boundaryGeometry(municipality, barangayName)
        isLoading = false
    }

    // "Add my location, then compare the distance to the selected barangay"
    // — a live fused-location subscription (same provider Share Location's
    // own live tracking uses), pushed into the already-loaded map page as a
    // marker on every fix. Distance itself is computed in JS (Leaflet's own
    // map.distance(), true WGS84 great-circle) and handed back through the
    // one JS->Android bridge [LeafletMapView] already exposes
    // (AndroidBridge.showDetails), rather than re-deriving a polygon
    // centroid in Kotlin from raw GeoJSON.
    LaunchedEffect(isLiveLocationOn) {
        if (!isLiveLocationOn) {
            mapCommand = MapCommand.HideMyLocation
            distanceLabel = null
            pointDistanceLabel = null
            isPickModeOn = false
            return@LaunchedEffect
        }
        runCatching {
            viewModel.locationUpdates().collect { fix ->
                mapCommand = MapCommand.UpdateMyLocation(fix.lat, fix.lng)
            }
        }.onFailure {
            isLiveLocationOn = false
            snackbarHostState.showSnackbar("Could not get your current location. Make sure location is turned on and try again.")
        }
    }

    fun turnOnLiveLocation() {
        if (!viewModel.isLocationServicesEnabled()) {
            scope.launch { snackbarHostState.showSnackbar("Location services are disabled. Please enable GPS to continue.") }
            return
        }
        isLiveLocationOn = true
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            turnOnLiveLocation()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Location permission is required to show your location.") }
        }
    }
    fun toggleLiveLocation() {
        if (isLiveLocationOn) {
            isLiveLocationOn = false
        } else if (viewModel.hasLocationPermission()) {
            turnOnLiveLocation()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // "Let the user select a point then calculate the distance from
    // location" — a second mode, independent of the barangay-distance
    // banner above: while on, tapping anywhere on the map (handled in JS,
    // see buildBoundaryHtml's map.on('click', ...)) drops a point marker and
    // reports its distance from the live location fix. Requires live
    // location already on, since a point is meaningless without a "from".
    fun togglePickMode() {
        if (!isLiveLocationOn) {
            scope.launch { snackbarHostState.showSnackbar("Turn on My Location first, then tap the map to pick a point.") }
            return
        }
        isPickModeOn = !isPickModeOn
        mapCommand = MapCommand.SetPickMode(isPickModeOn)
        if (isPickModeOn) {
            scope.launch { snackbarHostState.showSnackbar("Tap anywhere on the map to measure distance from your location.") }
        } else {
            pointDistanceLabel = null
        }
    }

    // "If the selected point is clicked, show Google Maps — show the way
    // from my location" — a plain directions deep link (not a package-
    // specific intent for the Google Maps app) so it still works through
    // whatever the device resolves ACTION_VIEW maps URLs to (Google Maps if
    // installed, a browser fallback otherwise), same tolerant approach the
    // rest of this app takes for external links.
    fun openDirections(originLat: Double, originLng: Double, destLat: Double, destLng: Double) {
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng&travelmode=driving",
        )
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { scope.launch { snackbarHostState.showSnackbar("Could not open Google Maps.") } }
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
                    actions = {
                        // "Can the line barrier also show in Google Maps?" —
                        // see BoundaryKmlExporter's own doc comment for why
                        // this can only ever be a manual KML import into
                        // Google My Maps (no API renders a custom polygon
                        // inside a maps deep link), not something automatic.
                        if (geometryJson != null) {
                            IconButton(
                                onClick = { BoundaryKmlExporter.share(context, geometryJson!!, barangayName, municipality) },
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share boundary as KML")
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                // Live location / pick-a-point only exist on the Leaflet
                // fallback's JS bridge — TomTomBoundaryMap below is a plain
                // boundary view with no equivalent yet, so these FABs would
                // just do nothing on a device that gets the native map.
                if (!isLoading && geometryJson != null && !NativeMapSupport.isSupported(context)) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = { togglePickMode() },
                            containerColor = if (isPickModeOn) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ) {
                            Icon(
                                Icons.Rounded.PinDrop,
                                contentDescription = if (isPickModeOn) "Stop picking a point" else "Pick a point to measure distance",
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        FloatingActionButton(
                            onClick = { toggleLiveLocation() },
                            containerColor = if (isLiveLocationOn) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            Icon(
                                if (isLiveLocationOn) Icons.Rounded.MyLocation else Icons.Rounded.LocationOff,
                                contentDescription = if (isLiveLocationOn) "Hide my location" else "Show my location",
                            )
                        }
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
                    NativeMapSupport.isSupported(context) -> TomTomBoundaryMap(
                        boundaries = listOf(NamedBoundary(barangayName, geometryJson!!)),
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        BarangayBoundaryMap(
                            geometryJson = geometryJson!!,
                            command = mapCommand,
                            onDistanceComputed = { meters ->
                                distanceLabel = "📍 ${formatDistance(meters)} from $barangayName"
                            },
                            onPointDistanceComputed = { meters ->
                                pointDistanceLabel = "📏 ${formatDistance(meters)} to selected point"
                            },
                            onPointNeedsLocation = {
                                scope.launch { snackbarHostState.showSnackbar("Turn on My Location first.") }
                            },
                            onOpenDirections = { originLat, originLng, destLat, destLng ->
                                openDirections(originLat, originLng, destLat, destLng)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            distanceLabel?.let { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            pointDistanceLabel?.let { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Instruction pushed down into the already-loaded map page — [mapCommand]
 * changing (even to an equal-looking new instance) is what
 * [BarangayBoundaryMap] keys its `LaunchedEffect` on to re-run the JS call,
 * so e.g. every new live-location fix (a fresh [UpdateMyLocation] each time)
 * still gets pushed through. */
private sealed class MapCommand {
    data class UpdateMyLocation(val lat: Double, val lng: Double) : MapCommand()
    data object HideMyLocation : MapCommand()
    data class SetPickMode(val enabled: Boolean) : MapCommand()
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
    onPointDistanceComputed: (meters: Double) -> Unit,
    onPointNeedsLocation: () -> Unit,
    onOpenDirections: (originLat: Double, originLng: Double, destLat: Double, destLng: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val html = remember(geometryJson) { buildBoundaryHtml(geometryJson) }
    val controller = rememberLeafletMapController()
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }

    // Re-runs whenever [command] changes (including every new live-location
    // fix) — only once the page has actually finished loading, since these
    // `window.xxx` functions don't exist in the page until then.
    LaunchedEffect(command, loadState) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        when (command) {
            is MapCommand.UpdateMyLocation ->
                controller.evaluateJavascript("if (window.updateMyLocation) { window.updateMyLocation(${command.lat}, ${command.lng}); }")
            is MapCommand.HideMyLocation ->
                controller.evaluateJavascript("if (window.hideMyLocation) { window.hideMyLocation(); }")
            is MapCommand.SetPickMode ->
                controller.evaluateJavascript("if (window.setPickMode) { window.setPickMode(${command.enabled}); }")
            null -> Unit
        }
    }

    Box(modifier = modifier) {
        LeafletMapView(
            html = html,
            mapGlobalVarName = "boundaryMap",
            controller = controller,
            // The one JS->Android bridge LeafletMapView exposes, repurposed
            // here to carry distance figures JS already computed via
            // Leaflet's own map.distance(), and the "open directions" request
            // from tapping the selected-point marker — see buildBoundaryHtml's
            // window.updateMyLocation / map click handler / selectedPointMarker
            // click handler for the "DISTANCE:<meters>" / "POINT_DISTANCE:
            // <meters>" / "POINT_NO_LOCATION" / "OPEN_MAPS:lat,lng:lat,lng"
            // messages they send.
            onMarkerClick = { id ->
                when {
                    id.startsWith("DISTANCE:") -> id.removePrefix("DISTANCE:").toDoubleOrNull()?.let(onDistanceComputed)
                    id.startsWith("POINT_DISTANCE:") -> id.removePrefix("POINT_DISTANCE:").toDoubleOrNull()?.let(onPointDistanceComputed)
                    id == "POINT_NO_LOCATION" -> onPointNeedsLocation()
                    id.startsWith("OPEN_MAPS:") -> runCatching {
                        val parts = id.removePrefix("OPEN_MAPS:").split(":")
                        val (originLat, originLng) = parts[0].split(",").map { it.toDouble() }
                        val (destLat, destLng) = parts[1].split(",").map { it.toDouble() }
                        onOpenDirections(originLat, originLng, destLat, destLng)
                    }
                }
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
      /* "Make my location live and blinking" — a pulsing ring behind a solid
         dot, the same visual convention Google/Apple Maps use for a live
         (as opposed to one-shot) location fix. */
      .my-location-dot {
        width: 16px; height: 16px; border-radius: 50%; background: #1a73e8;
        border: 3px solid #ffffff; box-shadow: 0 1px 4px rgba(0,0,0,.5); position: relative;
      }
      .my-location-pulse {
        position: absolute; top: 50%; left: 50%; width: 16px; height: 16px; margin: -8px 0 0 -8px;
        border-radius: 50%; background: rgba(26, 115, 232, 0.55);
        animation: my-location-pulse 1.6s ease-out infinite;
      }
      @keyframes my-location-pulse {
        0% { transform: scale(1); opacity: 0.8; }
        100% { transform: scale(3); opacity: 0; }
      }
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
      // "Lesser opacity" on the barrier fill — the red outline (weight 3,
      // full opacity) stays clearly visible on its own; only the fill
      // behind it is now light enough that satellite imagery underneath
      // still reads through.
      var layer = L.geoJSON(geometry, {
        style: { color: '#D32F2F', weight: 3, fillColor: '#D32F2F', fillOpacity: 0.08 }
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
      // barangay" + "make my location live and blinking" + "show and hide
      // my location" — window.updateMyLocation is called from Kotlin on
      // every fix of a continuous location subscription (not a one-shot
      // fetch), so it both moves the marker live and keeps recomputing the
      // distance to the boundary's own center (not its nearest edge —
      // simpler, and a reasonable "how far to this barangay" figure for a
      // Service Overseer/Secretary planning territory assignments) via
      // Leaflet's own map.distance(), a true WGS84 great-circle distance.
      // The CSS pulse animation (.my-location-pulse) is what makes it
      // "blinking" — Leaflet itself has no live/animated marker concept, so
      // the blink is pure CSS on a marker Kotlin just keeps moving.
      var myLocationMarker = null;
      var myLocationLatLng = null;
      var myLocationLine = null;
      var hasFitMyLocation = false;
      window.updateMyLocation = function(lat, lng) {
        myLocationLatLng = L.latLng(lat, lng);
        if (!myLocationMarker) {
          var pin = L.divIcon({
            className: '',
            html: '<div class="my-location-dot"><div class="my-location-pulse"></div></div>',
            iconSize: [16, 16], iconAnchor: [8, 8]
          });
          myLocationMarker = L.marker(myLocationLatLng, { icon: pin, zIndexOffset: 1000 }).addTo(map).bindPopup('My Location');
        } else {
          myLocationMarker.setLatLng(myLocationLatLng);
        }
        if (boundaryCenter) {
          if (myLocationLine) { map.removeLayer(myLocationLine); }
          myLocationLine = L.polyline([myLocationLatLng, boundaryCenter], { color: '#1a73e8', weight: 2, dashArray: '6, 6' }).addTo(map);
          var distanceMeters = map.distance(myLocationLatLng, boundaryCenter);
          if (window.AndroidBridge) { window.AndroidBridge.showDetails('DISTANCE:' + distanceMeters); }
        }
        // Only re-fit the view on the very first fix — once live tracking
        // is running, re-fitting on every update would otherwise yank the
        // view out from under anyone panning/zooming to look around.
        if (!hasFitMyLocation) {
          hasFitMyLocation = true;
          var group = L.featureGroup([myLocationMarker, layer]);
          map.fitBounds(group.getBounds().pad(0.2), { maxZoom: 17 });
        }
      };
      window.hideMyLocation = function() {
        if (myLocationMarker) { map.removeLayer(myLocationMarker); myLocationMarker = null; }
        if (myLocationLine) { map.removeLayer(myLocationLine); myLocationLine = null; }
        if (selectedPointMarker) { map.removeLayer(selectedPointMarker); selectedPointMarker = null; }
        if (selectedPointLine) { map.removeLayer(selectedPointLine); selectedPointLine = null; }
        myLocationLatLng = null;
        selectedPointLatLng = null;
        pickModeOn = false;
        hasFitMyLocation = false;
      };
      // "Let the user select a point then calculate the distance from
      // location" — only active while [pickModeOn] (toggled from Kotlin via
      // window.setPickMode), so an ordinary pan/zoom tap never accidentally
      // drops a point.
      var pickModeOn = false;
      var selectedPointMarker = null;
      var selectedPointLatLng = null;
      var selectedPointLine = null;
      window.setPickMode = function(enabled) {
        pickModeOn = enabled;
      };
      map.on('click', function(e) {
        if (!pickModeOn) return;
        if (!myLocationLatLng) {
          if (window.AndroidBridge) { window.AndroidBridge.showDetails('POINT_NO_LOCATION'); }
          return;
        }
        if (selectedPointMarker) { map.removeLayer(selectedPointMarker); }
        if (selectedPointLine) { map.removeLayer(selectedPointLine); }
        selectedPointLatLng = e.latlng;
        var pin = L.divIcon({
          className: '',
          html: '<div style="width:16px;height:16px;border-radius:50%;background:#e8710a;border:3px solid #ffffff;box-shadow:0 1px 4px rgba(0,0,0,.5);"></div>',
          iconSize: [16, 16], iconAnchor: [8, 8]
        });
        // "If the selected point is clicked, show Google Maps — show the way
        // from my location" — the marker's own click (separate from the
        // map's click above, which only fires on empty map space since
        // Leaflet markers stop event propagation) hands the live location +
        // this point back to Kotlin to open turn-by-turn directions.
        selectedPointMarker = L.marker(e.latlng, { icon: pin }).addTo(map);
        selectedPointMarker.on('click', function() {
          if (myLocationLatLng && selectedPointLatLng && window.AndroidBridge) {
            window.AndroidBridge.showDetails(
              'OPEN_MAPS:' + myLocationLatLng.lat + ',' + myLocationLatLng.lng + ':' +
              selectedPointLatLng.lat + ',' + selectedPointLatLng.lng
            );
          }
        });
        selectedPointLine = L.polyline([myLocationLatLng, e.latlng], { color: '#e8710a', weight: 2, dashArray: '4, 8' }).addTo(map);
        var distanceMeters = map.distance(myLocationLatLng, e.latlng);
        if (window.AndroidBridge) { window.AndroidBridge.showDetails('POINT_DISTANCE:' + distanceMeters); }
      });
    } catch (e) {
      console.error('Boundary map failed: ' + e);
    }
    </script>
    </body>
    </html>
""".trimIndent()
