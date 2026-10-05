package com.emfitsolutions.gopreach.ui.components.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.emfitsolutions.gopreach.data.export.MapImageExporter
import com.emfitsolutions.gopreach.data.repository.AreaFeature
import com.emfitsolutions.gopreach.data.repository.Landmark
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/** One named boundary to draw — a list since a future caller could overlay
 * more than one, though [BarangayBoundaryDialog][com.emfitsolutions.gopreach
 * .ui.screens.territoryassignments.BarangayBoundaryDialog] only ever passes a
 * single barangay. [colorHex] ("#RRGGBB") overrides [OsmBoundaryMap]'s own
 * `boundaryColorHex` for just this one boundary — e.g. "show every Group's
 * territory at once" draws each barangay in its own assigned Group's color
 * on one shared map, rather than every boundary sharing one color. */
data class NamedBoundary(val name: String, val geometryJson: String, val colorHex: String? = null)

private enum class MapStyle(val label: String) {
    SATELLITE("Satellite"),
    STANDARD("Standard"),
    NIGHT("Night"),
}

/** MapTiler's "Hybrid v4" basemap (satellite imagery with road/label
 * overlay) for "Satellite" — same key as [mapTilerNightSource]. */
private fun mapTilerSatelliteSource(): OnlineTileSourceBase =
    object : OnlineTileSourceBase("MapTilerHybridV4", 0, 19, 256, ".jpg", arrayOf("")) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://api.maptiler.com/maps/hybrid-v4/256/$z/$x/$y.jpg?key=${BuildConfig.MAPTILER_API_KEY}"
        }
    }

/** MapTiler's "Basic Dark" basemap (OpenStreetMap data, MapTiler's own
 * rendering/hosting) for "Night" — needs [BuildConfig.MAPTILER_API_KEY], a
 * free-tier key from a MapTiler account, same per-developer-secret handling
 * as the old TomTom key (see app/build.gradle.kts). */
private fun mapTilerNightSource(): OnlineTileSourceBase =
    object : OnlineTileSourceBase("MapTilerBasicDark", 0, 19, 256, ".png", arrayOf("")) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://api.maptiler.com/maps/basic-v2-dark/256/$z/$x/$y.png?key=${BuildConfig.MAPTILER_API_KEY}"
        }
    }

/** MapTiler's "Outdoor v4" basemap for "Standard" — same key as [mapTilerNightSource]. */
private fun mapTilerStandardSource(): OnlineTileSourceBase =
    object : OnlineTileSourceBase("MapTilerOutdoorV4", 0, 19, 256, ".png", arrayOf("")) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://api.maptiler.com/maps/outdoor-v4/256/$z/$x/$y.png?key=${BuildConfig.MAPTILER_API_KEY}"
        }
    }

/**
 * Native OpenStreetMap preview for Territory Assignment's Barangay Boundary
 * dialog — a real osmdroid `MapView` (no WebView, no JS mapping library),
 * same Satellite/Standard/Night choice the app has always offered, now all
 * three via MapTiler: "Hybrid v4" for Satellite, "Outdoor v4" for Standard
 * (the default style on open), and "Basic Dark" for Night — built from
 * OpenStreetMap data, requiring [BuildConfig.MAPTILER_API_KEY].
 * Roads and buildings are the base map's own tiles, not a separately drawn
 * overlay — only the boundary polygon, named landmarks, and area labels are
 * drawn on top.
 */
