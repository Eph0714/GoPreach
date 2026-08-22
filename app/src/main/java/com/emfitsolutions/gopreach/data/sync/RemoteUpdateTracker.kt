package com.emfitsolutions.gopreach.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether any collection has received a live change from Firestore
 * since the user last acknowledged it — the "Update available" banner on the
 * dashboards reads this. The app's data is already always current (every
 * collection has a live listener via [RemoteSyncCoordinator]), so this isn't
 * gating a fetch; it's a visible "something changed elsewhere — tap to
 * acknowledge" signal for users who want that confirmation rather than
 * trusting the background sync silently.
 */
@Singleton
class RemoteUpdateTracker @Inject constructor() {
    private val _hasUpdates = MutableStateFlow(false)
    val hasUpdates: StateFlow<Boolean> = _hasUpdates

    fun markUpdated() {
        _hasUpdates.value = true
    }

    fun acknowledge() {
        _hasUpdates.value = false
    }
}
