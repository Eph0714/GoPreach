package com.emfitsolutions.gopreach.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.emfitsolutions.gopreach.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single, app-wide "does this device currently have real internet" state —
 * "GoPreach — Fix Online/Offline Status and Sync Indicator" spec §3/§10: every
 * screen/component that needs this observes [online] (or calls [isOnline] for a
 * one-off point-in-time check), instead of each one registering its own
 * [ConnectivityManager.NetworkCallback]. Before this, [observe] was a cold
 * [Flow] — every collector ([SyncStatusCenter], [SyncScheduler], `HomeViewModel`,
 * `UpdateViewModel`, ...) independently registered its own callback, exactly the
 * "duplicate connectivity listeners" that spec calls out to avoid.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope appScope: CoroutineScope,
) {
    // Bug fix history, in order:
    // 1. Originally required NET_CAPABILITY_VALIDATED (Android's own "actually
    //    reached the internet" probe) alongside NET_CAPABILITY_INTERNET.
    // 2. That was removed ("it says offline even if there is an internet") because
    //    the validation probe can lag several seconds behind a network actually
    //    coming up, and on some networks (certain corporate/school Wi-Fi, some
    //    VPNs, DNS-filtering setups) it never succeeds at all even though this
    //    app's own traffic (Firebase) gets through fine — so NET_CAPABILITY_INTERNET
    //    alone (the network's own claim that it provides internet access) was used
    //    instead, accepting a small false-"online" risk to avoid a bigger
    //    false-"offline" one.
    // 3. Restored here ("Fix Online/Offline Status and Sync Indicator" spec §3/§8:
    //    "Do not show Online merely because Wi-Fi/mobile data is turned on... only
    //    when Android reports the active network has validated internet
    //    connectivity") — this is now a deliberate, explicit product decision to
    //    accept trade-off #2's opposite risk instead: a real "connected to Wi-Fi
    //    with no internet" must read Offline, even on the rare network where
    //    validation itself is unreliable. Nothing about SyncWorker's own retry
    //    behavior depends on this being perfectly accurate either way — a network
    //    that claims to be online but isn't still fails at the Firestore/HTTP layer
    //    itself, which SyncWorker already retries through.
    private fun currentlyOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** The one [ConnectivityManager.NetworkCallback] for the whole app. Requesting
     * NET_CAPABILITY_VALIDATED as part of the request itself (not just checked
     * afterwards in [currentlyOnline]) means the system calls [onLost] the moment
     * a still-connected network stops being validated (e.g. the Wi-Fi router loses
     * its own upstream internet) — not only on an actual disconnect — and
     * [onAvailable] again the moment it revalidates, so "connected but no
     * internet" and "internet restored" both surface immediately and automatically
     * per spec §4, with no polling. */
    private val rawUpdates: Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = trySend(currentlyOnline()).let {}
            override fun onLost(network: Network) = trySend(currentlyOnline()).let {}
            override fun onUnavailable() = trySend(currentlyOnline()).let {}
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                trySend(currentlyOnline()).let {}
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        cm.registerNetworkCallback(request, callback)
        trySend(currentlyOnline())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** Shared, hot state — every observer sees the exact same value at the exact
     * same time, and there is exactly one underlying [ConnectivityManager
     * .NetworkCallback] registration for the app's whole lifetime ([SharingStarted
     * .Eagerly]: connectivity matters even while nothing is actively collecting,
     * e.g. between screens, so this isn't torn down and re-registered per
     * subscriber like [SharingStarted.WhileSubscribed] would). */
    val online: StateFlow<Boolean> = rawUpdates.stateIn(appScope, SharingStarted.Eagerly, currentlyOnline())

    /** Point-in-time check for a plain (non-Flow) call site — e.g. "is it even
     * worth trying to sync right now." Reads the same cached state [online]
     * holds, so this can never disagree with what [observe] is telling anyone
     * else at the same moment. */
    fun isOnline(): Boolean = online.value

    fun observe(): Flow<Boolean> = online
}