@Composable
fun OsmBoundaryMap(
    boundaries: List<NamedBoundary>,
    landmarks: List<Landmark> = emptyList(),
    areas: List<AreaFeature> = emptyList(),
    /** "#RRGGBB" — the assigned Field Service Group's own
     * [com.emfitsolutions.gopreach.ui.components.GroupColorPalette] color, so
     * the boundary reads as "whose territory is this" the same way Group
     * color already does everywhere else in the app. Null (no Group
     * assigned) falls back to a plain neutral blue/yellow. */
    boundaryColorHex: String? = null,
    /** Fires with the tapped [NamedBoundary.name] when a boundary polygon is
     * clicked — e.g. [com.emfitsolutions.gopreach.ui.screens
     * .territoryassignments.GroupTerritoryMapDialog] uses this to drill from
     * "all this Group's territory" down into one barangay's own boundary
     * dialog. `null` (the default) leaves boundaries non-interactive. */
    onBoundaryClick: ((name: String) -> Unit)? = null,
    /** Names the file/share-sheet entry [MapImageExporter] produces when the
     * print/export action is used — e.g. the barangay name, the Group name,
     * or "All Territories". */
    exportTitle: String = "territory_map",
    myLocation: Pair<Double, Double>? = null,
    pickModeEnabled: Boolean = false,
    onDistanceToCenterComputed: (meters: Double) -> Unit = {},
    onPointDistanceComputed: (meters: Double) -> Unit = {},
    onPointNeedsLocation: () -> Unit = {},
    onOpenDirections: (originLat: Double, originLng: Double, destLat: Double, destLng: Double) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // osmdroid refuses to fetch tiles without a user-agent (tile servers
    // reject the default one) and needs a writable cache location — both
    // are process-global, one-time setup.
    remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
        }
    }

    var selectedStyle by remember { mutableStateOf(MapStyle.STANDARD) }
    var selectedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var boundaryCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var hasFitBoundary by remember { mutableStateOf(false) }
    var hasFitMyLocation by remember { mutableStateOf(false) }

    val tileSources = remember {
        mapOf(
            MapStyle.SATELLITE to mapTilerSatelliteSource(),
            MapStyle.STANDARD to mapTilerStandardSource(),
            MapStyle.NIGHT to mapTilerNightSource(),
        )
    }

    val latestPickMode = rememberUpdatedState(pickModeEnabled)
    val latestMyLocation = rememberUpdatedState(myLocation)
    val latestOnPointNeedsLocation = rememberUpdatedState(onPointNeedsLocation)
    val latestOnBoundaryClick = rememberUpdatedState(onBoundaryClick)

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setTileSource(tileSources.getValue(MapStyle.STANDARD))
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(14.0)
            isTilesScaledToDpi = true
            // Bottom of the overlay stack — markers/polygons added after
            // this (always appended, never inserted before it) get first
            // chance at a tap, so tapping a marker never also registers as
            // "pick a new point here".
            overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            if (!latestPickMode.value) return false
                            if (latestMyLocation.value == null) {
                                latestOnPointNeedsLocation.value()
                                return true
                            }
                            selectedPoint = p
                            return true
                        }

                        // "Long press open Google Maps exactly on the pressed
                        // location" — a plain search deep link (not a
                        // package-specific intent) so it still works through
                        // whatever the device resolves it to (Google Maps if
                        // installed, a browser fallback otherwise), same
                        // tolerant approach [BarangayBoundaryDialog]'s own
                        // directions link already takes.
                        override fun longPressHelper(p: GeoPoint): Boolean {
                            val uri = android.net.Uri.parse(
                                "https://www.google.com/maps/search/?api=1&query=${p.latitude},${p.longitude}",
                            )
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                            }
                            return true
                        }
                    },
                ),
            )
        }
    }

    val myLocationMarker = remember { Marker(mapView).apply { setInfoWindow(null) } }
    val myLocationLine = remember {
        Polyline().apply {
            outlinePaint.color = Color.argb(255, 26, 115, 232)
            outlinePaint.strokeWidth = 4f
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(14f, 14f), 0f)
        }
    }
    val pointMarker = remember { Marker(mapView).apply { setInfoWindow(null) } }
    val pointLine = remember {
        Polyline().apply {
            outlinePaint.color = Color.argb(255, 232, 113, 10)
            outlinePaint.strokeWidth = 4f
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(8f, 16f), 0f)
        }
    }
    val dataOverlays = remember { mutableListOf<Overlay>() }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(selectedStyle) {
        mapView.setTileSource(tileSources.getValue(selectedStyle))
        mapView.invalidate()
    }

    // Rebuilds the boundary polygon, area labels, and landmark pins whenever
    // the data changes. Roads and buildings are left to the base map's own
    // tiles (Standard/Night already draw real OSM streets/buildings;
    // Satellite shows the real rooftops in imagery) rather than a separately
    // drawn overlay.
    LaunchedEffect(boundaries, landmarks, areas, boundaryColorHex) {
        val newOverlays = mutableListOf<Overlay>()
        val allRings = boundaries.flatMap { BoundaryGeometry.outerRings(it.geometryJson) }
        val allPoints = mutableListOf<GeoPoint>()
        val baseColor = boundaryColorHex?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: Color.argb(255, 66, 133, 244)

        boundaries.forEach { boundary ->
            val ownColor = boundary.colorHex?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: baseColor
            BoundaryGeometry.outerRings(boundary.geometryJson).forEach { ring ->
                val points = ring.map { (lat, lng) -> GeoPoint(lat, lng) }
                if (points.size >= 3) {
                    allPoints.addAll(points)
                    newOverlays.add(
                        Polygon(mapView).apply {
                            setPoints(points)
                            fillColor = Color.TRANSPARENT
                            strokeColor = Color.argb(240, Color.red(ownColor), Color.green(ownColor), Color.blue(ownColor))
                            strokeWidth = 6f
                            if (onBoundaryClick != null) {
                                setOnClickListener { _, _, _ -> latestOnBoundaryClick.value?.invoke(boundary.name); true }
                            }
                        },
                    )
                }
            }
        }
        boundaryCenter = if (allPoints.isNotEmpty()) {
            val box = BoundingBox.fromGeoPoints(allPoints)
            GeoPoint((box.latNorth + box.latSouth) / 2, (box.lonEast + box.lonWest) / 2)
        } else {
            null
        }

        // What's actually on the ground (rice fields, orchards, forest, ...).
        areas.forEach { area ->
            val inside = BoundaryGeometry.containsPoint(allRings, area.lat, area.lng)
            newOverlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(area.lat, area.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = BitmapDrawable(
                        context.resources,
                        buildTextLabelBitmap(area.name, dimmed = !inside, dark = false, italic = true),
                    )
                    setInfoWindow(null)
                },
            )
        }

        // Real named landmarks.
        landmarks.forEach { landmark ->
            val inside = BoundaryGeometry.containsPoint(allRings, landmark.lat, landmark.lng)
            val built = buildLandmarkPinBitmap(landmark, dimmed = !inside)
            newOverlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(landmark.lat, landmark.lng)
                    setAnchor(built.tipAnchor.x, built.tipAnchor.y)
                    icon = BitmapDrawable(context.resources, built.bitmap)
                    setInfoWindow(null)
                },
            )
        }

        mapView.overlays.removeAll(dataOverlays)
        dataOverlays.clear()
        dataOverlays.addAll(newOverlays)
        mapView.overlays.addAll(newOverlays)
        mapView.invalidate()

        if (!hasFitBoundary && allPoints.isNotEmpty()) {
            hasFitBoundary = true
            mapView.post { mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints), false, 80) }
        }
    }

    // "Add my location, then compare the distance to the selected barangay"
    // — called on every fix of a continuous location subscription (not a
    // one-shot fetch) by the caller, so the marker tracks live.
    LaunchedEffect(myLocation) {
        val fix = myLocation
        if (fix == null) {
            mapView.overlays.remove(myLocationMarker)
            mapView.overlays.remove(myLocationLine)
            mapView.overlays.remove(pointMarker)
            mapView.overlays.remove(pointLine)
            selectedPoint = null
            hasFitMyLocation = false
            mapView.invalidate()
            return@LaunchedEffect
        }
        val point = GeoPoint(fix.first, fix.second)
        myLocationMarker.position = point
        myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        myLocationMarker.icon = BitmapDrawable(context.resources, buildMyLocationDotBitmap())
        if (myLocationMarker !in mapView.overlays) mapView.overlays.add(myLocationMarker)

        val center = boundaryCenter
        if (center != null) {
            myLocationLine.setPoints(listOf(point, center))
            if (myLocationLine !in mapView.overlays) mapView.overlays.add(myLocationLine)
            onDistanceToCenterComputed(point.distanceToAsDouble(center))
        }
        if (!hasFitMyLocation) {
            hasFitMyLocation = true
            val boxPoints = listOfNotNull(point, center)
            if (boxPoints.size >= 2) {
                mapView.post { mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(boxPoints), true, 100) }
            }
        }
        mapView.invalidate()
    }

    // "Let the user select a point then calculate the distance from
    // location" — [selectedPoint] is set from the map-tap handler above
    // while [pickModeEnabled] is on.
    LaunchedEffect(selectedPoint, myLocation) {
        val point = selectedPoint
        val fix = myLocation
        if (point == null || fix == null) {
            mapView.overlays.remove(pointMarker)
            mapView.overlays.remove(pointLine)
            mapView.invalidate()
            return@LaunchedEffect
        }
        val origin = GeoPoint(fix.first, fix.second)
        pointMarker.position = point
        pointMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        pointMarker.icon = BitmapDrawable(context.resources, buildPickedPointBitmap())
        pointMarker.setOnMarkerClickListener { _, _ ->
            onOpenDirections(fix.first, fix.second, point.latitude, point.longitude)
            true
        }
        if (pointMarker !in mapView.overlays) mapView.overlays.add(pointMarker)
        pointLine.setPoints(listOf(origin, point))
        if (pointLine !in mapView.overlays) mapView.overlays.add(pointLine)
        onPointDistanceComputed(origin.distanceToAsDouble(point))
        mapView.invalidate()
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                modifier = Modifier
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
            Box(modifier = Modifier.padding(top = 4.dp)) {
                var exportMenuExpanded by remember { mutableStateOf(false) }
                FilledTonalIconButton(onClick = { exportMenuExpanded = true }) {
                    Icon(Icons.Rounded.Print, contentDescription = "Export map")
                }
                DropdownMenu(expanded = exportMenuExpanded, onDismissRequest = { exportMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Export as Image") },
                        leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                        onClick = {
                            exportMenuExpanded = false
                            MapImageExporter.exportAsImage(context, mapView, exportTitle)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export as PDF") },
                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                        onClick = {
                            exportMenuExpanded = false
                            MapImageExporter.exportAsPdf(context, mapView, exportTitle)
                        },
                    )
                }
            }
        }
    }
}

