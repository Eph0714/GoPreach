package com.emfitsolutions.gopreach.ui.components.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.data.repository.Landmark
import com.emfitsolutions.gopreach.data.repository.StreetSegment
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.location.GeoBounds
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraOptionsFactory
import com.tomtom.sdk.map.display.camera.InitialCameraOptions
import com.tomtom.sdk.map.display.common.WidthByZoom
import com.tomtom.sdk.map.display.image.ImageFactory
import com.tomtom.sdk.map.display.marker.Label
import com.tomtom.sdk.map.display.marker.MarkerOptions
import com.tomtom.sdk.map.display.polygon.InnerPolygonOptions
import com.tomtom.sdk.map.display.polygon.PolygonOverlayOptions
import com.tomtom.sdk.map.display.polyline.PolylineOptions
import com.tomtom.sdk.map.display.style.LoadingStyleFailure
import com.tomtom.sdk.map.display.style.StandardStyles
import com.tomtom.sdk.map.display.style.StyleDescriptor
import com.tomtom.sdk.map.display.style.StyleLoadingCallback
import com.tomtom.sdk.map.display.style.StyleMode
import com.tomtom.sdk.map.display.ui.MapView

/** One named boundary to draw — [name] is only used as the polygon's tag so
 * [TomTomBoundaryMap] can clear and redraw cleanly when the selection
 * changes, not shown as a label on the map itself. */
data class NamedBoundary(val name: String, val geometryJson: String)

/** Satellite/Standard/Night — the same three choices the Leaflet fallback's
 * own layer control already offered, mapped onto the native SDK's two
 * orthogonal knobs: a base [StyleDescriptor] (vector "Standard"/BROWSING vs
 * raster "Satellite" imagery) and a [StyleMode] (MAIN/DARK — "Night" is
 * Standard's own dark variant, not a separate descriptor, matching how a
 * single style's darkUri already works). */
private enum class MapStyle(val label: String, val descriptor: StyleDescriptor, val mode: StyleMode) {
    SATELLITE("Satellite", StandardStyles.SATELLITE, StyleMode.MAIN),
    STANDARD("Standard", StandardStyles.BROWSING, StyleMode.MAIN),
    NIGHT("Night", StandardStyles.BROWSING, StyleMode.DARK),
}

/**
 * Native TomTom map preview for Territory Assignment — draws every selected
 * barangay's real boundary and fits the camera to all of them, with a
 * Satellite/Standard/Night picker matching the Leaflet fallback's own layer
 * control. Caller must have already checked [NativeMapSupport.isSupported];
 * this file is the one place in the app allowed to import `com.tomtom.sdk.*`
 * map classes for exactly that reason (see [NativeMapSupport]'s doc comment).
 */
