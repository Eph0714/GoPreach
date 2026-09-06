package com.emfitsolutions.gopreach.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports whether the device currently has usable internet — the trigger for
 * SyncWorker to flush the pending-operations queue (spec §6.5).
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Bug fix ("it says offline even if there is an internet"): this used to
    // also require NET_CAPABILITY_VALIDATED — Android's own "actually
    // reached the internet, not just claims to have a route to it" check,
    // done by probing a Google connectivity-check URL in the background.
    // That probe can lag several seconds behind a network actually coming
    // up, and on some networks (certain corporate/school Wi-Fi, some VPNs,
    // DNS-filtering setups) it never succeeds at all even though this app's
    // own traffic (Firebase) gets through completely fine — either way,
    // this device would report itself offline despite having working
    // internet for everything that actually matters here. NET_CAPABILITY_INTERNET
    // alone (the network's own claim that it provides internet access) is
    // what most apps key off for exactly this reason; an actually-dead
    // network still fails at the Firestore/HTTP layer itself, which this
    // app already retries through (see SyncWorker), so nothing about
    // "eventually consistent" is lost by trusting it here.
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun observe(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = trySend(isOnline()).let {}
            override fun onLost(network: Network) = trySend(isOnline()).let {}
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                trySend(isOnline()).let {}
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        trySend(isOnline())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