/** "Make the text outside the selected barangay less opacity" — halves a
 * color's own alpha when [dim] is true, otherwise returns it unchanged. */
private fun dimIf(dim: Boolean, argb: Int): Int {
    if (!dim) return argb
    val alpha = (Color.alpha(argb) * 0.4f).toInt()
    return Color.argb(alpha, Color.red(argb), Color.green(argb), Color.blue(argb))
}

private fun buildMyLocationDotBitmap(): Bitmap {
    val size = 32
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    canvas.drawCircle(center, center, 11f, Paint().apply { color = Color.argb(90, 26, 115, 232); isAntiAlias = true })
    canvas.drawCircle(center, center, 8f, Paint().apply { color = Color.WHITE; isAntiAlias = true })
    canvas.drawCircle(center, center, 6f, Paint().apply { color = Color.argb(255, 26, 115, 232); isAntiAlias = true })
    return bitmap
}

private fun buildPickedPointBitmap(): Bitmap {
    val size = 24
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    canvas.drawCircle(center, center, 8f, Paint().apply { color = Color.WHITE; isAntiAlias = true })
    canvas.drawCircle(center, center, 6f, Paint().apply { color = Color.argb(255, 232, 113, 10); isAntiAlias = true })
    return bitmap
}

/** A plain text label with no pin — street names (dark text, light halo, so
 * it reads sitting on top of the street line itself) and area names (light,
 * italic text with a dark halo, the same soft area-name convention Google
 * Maps uses for parks/farmland/forest). */
