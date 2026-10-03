package com.emfitsolutions.gopreach.ui.components.map

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emfitsolutions.gopreach.BuildConfig
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.location.GeoBounds
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraOptionsFactory
import com.tomtom.sdk.map.display.camera.InitialCameraOptions
import com.tomtom.sdk.map.display.polygon.PolygonOptions
import com.tomtom.sdk.map.display.ui.MapView

/** One named boundary to draw — [name] is only used as the polygon's tag so
 * [TomTomBoundaryMap] can clear and redraw cleanly when the selection
 * changes, not shown as a label on the map itself. */
data class NamedBoundary(val name: String, val geometryJson: String)

/**
 * Native TomTom map preview for Territory Assignment — draws every selected
 * barangay's real boundary as a polygon and fits the camera to all of them.
 * Caller must have already checked [NativeMapSupport.isSupported]; this file
 * is the one place in the app allowed to import `com.tomtom.sdk.*` map
 * classes for exactly that reason (see [NativeMapSupport]'s doc comment).
 */
@Composable
fun TomTomBoundaryMap(
    boundaries: List<NamedBoundary>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

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

    LaunchedEffect(tomTomMap, boundaries) {
        val map = tomTomMap ?: return@LaunchedEffect
        map.removePolygons(POLYGON_TAG)
        val allPoints = mutableListOf<GeoPoint>()
        boundaries.forEach { boundary ->
            BoundaryGeometry.outerRings(boundary.geometryJson).forEach { ring ->
                val points = ring.map { (lat, lng) -> GeoPoint(lat, lng) }
                if (points.size >= 3) {
                    allPoints.addAll(points)
                    map.addPolygon(
                        PolygonOptions(
                            coordinates = points,
                            outlineColor = Color.argb(255, 211, 47, 47),
                            outlineWidth = 3.0,
                            fillColor = Color.argb(20, 211, 47, 47),
                            tag = POLYGON_TAG,
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

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val POLYGON_TAG = "territory_boundary"
