package com.emfitsolutions.gopreach.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val UNIQUE_WORK_NAME = "gopreach_sync_queue"
    }

    /** Enqueues a flush of the pending-operations queue. Per spec §17 "Manual Sync
     * Requirement," this is called **only** by an explicit user action — the
     * "SYNC TO SERVER" button, the header [com.emfitsolutions.gopreach.ui.components
     * .SyncStatusButton] shortcut, or a manual pull-to-refresh — never automatically
     * off the back of a write or a network-reconnect event. Still carries a network
     * constraint so a request made while offline simply waits rather than failing
     * outright; [SyncToServerButton] checks connectivity itself beforehand precisely
     * so it can show the "No Network Connection" message instead of silently queuing
     * a run for whenever connectivity happens to return later.
     *
     * [ExistingWorkPolicy.REPLACE], not `APPEND_OR_REPLACE` — this used to chain every
     * run under the same unique work name, so `getWorkInfosForUniqueWork*` could keep
     * returning more than one [WorkInfo] (old completed runs alongside the new one).
     * [observeWorkInfo] only ever wants "the current run," and REPLACE is what makes
     * that reliably true: it cancels/discards whatever was there and enqueues a single
     * fresh request, so there's never more than one entry to pick from. Without this,
     * a stale already-SUCCEEDED WorkInfo from a past run could be what
     * `.firstOrNull()` returned, which never reports `SyncWorker.KEY_FINISHED` for
     * *this* attempt — the observed real symptom was the "Sync to Server" button
     * getting permanently stuck on "Checking for pending changes...". */
    fun requestSyncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** The unique sync work's live state — [WorkInfo.progress] carries the
     * in-progress upload counters, [WorkInfo.state] the run/finished status. Used by
     * the manual "SYNC TO SERVER" button to show progress and a completion summary
     * for a run it just triggered (see [SyncWorker.KEY_FINISHED]). With [REPLACE]
     * above, this list should only ever contain the one current/most recent request,
     * but sorts by [WorkInfo.id] descending defensively — WorkManager's own recency —
     * rather than trusting list order, in case of any transient overlap during the
     * cancel-and-replace handoff. */
    fun observeWorkInfo(): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .map { infos -> infos.maxByOrNull { it.generation } }
}
