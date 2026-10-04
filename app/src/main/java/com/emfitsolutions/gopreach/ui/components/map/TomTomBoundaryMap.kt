package com.emfitsolutions.gopreach.ui.components.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.emfitsolutions.gopreach.data.repository.LandmarkCategory
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
import com.tomtom.sdk.map.display.polyline.CapType
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
    // fails with "No value passed for parameter 'pinImage'") — one teardrop
    // pin bitmap per [LandmarkCategory], built once and reused for every
    // landmark of that kind, so a school pin actually looks different from a
    // police station pin instead of every POI being an identical dot.
    val categoryPinImages = remember {
        LandmarkCategory.entries.associateWith { category -> ImageFactory.fromBitmap(buildPinBitmap(category)) }
    }
    // Street name labels need a Label but have nothing to pin — MarkerOptions
    // has no "label only, no icon" constructor, so a 1x1 fully transparent
    // bitmap stands in for the icon it otherwise requires.
    val transparentPinImage = remember {
        ImageFactory.fromBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
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
    // "Make the map text and icons responsive if zoom in and out" — the
    // SDK's own built-in per-zoom marker scaling, set once per map instance
    // rather than something this file would otherwise have to fake with a
    // camera listener: landmark/street-label markers shrink smoothly toward
    // zoom 10 (so a zoomed-out overview isn't cluttered with full-size pins)
    // and grow back to full size by zoom 16, fading in/out over a narrower
    // band so they don't just pop abruptly into view.
    LaunchedEffect(tomTomMap) {
        val map = tomTomMap ?: return@LaunchedEffect
        map.isMarkersShrinkingEnabled = true
        map.markersShrinkingRange = 10..16
        map.isMarkersFadingEnabled = true
        map.markersFadingRange = 11..13
    }

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

    LaunchedEffect(tomTomMap, isStyleReady, boundaries, streets, landmarks) {
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
        map.removeMarkers(STREET_LABEL_TAG)
        val allPoints = mutableListOf<GeoPoint>()
        boundaries.forEach { boundary ->
            BoundaryGeometry.outerRings(boundary.geometryJson).forEach { ring ->
                val points = ring.map { (lat, lng) -> GeoPoint(lat, lng) }
                if (points.size >= 3) {
                    allPoints.addAll(points)
                    // A light territory tint inside, a softer dim outside —
                    // this overlay has no stroke of its own (see this
                    // effect's earlier doc comment), the actual boundary
                    // line is the halo'd polyline drawn right after it.
                    map.addPolygonOverlay(
                        PolygonOverlayOptions(
                            outerColor = Color.argb(110, 0, 0, 0),
                            innerPolygonOptions = InnerPolygonOptions(
                                coordinates = points,
                                fillColor = Color.argb(26, 211, 47, 47),
                                innerPolygonOptions = null,
                            ),
                        ),
                    )
                    // A white halo under a red core line — the same
                    // casing-plus-fill technique real cartography uses for a
                    // line that needs to read clearly over any basemap
                    // (satellite, light, or dark), rounded joints instead of
                    // the hard mitered corners a single thin line left
                    // looking hand-drawn.
                    map.addPolyline(
                        PolylineOptions(
                            coordinates = points + points.first(),
                            lineColor = Color.argb(255, 211, 47, 47),
                            lineWidths = BOUNDARY_LINE_WIDTHS,
                            outlineColor = Color.argb(235, 255, 255, 255),
                            outlineWidths = BOUNDARY_OUTLINE_WIDTHS,
                            lineStartCapType = CapType.Round,
                            lineEndCapType = CapType.Round,
                            tag = BOUNDARY_LINE_TAG,
                        ),
                    )
                }
            }
        }
        map.removeMarkers(LANDMARK_TAG)
        landmarks.forEach { landmark ->
            val pin = categoryPinImages[landmark.category] ?: categoryPinImages.getValue(LandmarkCategory.OTHER)
            map.addMarker(
                MarkerOptions(
                    coordinate = GeoPoint(landmark.lat, landmark.lng),
                    pinImage = pin,
                    label = Label(
                        text = landmark.name,
                        textColor = Color.WHITE,
                        textSize = 13.0,
                        outlineColor = Color.argb(220, 0, 0, 0),
                        outlineWidth = 2.0,
                    ),
                    tag = LANDMARK_TAG,
                ),
            )
        }
        // Real street lines, drawn independently of whatever TomTom's own
        // map data does or doesn't have for this area — see
        // OverpassLandmarkRepository's own doc comment. A dark casing under
        // a light fill (the same technique the boundary line above uses) is
        // how real road cartography reads as an actual street rather than a
        // bare line, and a named road gets its own label at its midpoint —
        // the same "no pin, just text with a halo" treatment Google/Apple
        // Maps use for street names.
        streets.forEach { street ->
            val points = street.points.map { (lat, lng) -> GeoPoint(lat, lng) }
            if (points.size >= 2) {
                map.addPolyline(
                    PolylineOptions(
                        coordinates = points,
                        lineColor = Color.argb(235, 255, 255, 255),
                        lineWidths = STREET_LINE_WIDTHS,
                        outlineColor = Color.argb(160, 55, 55, 55),
                        outlineWidths = STREET_OUTLINE_WIDTHS,
                        lineStartCapType = CapType.Round,
                        lineEndCapType = CapType.Round,
                        tag = STREET_LINE_TAG,
                    ),
                )
                val streetName = street.name
                if (!streetName.isNullOrBlank()) {
                    val mid = points[points.size / 2]
                    map.addMarker(
                        MarkerOptions(
                            coordinate = mid,
                            pinImage = transparentPinImage,
                            label = Label(
                                text = streetName,
                                textColor = Color.argb(255, 235, 235, 235),
                                textSize = 11.0,
                                outlineColor = Color.argb(220, 0, 0, 0),
                                outlineWidth = 2.0,
                            ),
                            tag = STREET_LABEL_TAG,
                        ),
                    )
                }
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
private const val STREET_LABEL_TAG = "territory_street_label"

/** Multiple zoom stops (the SDK interpolates width between them) instead of
 * one flat width — "responsive to zoom in/out": thin and unobtrusive at a
 * whole-barangay overview, thick and easy to tap/read once zoomed into one
 * street. */
private val BOUNDARY_LINE_WIDTHS = listOf(WidthByZoom(width = 2.0, zoom = 10.0), WidthByZoom(width = 4.0, zoom = 14.0), WidthByZoom(width = 6.0, zoom = 18.0))
private val BOUNDARY_OUTLINE_WIDTHS = listOf(WidthByZoom(width = 3.5, zoom = 10.0), WidthByZoom(width = 7.0, zoom = 14.0), WidthByZoom(width = 10.0, zoom = 18.0))
private val STREET_LINE_WIDTHS = listOf(WidthByZoom(width = 1.0, zoom = 10.0), WidthByZoom(width = 2.0, zoom = 14.0), WidthByZoom(width = 3.5, zoom = 18.0))
private val STREET_OUTLINE_WIDTHS = listOf(WidthByZoom(width = 2.0, zoom = 10.0), WidthByZoom(width = 3.5, zoom = 14.0), WidthByZoom(width = 5.5, zoom = 18.0))

/** A modern map-pin bitmap (rounded head + pointed tail, like a Google Maps
 * pin) filled with [category]'s own color and centered with its emoji —
 * drawn once per category and cached by the caller, rather than the single
 * identical plain-dot marker every landmark used to get regardless of what
 * kind of place it actually was. */
private fun buildPinBitmap(category: LandmarkCategory): Bitmap {
    val width = 72
    val height = 92
    val radius = 28f
    val centerX = width / 2f
    val centerY = radius + 6f
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val head = Path().apply { addCircle(centerX, centerY, radius, Path.Direction.CW) }
    val tail = Path().apply {
        moveTo(centerX - 13f, centerY + radius - 7f)
        lineTo(centerX, height - 4f)
        lineTo(centerX + 13f, centerY + radius - 7f)
        close()
    }
    val pinShape = Path().apply { op(head, tail, Path.Op.UNION) }

    canvas.drawPath(
        pinShape,
        Paint().apply {
            color = Color.argb(70, 0, 0, 0)
            isAntiAlias = true
            maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
        },
    )
    canvas.drawPath(pinShape, Paint().apply { color = category.colorArgb; isAntiAlias = true })
    canvas.drawPath(
        pinShape,
        Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        },
    )
    val textPaint = Paint().apply {
        textSize = radius * 1.15f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    val textY = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(category.emoji, centerX, textY, textPaint)
    return bitmap
}
