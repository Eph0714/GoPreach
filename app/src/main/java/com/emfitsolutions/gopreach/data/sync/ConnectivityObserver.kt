package com.emfitsolutions.gopreach.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConnectivityObserver"

/**
 * The app's one source of truth for "does this device currently have real,
 * usable internet." Deliberately simple, on purpose:
 *
 * - [isOnline] is a fresh, uncached query against Android's own
 *   [ConnectivityManager] every single time it's called — never a cached or
 *   shared value that could go stale.
 * - [observe] registers a fresh [ConnectivityManager.NetworkCallback] for
 *   *each collector*, torn down via [awaitClose] when that collector's own
 *   scope ends, rather than one shared callback for the whole app.
 *
 * History, for whoever touches this next: an earlier version centralized
 * this into a single `@Singleton`-held hot `StateFlow`, shared app-wide via
 * `SharingStarted.Eagerly` on an IO-dispatcher application scope —
 * specifically to avoid every consumer (SyncStatusCenter, SyncScheduler,
 * HomeViewModel, UpdateViewModel, ...) registering its own callback. That
 * version is what broke real-time updates in practice: reported as "still
 * shows Online with no internet" and "sync still runs while Offline," on a
 * real device, after being verified to compile and reasoned through
 * carefully. A follow-up fix (passing an explicit main-Looper `Handler` to
 * `registerNetworkCallback`, on the theory that callback delivery was
 * silently failing on the IO-dispatcher thread that registered it) did NOT
 * resolve it either — meaning the actual failure mode was never fully
 * confirmed, only theorized. Rather than keep layering fixes onto an
 * architecture that demonstrably doesn't work as designed, this reverts to
 * the plain, previously-working shape: no shared/cached state at all, so
 * there is nothing that can freeze or go stale. The trade-off (one
 * NetworkCallback registration per active collector instead of one for the
 * whole app) is a negligible cost next to actually being correct.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun connectivityManager() =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** True only when the active network both claims internet access AND
     * Android has actually validated it (reached the internet, not just
     * "has a route to some network") — "do not show Online merely because
     * Wi-Fi/mobile data is turned on." Never cached: this re-queries
     * [ConnectivityManager] fresh every single call, so it can never
     * disagree with reality at the exact moment it's asked — this is what
     * every mandatory-connectivity-gate call site (SyncWorker, the manual
     * "Sync to Server" pre-flight check, SyncScheduler.triggerSyncIfOnline)
     * actually relies on. */
    fun isOnline(): Boolean {
        val cm = connectivityManager()
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Real-time updates for a UI badge or an automatic-sync trigger — a
     * fresh registration per collector (see this class's own doc comment for
     * why), explicitly delivered on the main thread's Looper so callback
     * delivery never depends on which thread happened to start collecting
     * (a `SyncScheduler`-style collector on an application-wide IO-dispatcher
     * scope, and a ViewModel's own `viewModelScope`, both need to work). */
    fun observe(): Flow<Boolean> = callbackFlow {
        val cm = connectivityManager()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "onAvailable — online=${isOnline()}")
                trySend(isOnline())
            }
            override fun onLost(network: Network) {
                Log.d(TAG, "onLost — online=${isOnline()}")
                trySend(isOnline())
            }
            override fun onUnavailable() {
                Log.d(TAG, "onUnavailable")
                trySend(isOnline())
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(isOnline())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        cm.registerNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
        trySend(isOnline())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
