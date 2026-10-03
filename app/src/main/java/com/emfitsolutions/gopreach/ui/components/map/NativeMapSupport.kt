package com.emfitsolutions.gopreach.ui.components.map

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * The native TomTom Maps SDK (`com.tomtom.sdk.maps:map-display-premium`)
 * declares `minSdk 26` and ships native libs only for `arm64-v8a`/`x86_64`
 * with a hard Vulkan 1.0 requirement (see its AAR manifest / developer docs).
 * This app's own `minSdk` stays at 24 (`AndroidManifest.xml`'s
 * `tools:overrideLibrary` is what makes that legal to even compile), so
 * every call site touching a `com.tomtom.sdk.*` class — not just
 * instantiating `MapView`, but importing its types at all in a class that
 * gets loaded — must be gated behind [isSupported] first, and must live in
 * its own file (never this one) so ART never has to verify that class's
 * bytecode against an unsupported API level/ABI/GPU on a device that fails
 * this check.
 */
object NativeMapSupport {
    private const val MIN_SDK = Build.VERSION_CODES.O // 26
    private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

    fun isSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < MIN_SDK) return false
        if (Build.SUPPORTED_ABIS.none { it in SUPPORTED_ABIS }) return false
        return context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_VULKAN_HARDWARE_VERSION,
            0x400000, // VK_API_VERSION_1_0
        )
    }
}
