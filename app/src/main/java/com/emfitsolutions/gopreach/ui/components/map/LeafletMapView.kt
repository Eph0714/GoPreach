package com.emfitsolutions.gopreach.ui.components.map

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import com.emfitsolutions.gopreach.BuildConfig

/**
 * "Enhance the Shared Location Module with... the same design, behavior, and
 * functionality already implemented in the Territory Map Module... Reuse the
 * same proven and working map configuration from the Territory Map Module
 * instead of creating a separate incompatible map implementation." —
 * everything about *embedding a Leaflet WebView reliably* (as opposed to the
 * markers/filters/HTML any one screen puts on top of it, which stay
 * screen-specific) lives here exactly once: the container-ready timing race,
 * the triple invalidateSize() fallback, software layer type, the JS console
 * bridge, and duplicate-init prevention — every one of them a real,
 * previously-hit "the map is just blank" bug on Territory Map, now fixed in
 * one place both screens share instead of two copies that could silently
 * drift apart (or only get the fix on one of them).
 */
enum class MapLoadState { LOADING, LOADED, FAILED }

/** Live handle onto the WebView a [LeafletMapView] is currently showing —
 * lets the caller push further JS calls into the already-loaded page
 * (filtering, panning, adding a "my location" marker, whatever
 * screen-specific `window.xxx` functions its own HTML defines) without ever
 * touching the WebView instance directly. `null` until the WebView exists,
 * and again while a reload is in flight — [evaluateJavascript] silently
 * no-ops rather than throwing during that window, same as calling it before
 * [MapLoadState.LOADED] always could. */
class LeafletMapController {
    internal var webView: WebView? = null

    fun evaluateJavascript(script: String) {
        webView?.evaluateJavascript(script, null)
    }
}

@Composable
fun rememberLeafletMapController(): LeafletMapController = remember { LeafletMapController() }