@Composable
fun TomTomBoundaryMap(
    boundaries: List<NamedBoundary>,
    landmarks: List<Landmark> = emptyList(),
    streets: List<StreetSegment> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Satellite first — confirmed on-device that Standard/BROWSING's vector
    // road+building data has little to no coverage for rural provinces this
    // app actually serves (a flat blank canvas), whereas satellite imagery
    // shows real ground detail everywhere.
    var selectedStyle by remember { mutableStateOf(MapStyle.SATELLITE) }

    // MapOptions' plain mapKey constructor resolves its tile data provider
    // through the SDK's own global context — without this having run first,
    // MapView.onCreate throws "No valid data provider configured" (confirmed
    // on-device; TomTom's docs never state this requirement outright). Must
    // run before the MapView below is even constructed, and only once per
    // process — a second initialize() call throws.
    remember {
        if (!TomTomSdk.isInitialized) {
            TomTomSdk.initialize(
                context.applicationContext,
                // The overloads taking a telemetryUserConsent lambda need a
                // com.tomtom.sdk.telemetry.UserConsent/Consent value — both
                // have a private constructor and internal-only Companion
                // factories in 2.6.2, so no app can actually construct one;
                // this no-consent-parameter overload is the only one that
                // compiles for a third party, deprecated-for-removal-after-
                // 2026-10-07 warning notwithstanding.
                buildSdkConfiguration(
                    context.applicationContext,
                    BuildConfig.TOMTOM_API_KEY,
                ),
            )
        }
    }

    val mapView = remember {
        MapView(
            context,
            MapOptions(
                mapKey = BuildConfig.TOMTOM_API_KEY,
                // Arbitrary starting view (Manila) — overwritten the moment
                // boundaries load and the camera fits their real bounds below.
                initialCameraOptions = InitialCameraOptions.LocationBased(
                    position = GeoPoint(14.5995, 120.9842),
                    zoom = 5.0,
                ),
            ),
        )
    }
    var tomTomMap by remember { mutableStateOf<TomTomMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    // MarkerOptions.pinImage has no default (confirmed: compiling without it
    // fails with "No value passed for parameter 'pinImage'") and this app
    // bundles no map-pin drawable — a small solid dot, drawn once and reused
    // for every landmark, needs neither.
    val landmarkPinImage = remember {
        val size = 28
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawCircle(size / 2f, size / 2f, size / 2f - 2f, Paint().apply { color = Color.argb(255, 25, 118, 210); isAntiAlias = true })
            drawCircle(size / 2f, size / 2f, size / 2f - 2f, Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true })
        }
        ImageFactory.fromBitmap(bitmap)
    }

    // MapView.onCreate must only ever run once per instance; subsequent
    // lifecycle events are forwarded for as long as this composable stays in
    // the tree, same shape as every other Compose/View lifecycle bridge.
    DisposableEffect(lifecycle, mapView) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        val pending = mapView.getMapAsync { map -> tomTomMap = map }
        onDispose {
            pending.cancel()
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Confirmed on-device: adding polygons right after getMapAsync (i.e.
    // before the chosen style has actually finished loading) makes them
    // vanish once the style/imagery catches up. Explicitly (re)loading the
    // style and only drawing boundaries in its onSuccess callback avoids
    // that race instead of guessing a delay — reruns on every picker change.
    LaunchedEffect(tomTomMap, selectedStyle) {
        val map = tomTomMap ?: return@LaunchedEffect
        isStyleReady = false
        map.loadStyle(
            selectedStyle.descriptor,
            object : StyleLoadingCallback {
                override fun onSuccess() {
                    map.setStyleMode(selectedStyle.mode)
                    isStyleReady = true
                }
                override fun onFailure(failure: LoadingStyleFailure) {
                    // Degrade to "draw the boundary anyway" rather than leave
                    // the preview permanently empty — same graceful-miss
                    // spirit as every other boundary lookup in this app.
                    isStyleReady = true
                }
            },
        )
    }

    LaunchedEffect(tomTomMap, isStyleReady, boundaries, streets) {
        val map = tomTomMap ?: return@LaunchedEffect
        if (!isStyleReady) return@LaunchedEffect
        // A plain PolygonController.addPolygon got silently painted over by
        // satellite raster tiles once they finished loading (confirmed
        // on-device — the polygon worked fine under a blank/no-data style
        // but vanished under real imagery). PolygonOverlayController is a
        // genuinely separate compositing layer that always sits above the
        // base map regardless of style, at the cost of no stroke/outline
        // option of its own — the dim-outside/clear-inside edge it produces
        // reads as the boundary line instead.
        map.removePolygonOverlays()
        map.removePolylines(BOUNDARY_LINE_TAG)
        map.removePolylines(STREET_LINE_TAG)
        val allPoints = mutableListOf<GeoPoint>()
        boundaries.forEach { boundary ->
            BoundaryGeometry.outerRings(boundary.geometryJson).forEach { ring ->
                val points = ring.map { (lat, lng) -> GeoPoint(lat, lng) }
                if (points.size >= 3) {
                    allPoints.addAll(points)
                    map.addPolygonOverlay(
                        PolygonOverlayOptions(
                            outerColor = Color.argb(140, 0, 0, 0),
                            innerPolygonOptions = InnerPolygonOptions(
                                coordinates = points,
                                fillColor = Color.TRANSPARENT,
                                innerPolygonOptions = null,
                            ),
                        ),
                    )
                    // The overlay above has no stroke option of its own (see
                    // this effect's earlier doc comment) — an explicit red
                    // polyline around the same ring, closed back to its own
                    // first point, is the actual drawn boundary line.
                    map.addPolyline(
                        PolylineOptions(
                            coordinates = points + points.first(),
                            lineColor = Color.argb(255, 211, 47, 47),
                            lineWidths = listOf(WidthByZoom(3.0)),
                            tag = BOUNDARY_LINE_TAG,
                        ),
                    )
                }
            }
        }
        map.removeMarkers(LANDMARK_TAG)
        landmarks.forEach { landmark ->
            map.addMarker(
                MarkerOptions(
                    coordinate = GeoPoint(landmark.lat, landmark.lng),
                    pinImage = landmarkPinImage,
                    label = Label(text = landmark.name),
                    tag = LANDMARK_TAG,
                ),
            )
        }
        // Real street lines, drawn independently of whatever TomTom's own
        // map data does or doesn't have for this area — see
        // OverpassLandmarkRepository's own doc comment.
        streets.forEach { street ->
            val points = street.points.map { (lat, lng) -> GeoPoint(lat, lng) }
            if (points.size >= 2) {
                map.addPolyline(
                    PolylineOptions(
                        coordinates = points,
                        lineColor = Color.argb(220, 255, 255, 255),
                        lineWidths = listOf(WidthByZoom(2.0)),
                        tag = STREET_LINE_TAG,
                    ),
                )
            }
        }
        // CameraOptions' own bounds constructor param is internal to the SDK
        // (an unstable, TomTom-internal-only API per its own annotation) —
        // CameraOptionsFactory.lookAt is the public equivalent: fits a
        // GeoBounds (plain list of points, no need to compute a min/max
        // box ourselves) with a sensible default padding/zoom.
        if (allPoints.isNotEmpty()) {
            map.moveCamera(CameraOptionsFactory.lookAt(GeoBounds(allPoints)))
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(4.dp),
        ) {
            MapStyle.entries.forEach { style ->
                FilterChip(
                    selected = style == selectedStyle,
                    onClick = { selectedStyle = style },
                    label = { Text(style.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}

private const val BOUNDARY_LINE_TAG = "territory_boundary_line"
private const val LANDMARK_TAG = "territory_landmark"
private const val STREET_LINE_TAG = "territory_street_line"
