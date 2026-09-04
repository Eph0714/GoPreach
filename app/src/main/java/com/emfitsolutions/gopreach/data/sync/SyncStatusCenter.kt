package com.emfitsolutions.gopreach.data.sync

import com.emfitsolutions.gopreach.data.local.dao.SyncQueueDao
import com.emfitsolutions.gopreach.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single, app-wide source of truth for "what is the sync system doing right now" —
 * the real-time 🟢/🔴/🟡/⚠️ indicator ([Status]) and the plain-language system
 * messages ("Changes saved locally...", "Synchronization completed successfully.",
 * ...) both come from here, rather than each screen guessing at connectivity +
 * queue state independently.
 *
 * Deliberately depends on [SyncQueueDao] directly (not [OfflineFirestoreRepository])
 * to avoid a circular dependency: [OfflineFirestoreRepository] itself calls into this
 * class (see [onWriteQueued]) to report "a change was just saved locally".
 */
@Singleton
class SyncStatusCenter @Inject constructor(
    private val connectivityObserver: ConnectivityObserver,
    syncQueueDao: SyncQueueDao,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    enum class Status { ONLINE, OFFLINE, SYNCING, SYNC_FAILED }

    private val pendingCount = syncQueueDao.observePendingCount()

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

    init {
        // "Internet connection detected. Synchronizing changes..." — only on a
        // real offline→online transition, and only when there's actually
        // something waiting to go up (an idle device reconnecting with nothing
        // pending has nothing worth announcing).
        var wasOnline: Boolean? = null
        connectivityObserver.observe().onEach { online ->
            val justReconnected = wasOnline == false && online
            wasOnline = online
            if (justReconnected) {
                appScope.launch {
                    if (pendingCount.first() > 0) {
                        _messages.emit("Internet connection detected. Synchronizing changes...")
                    }
                }
            }
        }.launchIn(appScope)
    }

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
     * everything in it succeeded). */
    fun onSyncFinished(uploaded: Int, failed: Int) {
        isSyncing.value = false
        lastSyncFailed.value = failed > 0
        when {
            failed > 0 -> _messages.tryEmit("Synchronization failed. Will retry automatically.")
            uploaded > 0 -> _messages.tryEmit("Synchronization completed successfully.")
            // uploaded == 0 && failed == 0: nothing was pending — a silent no-op,
            // not worth a system message.
        }
    }
}
