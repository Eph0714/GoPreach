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
import com.emfitsolutions.gopreach.data.repository.AreaFeature
import com.emfitsolutions.gopreach.data.repository.BuildingFootprint
import com.emfitsolutions.gopreach.data.repository.Landmark
import com.emfitsolutions.gopreach.data.repository.StreetSegment
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
    province: String,
    municipality: String,
    barangayName: String,
    onDismiss: () -> Unit,
    viewModel: TerritoryAssignmentsViewModel = hiltViewModel(),
) {
    var geometryJson by remember(municipality, barangayName) { mutableStateOf<String?>(null) }
    var isLoading by remember(municipality, barangayName) { mutableStateOf(true) }
    var landmarks by remember(municipality, barangayName) { mutableStateOf<List<Landmark>>(emptyList()) }
    var streets by remember(municipality, barangayName) { mutableStateOf<List<StreetSegment>>(emptyList()) }
    var areas by remember(municipality, barangayName) { mutableStateOf<List<AreaFeature>>(emptyList()) }
    var buildings by remember(municipality, barangayName) { mutableStateOf<List<BuildingFootprint>>(emptyList()) }
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

    LaunchedEffect(province, municipality, barangayName) {
        isLoading = true
        geometryJson = viewModel.boundaryGeometry(province, municipality, barangayName)
        isLoading = false
        val details = geometryJson?.let { viewModel.mapDetailsFor(it) }
        landmarks = details?.landmarks ?: emptyList()
        streets = details?.streets ?: emptyList()
        areas = details?.areas ?: emptyList()
        buildings = details?.buildings ?: emptyList()
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
                        landmarks = landmarks,
                        streets = streets,
                        areas = areas,
                        buildings = buildings,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        BarangayBoundaryMap(
                            geometryJson = geometryJson!!,
                            landmarks = landmarks,
                            streets = streets,
                            areas = areas,
                            buildings = buildings,
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
    landmarks: List<Landmark>,
    streets: List<StreetSegment>,
    areas: List<AreaFeature>,
    buildings: List<BuildingFootprint>,
    command: MapCommand?,
    onDistanceComputed: (meters: Double) -> Unit,
    onPointDistanceComputed: (meters: Double) -> Unit,
    onPointNeedsLocation: () -> Unit,
    onOpenDirections: (originLat: Double, originLng: Double, destLat: Double, destLng: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val html = remember(geometryJson, landmarks, streets, areas, buildings) {
        buildBoundaryHtml(geometryJson, landmarks, streets, areas, buildings)
    }
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
private fun landmarksJs(landmarks: List<Landmark>): String {
    val gson = com.google.gson.Gson()
    return landmarks.joinToString(",\n") { l ->
        "{ name: ${gson.toJson(l.name)}, lat: ${l.lat}, lng: ${l.lng}, " +
            "emoji: ${gson.toJson(l.category.emoji)}, color: ${gson.toJson(String.format("#%06X", l.category.colorArgb and 0xFFFFFF))}, " +
            "group: ${gson.toJson(l.category.group.name)} }"
    }
}

private fun streetsJs(streets: List<StreetSegment>): String {
    val gson = com.google.gson.Gson()
    return streets.joinToString(",\n") { s ->
        "{ points: [${s.points.joinToString(",") { (lat, lng) -> "[$lat,$lng]" }}], name: ${gson.toJson(s.name)} }"
    }
}

private fun areasJs(areas: List<AreaFeature>): String {
    val gson = com.google.gson.Gson()
    return areas.joinToString(",\n") { a -> "{ name: ${gson.toJson(a.name)}, lat: ${a.lat}, lng: ${a.lng} }" }
}

private fun buildingsJs(buildings: List<BuildingFootprint>): String =
    buildings.joinToString(",\n") { b -> "[${b.points.joinToString(",") { (lat, lng) -> "[$lat,$lng]" }}]" }

private fun buildBoundaryHtml(
    geometryJson: String,
    landmarks: List<Landmark>,
    streets: List<StreetSegment>,
    areas: List<AreaFeature>,
    buildings: List<BuildingFootprint>,
): String = """
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
      // "Make the text outside the selected barangay less opacity, so
      // focus stays on the selected barangay" — a plain even-odd ray cast
      // against the boundary's own outer ring(s), checked below for every
      // landmark/street/area pulled from a padded bounding box around the
      // boundary (not the boundary itself), so everything actually outside
      // it can be dimmed instead of drawn at full strength.
      function pointInRing(ring, lat, lng) {
        var inside = false;
        for (var i = 0, j = ring.length - 1; i < ring.length; j = i++) {
          var lngI = ring[i][0], latI = ring[i][1];
          var lngJ = ring[j][0], latJ = ring[j][1];
          if (((lngI > lng) !== (lngJ > lng)) && (lat < (latJ - latI) * (lng - lngI) / (lngJ - lngI) + latI)) {
            inside = !inside;
          }
        }
        return inside;
      }
      function pointInBoundary(lat, lng) {
        var polys = geometry.type === 'Polygon' ? [geometry.coordinates]
          : geometry.type === 'MultiPolygon' ? geometry.coordinates : [];
        return polys.some(function(poly) { return pointInRing(poly[0], lat, lng); });
      }
      var DIMMED_OPACITY = 0.4;
      // Borderless territory tint only — no drawn outline. The earlier red
      // stroke (plus its white halo) was redundant once the fill itself
      // already reads as "this area" against the dimmer map around it, the
      // same soft-highlight convention Google Maps uses for a selected
      // region instead of a hard polygon outline.
      var layer = L.geoJSON(geometry, {
        style: { stroke: false, fillColor: '#4285F4', fillOpacity: 0.12 }
      }).addTo(map);
      // "Add a feature for the user to select what he wants to see
      // specifically" — a small custom control (Leaflet has no built-in
      // concept of this) with one button per [MapLayer]. The boundary
      // tint, street lines, and building footprints are never gated by it
      // — only which landmark pins/labels show, plus whether street NAMES
      // (not the lines themselves) are visible.
      var LAYER_OPTIONS = [
        { id: 'ALL', label: 'All' },
        { id: 'LANDMARKS', label: 'Landmarks' },
        { id: 'CHURCHES', label: 'Churches' },
        { id: 'KINGDOM_HALL', label: 'Kingdom Hall' },
        { id: 'STREET_NAMES', label: 'Street Names' }
      ];
      var currentLayerFilter = 'ALL';
      var landmarkMarkers = [];
      var streetLabelEntries = [];
      function applyLayerFilter() {
        landmarkMarkers.forEach(function(entry) {
          var show = currentLayerFilter === 'ALL' || entry.group === currentLayerFilter;
          entry.el.style.display = show ? '' : 'none';
        });
        var showStreetNames = currentLayerFilter === 'ALL' || currentLayerFilter === 'STREET_NAMES';
        streetLabelEntries.forEach(function(entry) {
          entry.el.style.display = showStreetNames ? '' : 'none';
        });
      }
      var LayerFilterControl = L.Control.extend({
        options: { position: 'topright' },
        onAdd: function() {
          var container = L.DomUtil.create('div', '');
          container.style.display = 'flex';
          container.style.flexWrap = 'wrap';
          container.style.gap = '4px';
          container.style.maxWidth = '220px';
          container.style.marginTop = '6px';
          LAYER_OPTIONS.forEach(function(opt) {
            var btn = L.DomUtil.create('button', '', container);
            btn.innerText = opt.label;
            btn.style.cssText = 'font-size:11px;padding:4px 9px;border-radius:12px;border:none;' +
              'background:#fff;color:#202124;box-shadow:0 1px 3px rgba(0,0,0,.3);cursor:pointer;';
            if (opt.id === currentLayerFilter) { btn.style.background = '#c8dafc'; }
            L.DomEvent.on(btn, 'click', function(e) {
              L.DomEvent.stopPropagation(e);
              currentLayerFilter = opt.id;
              container.querySelectorAll('button').forEach(function(b, idx) {
                b.style.background = LAYER_OPTIONS[idx].id === opt.id ? '#c8dafc' : '#fff';
              });
              applyLayerFilter();
            });
          });
          return container;
        }
      });
      new LayerFilterControl().addTo(map);
      // "Make the map text and icons responsive if zoom in and out" —
      // Leaflet keeps every stroke weight and divIcon at a fixed pixel size
      // regardless of zoom unless told otherwise, so this app's own 'zoom'
      // handler below rescales them: thinner lines and smaller pins/text
      // zoomed out (so a whole-barangay overview isn't cluttered), thicker
      // and larger zoomed in. zoomScaledLayers/zoomScaledPins/
      // zoomScaledLabels collect everything that handler needs to touch —
      // appended to as streets/landmarks are added below.
      var zoomScaledLayers = [];
      var zoomScaledPins = [];
      var zoomScaledLabels = [];
      function zoomScale(zoom) {
        return Math.max(0.6, Math.min(1.5, 0.6 + (zoom - 10) * 0.12));
      }
      function applyZoomScale() {
        var scale = zoomScale(map.getZoom());
        zoomScaledLayers.forEach(function(entry) {
          entry.layer.setStyle({ weight: entry.base * scale });
        });
        zoomScaledPins.forEach(function(el) { el.style.transform = 'scale(' + scale + ')'; });
        zoomScaledLabels.forEach(function(el) { el.style.transform = el.baseTransform + ' scale(' + scale + ')'; });
      }
      map.on('zoom', applyZoomScale);
      // addScaledLabel — every plain-text (no-pin) label on this map (street
      // names, area names) goes through here so it's registered for the
      // zoom handler above exactly once, instead of repeating that
      // registration dance at each call site.
      function addScaledLabel(latlng, html, baseTransform) {
        var marker = L.marker(latlng, {
          icon: L.divIcon({ className: '', html: html, iconSize: [0, 0] }),
          interactive: false
        }).addTo(map);
        var el = marker.getElement();
        if (el) {
          var div = el.querySelector('.zoom-label');
          if (div) { div.baseTransform = baseTransform; zoomScaledLabels.push(div); return div; }
        }
        return null;
      }
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
      // Real named landmarks (school, church, market, ...) within this
      // barangay's own bounding box — see OverpassLandmarkRepository's own
      // doc comment for why the map needs these at all rather than just the
      // bare boundary. Each one is a colored pin matching its own
      // [LandmarkCategory] (school vs. shop vs. government office, ...)
      // with its name in a plain white chip below it — the same pin+label
      // look Google Maps itself uses for a point of interest, rather than
      // glowing text with no background.
      var landmarks = [${landmarksJs(landmarks)}];
      landmarks.forEach(function(l) {
        var opacity = pointInBoundary(l.lat, l.lng) ? 1 : DIMMED_OPACITY;
        var pin = L.divIcon({
          className: '',
          html: '<div class="zoom-pin" style="position:relative;width:30px;height:30px;transform-origin:50% 100%;opacity:' + opacity + ';">' +
            '<div style="width:30px;height:30px;border-radius:50%;background:' + l.color + ';' +
            'border:2px solid #ffffff;box-shadow:0 1px 3px rgba(0,0,0,.35);display:flex;' +
            'align-items:center;justify-content:center;font-size:15px;">' + l.emoji + '</div>' +
            '<div style="position:absolute;top:25px;left:11px;width:8px;height:8px;' +
            'background:' + l.color + ';transform:rotate(45deg);border-radius:0 0 2px 0;"></div>' +
            '<div style="position:absolute;top:34px;left:50%;transform:translateX(-50%);white-space:nowrap;' +
            'background:#ffffff;color:#202124;font-size:12px;line-height:1.3;' +
            'padding:2px 7px;border-radius:10px;box-shadow:0 1px 3px rgba(0,0,0,.3);">' + l.name + '</div></div>',
          iconSize: [30, 64], iconAnchor: [15, 30]
        });
        var marker = L.marker([l.lat, l.lng], { icon: pin }).addTo(map);
        var el = marker.getElement();
        if (el) {
          var pinDiv = el.querySelector('.zoom-pin');
          if (pinDiv) { zoomScaledPins.push(pinDiv); }
          landmarkMarkers.push({ el: el, group: l.group });
        }
      });
      // Real street lines, drawn independently of whatever TomTom's own
      // map data does or doesn't have for this area — see
      // OverpassLandmarkRepository's own doc comment. A dark casing under a
      // light fill reads as an actual road, and a named street gets its own
      // label sitting directly on the line at its own local bearing (the
      // same "name follows the road" convention Google Maps uses) rather
      // than always-horizontal text floating beside it.
      var streets = [${streetsJs(streets)}];
      function bearingDeg(p1, p2) {
        var lat1 = p1[0] * Math.PI / 180, lat2 = p2[0] * Math.PI / 180;
        var dLon = (p2[1] - p1[1]) * Math.PI / 180;
        var y = Math.sin(dLon) * Math.cos(lat2);
        var x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return Math.atan2(y, x) * 180 / Math.PI;
      }
      streets.forEach(function(s) {
        var midIdx = Math.floor(s.points.length / 2);
        var inside = pointInBoundary(s.points[midIdx][0], s.points[midIdx][1]);
        var lineOpacity = inside ? 1 : DIMMED_OPACITY;
        // "Make the streets wider so the street text fits inside it when
        // zoomed in" — a wider casing (scaled by the same zoom handler as
        // everything else) so the road itself visibly carries its own name
        // at a close zoom instead of a thin line with text floating beside it.
        var casing = L.polyline(s.points, { color: '#333333', weight: 7, opacity: 0.55 * lineOpacity }).addTo(map);
        var fill = L.polyline(s.points, { color: '#ffffff', weight: 3.5, opacity: 0.9 * lineOpacity }).addTo(map);
        zoomScaledLayers.push({ layer: casing, base: 7 });
        zoomScaledLayers.push({ layer: fill, base: 3.5 });
        if (s.name && s.points.length >= 2) {
          var p1 = s.points[Math.max(0, midIdx - 1)];
          var p2 = s.points[Math.min(s.points.length - 1, midIdx + 1)];
          var angle = bearingDeg(p1, p2);
          if (angle > 90) angle -= 180;
          if (angle < -90) angle += 180;
          var baseTransform = 'translate(-50%,-50%) rotate(' + angle + 'deg)';
          var labelEl = addScaledLabel(
            s.points[midIdx],
            '<div class="zoom-label" style="font-size:11px;color:#202124;text-shadow:0 0 2px #fff,0 1px 2px #fff;' +
              'font-weight:600;white-space:nowrap;opacity:' + (inside ? 1 : DIMMED_OPACITY) + ';transform:' + baseTransform + ';">' + s.name + '</div>',
            baseTransform
          );
          if (labelEl) { streetLabelEntries.push({ el: labelEl }); }
        }
      });
      // What's actually on the ground (rice fields, orchards, forest, ...)
      // within this barangay — plain italic text with no pin and no
      // background chip, the same soft area-label convention Google Maps
      // uses for parks/farmland/forest rather than a named point of interest.
      var areas = [${areasJs(areas)}];
      areas.forEach(function(a) {
        var inside = pointInBoundary(a.lat, a.lng);
        addScaledLabel(
          [a.lat, a.lng],
          '<div class="zoom-label" style="font-size:13px;font-style:italic;color:#dcedc8;' +
            'text-shadow:0 0 2px #000,0 1px 2px #000;white-space:nowrap;' +
            'opacity:' + (inside ? 1 : DIMMED_OPACITY) + ';transform:translate(-50%,-50%);">' + a.name + '</div>',
          'translate(-50%,-50%)'
        );
      });
      // "Add a drawing of the houses and buildings if zoom in" — real OSM
      // building footprints, only added to the map once zoomed in close
      // (a single poblacion barangay can have hundreds, which would just
      // be clutter at a whole-barangay overview).
      var BUILDING_ZOOM_THRESHOLD = 16;
      var buildingPolygons = [];
      var buildings = [${buildingsJs(buildings)}];
      var buildingsOnMap = false;
      function updateBuildingsVisibility() {
        var shouldShow = map.getZoom() >= BUILDING_ZOOM_THRESHOLD;
        if (shouldShow === buildingsOnMap) return;
        buildingsOnMap = shouldShow;
        if (shouldShow) {
          if (buildingPolygons.length === 0) {
            buildings.forEach(function(pts) {
              if (pts.length < 3) return;
              var mid = pts[Math.floor(pts.length / 2)];
              var inside = pointInBoundary(mid[0], mid[1]);
              buildingPolygons.push(L.polygon(pts, {
                color: '#6d4c41', weight: 1, fillColor: '#d7ccc8',
                fillOpacity: inside ? 0.55 : 0.25, opacity: inside ? 1 : DIMMED_OPACITY
              }));
            });
          }
          buildingPolygons.forEach(function(p) { p.addTo(map); });
        } else {
          buildingPolygons.forEach(function(p) { map.removeLayer(p); });
        }
      }
      map.on('zoomend', updateBuildingsVisibility);
      updateBuildingsVisibility();
      applyLayerFilter();
      applyZoomScale();
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