/**
 * Embeds [html] (a full Leaflet+OpenStreetMap page — see
 * [com.emfitsolutions.gopreach.ui.screens.territories.buildTerritoryMapHtml]
 * for the reference implementation every caller's own HTML builder should
 * follow the same conventions as: expose the `L.map(...)` instance as
 * `window.<mapGlobalVarName>`, expose an `AndroidBridge.showDetails(id)`
 * hookup on every marker's click handler) in a `WebView`, handling every
 * part of making that reliable:
 *
 * - Waits for this composable's own container to report a real, non-zero
 *   size ([containerReady]) before ever calling `loadDataWithBaseURL` —
 *   Leaflet measures its container's pixel size exactly once, the moment
 *   `L.map(...)` runs, and never re-measures on its own; loading the page
 *   into a still-zero-sized container locks in a permanently blank map no
 *   later `invalidateSize()` alone can fix.
 * - Re-measures via `[mapGlobalVarName].invalidateSize()` from three
 *   independent triggers (`onSizeChanged`, `onPageFinished`, and a forced
 *   real height change followed by a delayed `invalidateSize()`) — Chromium's
 *   own internal viewport size sync to the Java-side View bounds has been
 *   observed, on-device, to still miss the very first layout pass even after
 *   all of the above look correct; forcing one further real (if momentary)
 *   height change is the documented fix for that exact WebView-engine quirk.
 * - `LAYER_TYPE_SOFTWARE` — hardware-accelerated WebView layering can render
 *   solid black/blank for embedded interop WebViews on some devices; this is
 *   slightly slower to draw but reliably shows the actual page.
 * - [reloadToken] is the only thing this composable watches to decide
 *   whether to reload the *same* [html] again (a manual Retry tap) — writing
 *   [MapLoadState] from inside the same recomposition that reload runs in
 *   would otherwise trigger another reload, forever, since load state is
 *   itself observed by the loading/error overlay a caller typically renders
 *   around this composable.
 * - [onMarkerClick] is this page's one required JS bridge hookup —
 *   `AndroidBridge.showDetails(id)` — every other page-specific `window.xxx`
 *   function the caller's own HTML defines is reached directly through
 *   [controller] instead, so this composable never needs to know what a
 *   caller's markers/filters/points even look like.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapView(
    html: String,
    mapGlobalVarName: String,
    controller: LeafletMapController,
    onMarkerClick: (id: String) -> Unit,
    onLoadStateChange: (MapLoadState) -> Unit,
    onConsoleMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    reloadToken: Int = 0,
    logTag: String = "LeafletMapView",
) {
    var containerReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(webViewRef) { controller.webView = webViewRef }

    // Same fix as Territory Map's own history: loading inside AndroidView's
    // `update` lambda (which Compose re-runs on *every* recomposition) used
    // to retrigger a reload every time `onLoadStateChange` itself caused a
    // recomposition — visually indistinguishable from "nothing renders." A
    // LaunchedEffect keyed only on what should actually trigger a (re)load
    // fixes that.
    LaunchedEffect(html, reloadToken, webViewRef, containerReady) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!containerReady) return@LaunchedEffect
        onLoadStateChange(MapLoadState.LOADING)
        webView.post {
            // A stable https base URL (rather than null/about:blank) so the
            // CDN script/tile requests aren't treated as mixed content.
            webView.loadDataWithBaseURL("https://gopreach.app/", html, "text/html", "UTF-8", null)
        }
    }

    AndroidView(
        modifier = modifier.onSizeChanged { size ->
            if (size.width > 0 && size.height > 0) {
                containerReady = true
                webViewRef?.evaluateJavascript("if (window.$mapGlobalVarName) { window.$mapGlobalVarName.invalidateSize(); }", null)
            }
        },
        factory = { ctx ->
            // Lets a debug build be inspected live from a PC via
            // chrome://inspect (USB debugging) — the single most direct way
            // to see the *actual* browser-side error when the on-device
            // console bridge below isn't enough on its own.
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Leaflet handles pinch/double-tap zoom itself via touch
                // events — the WebView's own native zoom would otherwise
                // fight Leaflet's for the same gesture.
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                val self = this
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun showDetails(id: String) {
                            self.post { onMarkerClick(id) }
                        }
                    },
                    "AndroidBridge",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadStateChange(MapLoadState.LOADED)
                        view?.evaluateJavascript("if (window.$mapGlobalVarName) { window.$mapGlobalVarName.invalidateSize(); }", null)
                        // Forces one real (if momentary) height change — the
                        // documented fix for the specific WebView-engine
                        // quirk where Chromium's own internal viewport size
                        // never actually received the real size at all, not
                        // just "not yet" — see this composable's own doc
                        // comment.
                        view?.let { wv ->
                            val realHeight = wv.height
                            val lp = wv.layoutParams
                            if (realHeight > 0 && lp != null) {
                                lp.height = realHeight - 1
                                wv.layoutParams = lp
                                wv.requestLayout()
                                wv.post {
                                    lp.height = realHeight
                                    wv.layoutParams = lp
                                    wv.requestLayout()
                                    wv.postDelayed({
                                        wv.evaluateJavascript("if (window.$mapGlobalVarName) { window.$mapGlobalVarName.invalidateSize(); }", null)
                                    }, 50)
                                }
                            }
                        }
                    }
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        // Only the top-level page failing counts as the map
                        // itself failing — a single missed sub-resource (one
                        // map tile timing out, say) shouldn't flip the whole
                        // view into an error state when the map is otherwise
                        // usable.
                        if (request?.isForMainFrame == true) {
                            onLoadStateChange(MapLoadState.FAILED)
                            val detail = "Main frame load error: ${error?.errorCode} ${error?.description} (${request.url})"
                            Log.e(logTag, detail)
                            onConsoleMessage(detail)
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        val line = "${consoleMessage.messageLevel()}: ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                        Log.d(logTag, "WebView console: $line")
                        onConsoleMessage(line)
                        return true
                    }
                }
            }.also { webViewRef = it }
        },
        update = {},
    )
}