private fun buildTextLabelBitmap(text: String, dimmed: Boolean, dark: Boolean, italic: Boolean): Bitmap {
    val textPaint = Paint().apply {
        textSize = if (italic) 15f else 13f
        isAntiAlias = true
        isFakeBoldText = !italic
        this.isUnderlineText = false
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (italic) android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL)
        color = if (dark) Color.rgb(0x20, 0x21, 0x24) else Color.rgb(0xDC, 0xED, 0xC8)
        textAlign = Paint.Align.CENTER
    }
    val haloPaint = Paint(textPaint).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = if (dark) Color.argb(220, 255, 255, 255) else Color.argb(200, 0, 0, 0)
    }
    val alpha = if (dimmed) 110 else 255
    val width = (textPaint.measureText(text) + 8f).toInt().coerceAtLeast(1)
    val height = (textPaint.textSize + 8f).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.saveLayerAlpha(null, alpha)
    val baselineY = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(text, width / 2f, baselineY, haloPaint)
    canvas.drawText(text, width / 2f, baselineY, textPaint)
    canvas.restore()
    return bitmap
}

/** A built landmark pin bitmap plus where its actual pin tip (not the chip
 * hanging below it) sits within it, as a fractional anchor. */
private data class BuiltPin(val bitmap: Bitmap, val tipAnchor: PointF)

