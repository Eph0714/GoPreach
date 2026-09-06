package com.emfitsolutions.gopreach.data.sync

import com.emfitsolutions.gopreach.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single, app-wide source of truth for "what is the sync system doing right now" —
 * the real-time 🟢/🔴/🟡/⚠️ indicator ([Status]) and the plain-language system
 * messages both come from here, rather than each screen guessing at connectivity +
 * queue state independently.
 *
 * "Do not show the system message if there are record[s] automatically
 * syncing" — [messages] only ever fires for a *manual* sync (the explicit
 * "Sync to Server" button, see [SyncWorker.KEY_MANUAL]) or an offline write
 * being queued; every automatic trigger (periodic floor, reconnect,
 * [SyncScheduler.triggerSyncIfOnline]) updates [status] live for anyone
 * watching the badge, but never interrupts with a toast.
 */
@Singleton
class SyncStatusCenter @Inject constructor(
    private val connectivityObserver: ConnectivityObserver,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    enum class Status { ONLINE, OFFLINE, SYNCING, SYNC_FAILED }

    private val isSyncing = MutableStateFlow(false)
    private val lastSyncFailed = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** One-shot system notifications — collected exactly once, app-wide, by
     * [com.emfitsolutions.gopreach.ui.components.SyncMessageHost]. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** The real-time status badge: 🔴 Offline beats everything else (nothing can
     * sync without a connection), then 🟡 Syncing while a flush is actually
     * running, then ⚠️ if the last attempt left changes still unsynced, else
     * 🟢 Online. */
    val status: StateFlow<Status> = combine(
        connectivityObserver.observe(),
        isSyncing,
        lastSyncFailed,
    ) { online, syncing, failed ->
        when {
            !online -> Status.OFFLINE
            syncing -> Status.SYNCING
            failed -> Status.SYNC_FAILED
            else -> Status.ONLINE
        }
    }.stateIn(appScope, SharingStarted.Eagerly, Status.OFFLINE)

    /** Called by [OfflineFirestoreRepository] right after a local write is queued.
     * Only worth announcing while offline — an online write is about to be
     * flushed almost immediately (see [SyncScheduler.triggerSyncIfOnline]), so a
     * "waiting for internet" message would be actively misleading. */
    fun onWriteQueued(deviceIsOnline: Boolean) {
        if (!deviceIsOnline) {
            _messages.tryEmit("Changes saved locally. Waiting for internet connection.")
        }
    }

    /** Called by [SyncWorker] at the start of every flush attempt, manual or
     * automatic. */
    fun onSyncStarted() {
        isSyncing.value = true
    }

    /** Called by [SyncWorker] once a flush attempt finishes (whether or not
     * everything in it succeeded). [isManual] gates the toast — see this
     * class's own doc comment: an automatic run still updates [status] live,
     * it just never emits a message for it. */
    fun onSyncFinished(uploaded: Int, failed: Int, isManual: Boolean) {
        isSyncing.value = false
        lastSyncFailed.value = failed > 0
        if (!isManual) return
        when {
            failed > 0 -> _messages.tryEmit("Synchronization failed. Will retry automatically.")
            uploaded > 0 -> _messages.tryEmit("Synchronization completed successfully.")
            // uploaded == 0 && failed == 0: nothing was pending — a silent no-op,
            // not worth a system message.
        }
    }
}
