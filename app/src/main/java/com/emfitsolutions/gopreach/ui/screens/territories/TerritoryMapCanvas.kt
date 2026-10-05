package com.emfitsolutions.gopreach.ui.screens.territories

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.emfitsolutions.gopreach.data.model.MapPin
import com.emfitsolutions.gopreach.ui.components.map.BoundaryGeometry
import com.emfitsolutions.gopreach.ui.components.map.MapLibreHost
import com.emfitsolutions.gopreach.ui.components.map.MapLoadState
import com.emfitsolutions.gopreach.ui.components.map.maptilerStyleUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

enum class TerritoryBasemap(val label: String, val styleId: String) {
    STANDARD("Standard", "outdoor-v4"),
    SATELLITE("Satellite", "hybrid-v4"),
    NIGHT("Night", "basic-v2-dark"),
}

/** A record plus its distance from the user's current location (null when the
 * location is unknown). */
data class RecordWithDistance(val record: LocationRecord, val meters: Double?)

private const val SRC_AREAS = "tm-areas-src"
private const val LYR_AREA_FILL = "tm-area-fill"
private const val LYR_AREA_LINE = "tm-area-line"
private const val SRC_AREA_LABELS = "tm-area-labels-src"
private const val LYR_AREA_LABELS = "tm-area-labels"
private const val SRC_RECORDS = "tm-records-src"
private const val LYR_RECORDS = "tm-records"
private const val LYR_RECORD_LABELS = "tm-record-labels"
private const val SRC_PINS = "tm-pins-src"
private const val LYR_PINS = "tm-pins"
private const val LYR_PIN_LABELS = "tm-pin-labels"
private const val IMG_NOTE_PIN = "tm-img-note-pin"
private const val SRC_ME = "tm-me-src"
private const val LYR_PULSE = "tm-me-pulse"
private const val LYR_ME_RING = "tm-me-ring"
private const val LYR_ME_DOT = "tm-me-dot"
private const val LYR_ME_LABEL = "tm-me-label"
private const val SRC_FOCUS = "tm-focus-src"
private const val LYR_FOCUS = "tm-focus"
private const val IMG_FOCUS = "tm-img-focus"
private val FONT = arrayOf("Noto Sans Regular")
private val FONT_BOLD = arrayOf("Noto Sans Bold")

private fun recordImage(type: RecordType, selected: Boolean, ringHex: String? = null) =
    "tm-img-${type.name}${if (selected) "-sel" else ""}${ringHex?.let { "-" + it.removePrefix("#") } ?: ""}"

/** A teardrop pin whose tip is the bottom-centre of the bitmap (so the map's
 * BOTTOM icon anchor puts the tip on the coordinate), coloured per record type
 * and carrying that type's emoji; the selected variant is larger with a gold ring. */
private fun buildPin(type: RecordType, selected: Boolean, density: Float, ringHex: String? = null) =
    drawPin(type.colorHex, type.emoji, selected, density, ringHex)

private fun drawPin(colorHex: String, emoji: String, selected: Boolean, density: Float, ringHex: String? = null): Bitmap {
    val scale = if (selected) 1.25f else 1f
    val w = (34f * scale * density).toInt()
    val h = (44f * scale * density).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = w / 2f - 2f * density
    val cx = w / 2f
    val cy = r + 2f * density
    val shape = Path().apply {
        addCircle(cx, cy, r, Path.Direction.CW)
        val tail = Path().apply {
            moveTo(cx - r * 0.62f, cy + r * 0.78f)
            lineTo(cx, h - 1f * density)
            lineTo(cx + r * 0.62f, cy + r * 0.78f)
            close()
        }
        op(tail, Path.Op.UNION)
    }
    c.drawPath(shape, Paint().apply { color = Color.argb(60, 0, 0, 0); isAntiAlias = true; setShadowLayer(3f * density, 0f, 1f * density, Color.argb(90, 0, 0, 0)) })
    c.drawPath(shape, Paint().apply { color = Color.parseColor(colorHex); isAntiAlias = true })
    c.drawPath(
        shape,
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = (if (selected || ringHex != null) 3.5f else 2.5f) * density
            // Selected: gold. In "Show All FS Groups" the ring is the record's FS Group color; otherwise white.
            color = if (selected) Color.rgb(0xFF, 0xD6, 0x00) else (ringHex?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: Color.WHITE)
            isAntiAlias = true
        },
    )
    val emojiPaint = Paint().apply { textSize = r * 1.05f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    c.drawText(emoji, cx, cy - (emojiPaint.ascent() + emojiPaint.descent()) / 2f, emojiPaint)
    return bmp
}

