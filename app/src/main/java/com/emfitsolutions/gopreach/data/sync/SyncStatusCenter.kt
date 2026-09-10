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
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single, app-wide source of truth for "what is the sync system doing right now" —
 * the real-time indicator ([Snapshot]) and the plain-language system
 * messages both come from here, rather than each screen guessing at connectivity +
 * queue state independently.
 *
 * "Do not show the system message if there are record[s] automatically
 * syncing" — [messages] only ever fires for a *manual* sync (the explicit
 * "Sync to Server" button, see [SyncWorker.KEY_MANUAL]) or an offline write
 * being queued; every automatic trigger (periodic floor, reconnect,
 * [SyncScheduler.triggerSyncIfOnline]) updates [snapshot] live for anyone
 * watching the badge, but never interrupts with a toast.
 */
@Singleton
class SyncStatusCenter @Inject constructor(
    private val connectivityObserver: ConnectivityObserver,
    private val syncQueueDao: SyncQueueDao,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    /** Bug fix ("Sync Failed" shown forever, even long after reconnecting) —
     * the old two-state failure model ([SYNC_FAILED] only) couldn't tell a
     * transient failure the system is *already* quietly retrying apart from
     * one that genuinely needs attention, so both looked identical and
     * permanent to the Publisher. Now:
     * - [WAITING_FOR_INTERNET] — offline, with changes still queued; nothing
     *   is "failing", there's simply no connection to sync over yet.
     * - [RETRYING] — a temporary network/server failure just happened and
     *   the system is already retrying automatically (see
     *   [com.emfitsolutions.gopreach.data.sync.SyncWorker]/[SyncScheduler]) —
     *   never a dead end the Publisher has to do anything about.
     * - [SYNC_ERROR] — a genuinely permanent failure (bad data, a rules
     *   rejection, ...) that retrying can never fix; the one state that
     *   actually needs a human to look at it.
     */
    enum class Status { SYNCED, SYNCING, WAITING_FOR_INTERNET, RETRYING, SYNC_ERROR }

    private val isSyncing = MutableStateFlow(false)
    private val lastSyncFailed = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** One-shot system notifications — collected exactly once, app-wide, by
     * [com.emfitsolutions.gopreach.ui.components.SyncMessageHost]. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** [pendingCount] — genuinely retryable changes still waiting (spec:
     * "N changes waiting to sync"); [permanentFailureCount] — rows
     * [SyncWorker] gave up retrying for good (spec: "keep the failed
     * operation for investigation... show a meaningful synchronization
     * error"), never silently dropped. */
    data class Snapshot(val status: Status, val pendingCount: Int, val permanentFailureCount: Int)

    /** The real-time status badge. Precedence: a permanent error always needs
     * attention regardless of anything else going on ([SYNC_ERROR]); then
     * [SYNCING] while a flush is actively running; then offline with
     * something still queued ([WAITING_FOR_INTERNET] — never shown as a
     * failure, since nothing has actually failed, there's simply no network
     * yet); then a just-happened temporary failure still being retried
     * ([RETRYING]); otherwise [SYNCED] (which the UI renders as "✓ All
     * changes synchronized" when [Snapshot.pendingCount] is 0, or "N changes
     * waiting to sync" when a fresh write is queued but hasn't been attempted
     * yet). */
    val snapshot: StateFlow<Snapshot> = combine(
        connectivityObserver.observe(),
        isSyncing,
        lastSyncFailed,
        syncQueueDao.observePendingCount(),
        syncQueueDao.observePermanentFailureCount(),
    ) { online, syncing, failed, pending, permanentFailures ->
        val status = when {
            permanentFailures > 0 -> Status.SYNC_ERROR
            syncing -> Status.SYNCING
            !online && pending > 0 -> Status.WAITING_FOR_INTERNET
            failed && pending > 0 -> Status.RETRYING
            else -> Status.SYNCED
        }
        Snapshot(status, pending, permanentFailures)
    }.stateIn(appScope, SharingStarted.Eagerly, Snapshot(Status.WAITING_FOR_INTERNET, 0, 0))

    /** Called by [OfflineFirestoreRepository] right after a local write is queued.
     * Only worth announcing while offline — an online write is about to be
     * flushed almost immediately (see [SyncScheduler.triggerSyncIfOnline]), so a
     * "waiting for internet" message would be actively misleading. Spec §6's
     * exact wording. */
    fun onWriteQueued(deviceIsOnline: Boolean) {
        if (!deviceIsOnline) {
            _messages.tryEmit("Offline — Changes saved locally and will sync automatically when internet is available.")
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
     * it just never emits a message for it. [failed] is transient/retryable
     * failures only — a permanent failure never makes this stay `true`
     * forever (see [Snapshot.permanentFailureCount] for that, surfaced
     * separately as [Status.SYNC_ERROR]). */
    fun onSyncFinished(uploaded: Int, failed: Int, isManual: Boolean) {
        isSyncing.value = false
        lastSyncFailed.value = failed > 0
        if (!isManual) return
        when {
            failed > 0 -> _messages.tryEmit("Sync temporarily unavailable — retrying automatically.")
            uploaded > 0 -> _messages.tryEmit("✓ Sync completed successfully.")
            // uploaded == 0 && failed == 0: nothing was pending — a silent no-op,
            // not worth a system message.
        }
    }
}
