package com.emfitsolutions.gopreach.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emfitsolutions.gopreach.BuildConfig
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** MapTiler vector style URL for [styleId] ("hybrid-v4", "outdoor-v4",
 * "basic-v2-dark", "satellite", ...), needing [BuildConfig.MAPTILER_API_KEY].
 * Without a key it falls back to OpenFreeMap's keyless "Liberty" style, so a
 * map is never blank just because a developer key is missing. */
fun maptilerStyleUrl(styleId: String): String =
    if (BuildConfig.MAPTILER_API_KEY.isBlank()) "https://tiles.openfreemap.org/styles/liberty"
    else "https://api.maptiler.com/maps/$styleId/style.json?key=${BuildConfig.MAPTILER_API_KEY}"

/**
 * The one place that embeds a native MapLibre [MapView] in Compose: library
 * init, the MapView lifecycle forwarding it requires, (re)loading the style,
 * and load/fail reporting. Screens add their own sources/layers inside
 * [onStyleReady], which runs after *every* style load — switching style wipes
 * all sources/layers/images, so anything added there is re-added each time.
 *
 * [interactive] = false switches every gesture off (a small preview that must
 * not fight the scroll view around it). [reloadToken] changes force a style
 * reload — what a screen's "Retry" button bumps.
 */
@Composable
fun MapLibreHost(
    styleUrl: String,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    reloadToken: Int = 0,
    onLoadStateChange: (MapLoadState) -> Unit = {},
    onMapClick: (MapLibreMap, LatLng) -> Boolean = { _, _ -> false },
    onMapLongClick: (MapLibreMap, LatLng) -> Boolean = { _, _ -> false },
    onStyleReady: (MapLibreMap, Style) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    remember { MapLibre.getInstance(context) }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val latestOnLoadState = rememberUpdatedState(onLoadStateChange)
    val latestOnMapClick = rememberUpdatedState(onMapClick)
    val latestOnMapLongClick = rememberUpdatedState(onMapLongClick)
    val latestOnStyleReady = rememberUpdatedState(onStyleReady)

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { latestOnLoadState.value(MapLoadState.FAILED) }
            getMapAsync { m ->
                m.uiSettings.isLogoEnabled = false
                if (!interactive) {
                    m.uiSettings.setAllGesturesEnabled(false)
                    m.uiSettings.isCompassEnabled = false
                }
                m.cameraPosition = CameraPosition.Builder().target(LatLng(12.8797, 121.7740)).zoom(5.0).build()
                m.addOnMapClickListener { latLng -> latestOnMapClick.value(m, latLng) }
                m.addOnMapLongClickListener { latLng -> latestOnMapLongClick.value(m, latLng) }
                map = m
            }
        }
    }

    DisposableEffect(lifecycle, mapView) {
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
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(map, styleUrl, reloadToken) {
        val m = map ?: return@LaunchedEffect
        latestOnLoadState.value(MapLoadState.LOADING)
        m.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            latestOnStyleReady.value(m, style)
            latestOnLoadState.value(MapLoadState.LOADED)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Load progress of a map embedded via [MapLibreHost]. */
enum class MapLoadState { LOADING, LOADED, FAILED }