private fun buildFocusPin(density: Float): Bitmap {
    val size = (26f * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawCircle(size / 2f, size / 2f, size / 2f, Paint().apply { color = Color.WHITE; isAntiAlias = true })
    c.drawCircle(size / 2f, size / 2f, size / 2f - 3f * density, Paint().apply { color = Color.parseColor("#D93025"); isAntiAlias = true })
    return bmp
}

private fun pointOf(lat: Double, lng: Double) = Point.fromLngLat(lng, lat)

/**
 * The Territory Map's MapLibre layer stack: territory boundaries (subtle fill
 * + line, the selected one bolder) with name labels, distinct per-type record
 * markers, the user's pulsing current-location indicator, and an optional
 * focus pin (Share Location's "open in Territory Map"). Owns no business
 * data — everything is passed in; camera moves are driven by tokens.
 */
@Composable
fun TerritoryMapCanvas(
    areas: List<TerritoryArea>,
    boundaryJson: Map<String, String>,
    /** area id -> "#RRGGBB": each territory is drawn in its own FS Group's color code. */
    areaColors: Map<String, String>,
    /** group id -> "#RRGGBB" (marker ring color in "Show All FS Groups"). */
    groupColors: Map<String, String>,
    /** "Show All FS Groups": territories are labelled with their group and markers ringed in its color. */
    showAllGroups: Boolean,
    records: List<RecordWithDistance>,
    /** Text pins dropped by long-press ("Create a Pin"), saved online. */
    pins: List<MapPin>,
    onPinTap: (String) -> Unit,
    selectedAreaId: String?,
    selectedRecordId: String?,
    myLocation: Pair<Double, Double>?,
    focus: Triple<Double, Double, String>?,
    basemap: TerritoryBasemap,
    reloadToken: Int,
    /** Changes whenever the camera should re-fit to the current territories/records. */
    fitKey: String,
    fitReady: Boolean,
    recenterToken: Int,
    flyToRecord: LocationRecord?,
    onRecordTap: (String) -> Unit,
    onAreaTap: (String) -> Unit,
    /** Long-press on the map — the exact (lat, lng) pressed. */
    onLongPress: (lat: Double, lng: Double) -> Unit,
    onLoadStateChange: (MapLoadState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleVersion by remember { mutableIntStateOf(0) }
    var lastFitKey by remember { mutableStateOf<String?>(null) }
    var focusConsumed by remember { mutableStateOf(false) }

    val latestOnRecordTap = rememberUpdatedState(onRecordTap)
    val latestOnAreaTap = rememberUpdatedState(onAreaTap)
    val latestOnLongPress = rememberUpdatedState(onLongPress)
    val latestOnPinTap = rememberUpdatedState(onPinTap)

    MapLibreHost(
        styleUrl = maptilerStyleUrl(basemap.styleId),
        reloadToken = reloadToken,
        onLoadStateChange = onLoadStateChange,
        onMapClick = { m, latLng ->
            val screen = m.projection.toScreenLocation(latLng)
            val recordId = m.queryRenderedFeatures(screen, LYR_RECORDS)
                .firstNotNullOfOrNull { if (it.hasProperty("id")) it.getStringProperty("id") else null }
            val pinId = if (recordId != null) null else m.queryRenderedFeatures(screen, LYR_PINS)
                .firstNotNullOfOrNull { if (it.hasProperty("id")) it.getStringProperty("id") else null }
            if (recordId != null) {
                latestOnRecordTap.value(recordId)
                true
            } else if (pinId != null) {
                latestOnPinTap.value(pinId)
                true
            } else {
                val areaId = m.queryRenderedFeatures(screen, LYR_AREA_FILL)
                    .firstNotNullOfOrNull { if (it.hasProperty("id")) it.getStringProperty("id") else null }
                if (areaId != null) {
                    latestOnAreaTap.value(areaId)
                    true
                } else {
                    false
                }
            }
        },
        onMapLongClick = { _, latLng ->
            latestOnLongPress.value(latLng.latitude, latLng.longitude)
            true
        },
        onStyleReady = { m, style ->
            RecordType.entries.forEach { t ->
                style.addImage(recordImage(t, false), buildPin(t, false, density))
                style.addImage(recordImage(t, true), buildPin(t, true, density))
            }
            style.addImage(IMG_FOCUS, buildFocusPin(density))

            style.addSource(GeoJsonSource(SRC_AREAS, FeatureCollection.fromFeatures(emptyList<Feature>())))
            style.addLayer(
                FillLayer(LYR_AREA_FILL, SRC_AREAS).withProperties(
                    PropertyFactory.fillColor(Expression.get("color")),
                    PropertyFactory.fillOpacity(
                        Expression.switchCase(
                            Expression.eq(Expression.get("sel"), Expression.literal("1")),
                            Expression.literal(0.22f),
                            Expression.literal(0.08f),
                        ),
                    ),
                ),
            )
            style.addLayer(
                LineLayer(LYR_AREA_LINE, SRC_AREAS).withProperties(
                    PropertyFactory.lineColor(Expression.get("color")),
                    PropertyFactory.lineWidth(
                        Expression.switchCase(
                            Expression.eq(Expression.get("sel"), Expression.literal("1")),
                            Expression.literal(5f),
                            Expression.literal(2.5f),
                        ),
                    ),
                    PropertyFactory.lineOpacity(0.95f),
                ),
            )
            style.addSource(GeoJsonSource(SRC_AREA_LABELS, FeatureCollection.fromFeatures(emptyList<Feature>())))
            style.addLayer(
                SymbolLayer(LYR_AREA_LABELS, SRC_AREA_LABELS).withProperties(
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textFont(FONT_BOLD),
                    PropertyFactory.textSize(13f),
                    PropertyFactory.textColor("#202124"),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.8f),
                    PropertyFactory.textAllowOverlap(false),
                ).also { it.minZoom = 10f },
            )

            style.addSource(GeoJsonSource(SRC_RECORDS, FeatureCollection.fromFeatures(emptyList<Feature>())))
            style.addLayer(
                SymbolLayer(LYR_RECORDS, SRC_RECORDS).withProperties(
                    PropertyFactory.iconImage(Expression.get("img")),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.symbolSortKey(Expression.get("order")),
                ),
            )
            style.addLayer(
                SymbolLayer(LYR_RECORD_LABELS, SRC_RECORDS).withProperties(
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textFont(FONT),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textOffset(arrayOf(0f, 0.3f)),
                    PropertyFactory.textColor("#202124"),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.6f),
                    PropertyFactory.textOptional(true),
                ).also { it.minZoom = 14.5f },
            )

            style.addImage(IMG_NOTE_PIN, drawPin("#D93025", "📌", false, density))
            style.addSource(GeoJsonSource(SRC_PINS, FeatureCollection.fromFeatures(emptyList<Feature>())))
            style.addLayer(
                SymbolLayer(LYR_PINS, SRC_PINS).withProperties(
                    PropertyFactory.iconImage(IMG_NOTE_PIN),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ),
            )
            // The text mark is always visible next to the pin.
            style.addLayer(
                SymbolLayer(LYR_PIN_LABELS, SRC_PINS).withProperties(
                    PropertyFactory.textField(Expression.get("text")),
                    PropertyFactory.textFont(FONT_BOLD),
                    PropertyFactory.textSize(12.5f),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textOffset(arrayOf(0f, 0.25f)),
                    PropertyFactory.textMaxWidth(10f),
                    PropertyFactory.textColor("#B3261E"),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textAllowOverlap(true),
                ),
            )
            style.addSource(GeoJsonSource(SRC_ME, FeatureCollection.fromFeatures(emptyList<Feature>())))
            style.addLayer(
                CircleLayer(LYR_PULSE, SRC_ME).withProperties(
                    PropertyFactory.circleColor("#1A73E8"),
                    PropertyFactory.circleRadius(10f),
                    PropertyFactory.circleOpacity(0.3f),
                    PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
                ),
            )
            style.addLayer(
                CircleLayer(LYR_ME_RING, SRC_ME).withProperties(
                    PropertyFactory.circleColor("#FFFFFF"),
                    PropertyFactory.circleRadius(9.5f),
                ),
            )
            style.addLayer(
                CircleLayer(LYR_ME_DOT, SRC_ME).withProperties(
                    PropertyFactory.circleColor("#1A73E8"),
                    PropertyFactory.circleRadius(6.5f),
                ),
            )
            style.addLayer(
                SymbolLayer(LYR_ME_LABEL, SRC_ME).withProperties(
                    PropertyFactory.textField("You Are Here"),
                    PropertyFactory.textFont(FONT_BOLD),
                    PropertyFactory.textSize(12.5f),
                    PropertyFactory.textColor("#1A73E8"),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true),
                ),
            )
            style.addSource(GeoJsonSource(SRC_FOCUS, FeatureCollection.fromFeatures(emptyList<Feature>())))
            style.addLayer(
                SymbolLayer(LYR_FOCUS, SRC_FOCUS).withProperties(
                    PropertyFactory.iconImage(IMG_FOCUS),
                    PropertyFactory.iconAllowOverlap(true),
                ),
            )
            map = m
            styleVersion++
        },
        modifier = modifier,
    )

    // Territory boundaries + labels.
    LaunchedEffect(map, styleVersion, areas, boundaryJson, selectedAreaId, areaColors, showAllGroups) {
        val m = map ?: return@LaunchedEffect
        val style = m.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        val features = mutableListOf<Feature>()
        val labels = mutableListOf<Feature>()
        areas.forEach { area ->
            val json = boundaryJson[area.id] ?: return@forEach
            val rings = BoundaryGeometry.outerRings(json).filter { it.size >= 3 }
            if (rings.isEmpty()) return@forEach
            val color = areaColors[area.id] ?: "#7E57C2"
            val sel = if (area.id == selectedAreaId) "1" else "0"
            rings.forEach { ring ->
                val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
                features += Feature.fromGeometry(Polygon.fromLngLats(listOf(closed.map { (lat, lng) -> Point.fromLngLat(lng, lat) }))).also {
                    it.addStringProperty("id", area.id)
                    it.addStringProperty("color", color)
                    it.addStringProperty("sel", sel)
                }
            }
            val all = rings.flatten()
            val centerLat = (all.minOf { it.first } + all.maxOf { it.first }) / 2
            val centerLng = (all.minOf { it.second } + all.maxOf { it.second }) / 2
            labels += Feature.fromGeometry(pointOf(centerLat, centerLng)).also { it.addStringProperty("name", if (showAllGroups) area.barangay + "\n" + area.groupName else area.barangay) }
        }
        style.getSourceAs<GeoJsonSource>(SRC_AREAS)?.setGeoJson(FeatureCollection.fromFeatures(features))
        style.getSourceAs<GeoJsonSource>(SRC_AREA_LABELS)?.setGeoJson(FeatureCollection.fromFeatures(labels))
    }

    // Record markers (selected one drawn last / largest).
    LaunchedEffect(map, styleVersion, records, selectedRecordId, groupColors, showAllGroups) {
        val m = map ?: return@LaunchedEffect
        val style = m.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        val features = records.map { rd ->
            val r = rd.record
            val selected = r.id == selectedRecordId
            val ring = if (showAllGroups) r.groupId?.let { groupColors[it] } else null
            val imageId = recordImage(r.type, selected, ring)
            if (style.getImage(imageId) == null) style.addImage(imageId, buildPin(r.type, selected, density, ring))
            Feature.fromGeometry(pointOf(r.lat, r.lng)).also {
                it.addStringProperty("id", r.id)
                it.addStringProperty("name", r.name)
                it.addStringProperty("img", imageId)
                it.addNumberProperty("order", if (selected) 1 else 0)
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_RECORDS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Text pins.
    LaunchedEffect(map, styleVersion, pins) {
        val m = map ?: return@LaunchedEffect
        val style = m.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        val features = pins.map { p ->
            Feature.fromGeometry(pointOf(p.lat, p.lng)).also {
                it.addStringProperty("id", p.id)
                it.addStringProperty("text", p.text)
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_PINS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Current-location dot; the pulse ring is animated below.
    LaunchedEffect(map, styleVersion, myLocation) {
        val m = map ?: return@LaunchedEffect
        val style = m.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_ME) ?: return@LaunchedEffect
        if (myLocation == null) src.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        else src.setGeoJson(Feature.fromGeometry(pointOf(myLocation.first, myLocation.second)))
    }

    // Subtle expanding/fading ring around "my location" — only runs while the
    // screen is RESUMED and a location is known, so nothing animates in the
    // background or after leaving the module.
    val hasLocation = myLocation != null
    LaunchedEffect(map, styleVersion, hasLocation) {
        if (!hasLocation) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val start = SystemClock.uptimeMillis()
            while (isActive) {
                val layer = m.style?.getLayerAs<CircleLayer>(LYR_PULSE)
                if (layer != null) {
                    val t = ((SystemClock.uptimeMillis() - start) % 1800L) / 1800f
                    layer.setProperties(
                        PropertyFactory.circleRadius(9f + 28f * t),
                        PropertyFactory.circleOpacity(0.38f * (1f - t)),
                    )
                }
                delay(60)
            }
        }
    }

    // Focus pin (Share Location's "open in Territory Map").
    LaunchedEffect(map, styleVersion, focus) {
        val m = map ?: return@LaunchedEffect
        val style = m.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_FOCUS) ?: return@LaunchedEffect
        if (focus == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        } else {
            src.setGeoJson(Feature.fromGeometry(pointOf(focus.first, focus.second)))
            if (!focusConsumed) {
                focusConsumed = true
                lastFitKey = fitKey // the focus takes priority over the first auto-fit
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focus.first, focus.second), 16.0))
            }
        }
    }

    // Fit to the selected group's territories/records once per selection/filter.
    LaunchedEffect(map, styleVersion, fitKey, fitReady) {
        val m = map ?: return@LaunchedEffect
        if (!fitReady || lastFitKey == fitKey || m.style?.isFullyLoaded != true) return@LaunchedEffect
        val points = mutableListOf<LatLng>()
        areas.forEach { area ->
            boundaryJson[area.id]?.let { json ->
                BoundaryGeometry.outerRings(json).forEach { ring -> ring.forEach { points += LatLng(it.first, it.second) } }
            }
        }
        records.forEach { points += LatLng(it.record.lat, it.record.lng) }
        lastFitKey = fitKey
        when {
            points.size >= 2 -> m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 120), 500)
            points.size == 1 -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(points[0], 15.0), 500)
            myLocation != null -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(myLocation.first, myLocation.second), 14.0), 500)
        }
    }

    // "My location" button.
    LaunchedEffect(recenterToken) {
        val m = map ?: return@LaunchedEffect
        val me = myLocation ?: return@LaunchedEffect
        if (recenterToken > 0) m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(me.first, me.second), 16.0), 500)
    }

    // Selecting a record from the list/panel flies the map to it.
    LaunchedEffect(flyToRecord?.id) {
        val m = map ?: return@LaunchedEffect
        val r = flyToRecord ?: return@LaunchedEffect
        val zoom = maxOf(m.cameraPosition.zoom, 15.0)
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(r.lat, r.lng), zoom), 500)
    }
}