/** A modern pin+label bitmap (rounded head + pointed tail, like a Google
 * Maps pin, with a white rounded-rect name chip hanging below it) built
 * fresh per landmark since each one bakes its own name into the image. */
private fun buildLandmarkPinBitmap(landmark: Landmark, dimmed: Boolean = false): BuiltPin {
    val category = landmark.category
    val pinRadius = 15f
    val tailHeight = 9f
    val gap = 5f
    val chipPaddingH = 9f
    val chipPaddingV = 5f

    val textPaint = Paint().apply {
        textSize = 14f
        isAntiAlias = true
        color = Color.rgb(0x20, 0x21, 0x24)
        textAlign = Paint.Align.CENTER
    }
    val textWidth = textPaint.measureText(landmark.name)
    val chipWidth = textWidth + chipPaddingH * 2
    val chipHeight = textPaint.textSize + chipPaddingV * 2

    val width = (maxOf(pinRadius * 2, chipWidth) + 8f).toInt()
    val centerX = width / 2f
    val pinTopY = 4f
    val pinCenterY = pinTopY + pinRadius
    val pinTipY = pinCenterY + pinRadius + tailHeight
    val chipTopY = pinTipY + gap
    val height = (chipTopY + chipHeight + 4f).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val layerAlpha = if (dimmed) 110 else 255
    canvas.saveLayerAlpha(null, layerAlpha)

    val head = Path().apply { addCircle(centerX, pinCenterY, pinRadius, Path.Direction.CW) }
    val tail = Path().apply {
        moveTo(centerX - 7f, pinCenterY + pinRadius - 4f)
        lineTo(centerX, pinTipY)
        lineTo(centerX + 7f, pinCenterY + pinRadius - 4f)
        close()
    }
    val pinShape = Path().apply { op(head, tail, Path.Op.UNION) }

    canvas.drawPath(
        pinShape,
        Paint().apply {
            color = Color.argb(70, 0, 0, 0)
            isAntiAlias = true
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        },
    )
    canvas.drawPath(pinShape, Paint().apply { color = category.colorArgb; isAntiAlias = true })
    canvas.drawPath(
        pinShape,
        Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        },
    )
    val emojiPaint = Paint().apply {
        textSize = pinRadius * 1.1f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    val emojiY = pinCenterY - (emojiPaint.ascent() + emojiPaint.descent()) / 2f
    canvas.drawText(category.emoji, centerX, emojiY, emojiPaint)

    val chipRect = RectF(centerX - chipWidth / 2f, chipTopY, centerX + chipWidth / 2f, chipTopY + chipHeight)
    val chipRadius = chipHeight / 2f
    canvas.drawRoundRect(
        chipRect,
        chipRadius,
        chipRadius,
        Paint().apply {
            color = Color.argb(60, 0, 0, 0)
            isAntiAlias = true
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        },
    )
    canvas.drawRoundRect(chipRect, chipRadius, chipRadius, Paint().apply { color = Color.WHITE; isAntiAlias = true })
    val textY = chipTopY + chipHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(landmark.name, centerX, textY, textPaint)
    canvas.restore()

    return BuiltPin(bitmap, PointF(0.5f, pinTipY / height))
}
