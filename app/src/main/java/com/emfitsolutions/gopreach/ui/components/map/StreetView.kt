package com.emfitsolutions.gopreach.ui.components.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.emfitsolutions.gopreach.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One Mapillary street-level photo near a tapped spot. */
data class MapillaryImage(
    val id: String,
    val lat: Double,
    val lng: Double,
    val capturedAt: Long?,
    /** Compass direction the camera faced, degrees clockwise from north. */
    val compassAngle: Double?,
    val thumbUrl: String,
    val distanceMeters: Double,
)

/**
 * "Street View" for the MapLibre maps, from Mapillary's free crowd-sourced
 * street-level photos (Google's Street View can't be used outside Google's own
 * maps). Needs a Mapillary client token — `mapillaryToken=MLY|...` in
 * local.properties (free, from mapillary.com/dashboard/developers). Coverage
 * outside main roads is patchy, so the UI always has a "no photos here" path.
 */
object Mapillary {
    val isConfigured: Boolean get() = BuildConfig.MAPILLARY_TOKEN.isNotBlank()

    private const val SRC = "mly-coverage-src"
    private const val LYR_SEQUENCES = "mly-coverage-sequences"
    private const val LYR_IMAGES = "mly-coverage-images"
    private const val GREEN = "#05CB63"

    /** Shows Mapillary's coverage (green lines where photos exist, dots when zoomed in),
     * drawn beneath [belowLayerId] when that layer exists. Safe to call repeatedly. */
    fun addCoverage(style: Style, belowLayerId: String? = null) {
        if (!isConfigured || style.getSource(SRC) != null) return
        val tiles = TileSet("2.2.0", "https://tiles.mapillary.com/maps/vtp/mly1_public/2/{z}/{x}/{y}?access_token=" + BuildConfig.MAPILLARY_TOKEN).apply {
            minZoom = 0f
            maxZoom = 14f
        }
        style.addSource(VectorSource(SRC, tiles))
        val sequences = LineLayer(LYR_SEQUENCES, SRC).apply { sourceLayer = "sequence"; minZoom = 6f }.withProperties(
            PropertyFactory.lineColor(GREEN),
            PropertyFactory.lineWidth(2.5f),
            PropertyFactory.lineOpacity(0.85f),
        )
        val images = CircleLayer(LYR_IMAGES, SRC).apply { sourceLayer = "image"; minZoom = 16f }.withProperties(
            PropertyFactory.circleColor(GREEN),
            PropertyFactory.circleRadius(4f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(1f),
        )
        val below = belowLayerId?.takeIf { style.getLayer(it) != null }
        if (below != null) {
            style.addLayerBelow(sequences, below)
            style.addLayerBelow(images, below)
        } else {
            style.addLayer(sequences)
            style.addLayer(images)
        }
    }

    fun removeCoverage(style: Style) {
        style.removeLayer(LYR_IMAGES)
        style.removeLayer(LYR_SEQUENCES)
        style.removeSource(SRC)
    }

    /** Photos within roughly 50 m of (lat, lng) — widening to ~150 m if none — nearest first.
     * Empty on no coverage, no network, or any error. */
    suspend fun imagesNear(lat: Double, lng: Double): List<MapillaryImage> = withContext(Dispatchers.IO) {
        for (radiusDegrees in listOf(0.00045, 0.0014)) {
            val found = runCatching { query(lat, lng, radiusDegrees) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) return@withContext found
        }
        emptyList()
    }

    private fun query(lat: Double, lng: Double, r: Double): List<MapillaryImage> {
        val bbox = "${lng - r},${lat - r},${lng + r},${lat + r}"
        val url = "https://graph.mapillary.com/images?access_token=" + URLEncoder.encode(BuildConfig.MAPILLARY_TOKEN, "UTF-8") +
            "&fields=id,captured_at,compass_angle,thumb_2048_url,computed_geometry&limit=40&bbox=" + bbox
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (connection.responseCode != 200) return emptyList()
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: return emptyList()
            val out = mutableListOf<MapillaryImage>()
            for (i in 0 until data.length()) {
                val o = data.getJSONObject(i)
                val thumb = o.optString("thumb_2048_url")
                val coords = o.optJSONObject("computed_geometry")?.optJSONArray("coordinates")
                if (thumb.isBlank() || coords == null) continue
                val iLng = coords.getDouble(0)
                val iLat = coords.getDouble(1)
                out += MapillaryImage(
                    id = o.getString("id"),
                    lat = iLat,
                    lng = iLng,
                    capturedAt = if (o.has("captured_at")) o.optLong("captured_at") else null,
                    compassAngle = if (o.has("compass_angle")) o.optDouble("compass_angle") else null,
                    thumbUrl = thumb,
                    distanceMeters = haversine(lat, lng, iLat, iLng),
                )
            }
            return out.sortedBy { it.distanceMeters }
        } finally {
            connection.disconnect()
        }
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** Shown when Street View is switched on without a Mapillary token configured. */
@Composable
fun StreetViewSetupDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Street View isn't set up yet") },
        text = {
            Text(
                "Street View uses Mapillary's free street-level photos and needs a Mapillary access token. " +
                    "Create a free token at mapillary.com/dashboard/developers and add it to local.properties as " +
                    "mapillaryToken=MLY|..., then rebuild the app.",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Full-screen viewer for the street-level photos found at a tapped spot: nearest first, with
 * Previous / Next through the others and a button to open the photo in Mapillary itself. */
@Composable
fun StreetViewDialog(images: List<MapillaryImage>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var index by remember(images) { mutableIntStateOf(0) }
    val image = images[index.coerceIn(0, images.lastIndex)]
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close Street View", tint = Color.White) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Street View", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        "Photo " + (index + 1) + " of " + images.size + " · " + image.distanceMeters.toInt() + " m from the spot you tapped",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = image.thumbUrl,
                    contentDescription = "Street-level photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } },
                    error = {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Couldn't load this photo. Check your internet connection.", color = Color.White)
                        }
                    },
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val details = listOfNotNull(
                    image.capturedAt?.let { "Taken " + DateFormat.getDateInstance().format(Date(it)) },
                    image.compassAngle?.let { "Facing " + it.toInt() + "°" },
                    "Photo: Mapillary contributors",
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        enabled = index > 0,
                        onClick = { index-- },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) { Text("Previous") }
                    OutlinedButton(
                        enabled = index < images.lastIndex,
                        onClick = { index++ },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) { Text("Next") }
                }
                Button(
                    onClick = {
                        val uri = Uri.parse("https://www.mapillary.com/app/?pKey=" + image.id + "&focus=photo")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Open in Mapillary (full 360° view)") }
            }
        }
    }
}
