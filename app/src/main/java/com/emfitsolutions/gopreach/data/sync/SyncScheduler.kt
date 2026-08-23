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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val UNIQUE_WORK_NAME = "gopreach_sync_queue"
    }

    /** Enqueues a flush of the pending-operations queue and returns that specific
     * request's id. Per spec §17 "Manual Sync Requirement," this is called
     * **only** by an explicit user action — the "SYNC TO SERVER" button, the
     * header [com.emfitsolutions.gopreach.ui.components.SyncStatusButton]
     * shortcut, or a manual pull-to-refresh — never automatically off the back
     * of a write or a network-reconnect event. Still carries a network
     * constraint so a request made while offline simply waits rather than
     * failing outright; [SyncToServerButton] checks connectivity itself
     * beforehand precisely so it can show the "No Network Connection" message
     * instead of silently queuing a run for whenever connectivity returns.
     *
     * [ExistingWorkPolicy.REPLACE], not `APPEND_OR_REPLACE` — the latter chains
     * every run under the same unique work name, so old completed/cancelled
     * requests can keep sitting alongside a new one under that name. REPLACE
     * cancels/discards whatever was there first. Still, a caller must observe
     * *this specific request's* id (see [observeWorkInfo]) — not "whatever the
     * unique work name currently returns" — since [WorkInfo.generation] does
     * **not** distinguish between separate `REPLACE`d requests the way an
     * earlier version of this code assumed (it only increments for a periodic
     * work request updated in place); picking "the highest generation" from
     * the unique-work list was still ambiguous and could still resolve to a
     * stale request. Tracking the request's own id sidesteps that ambiguity
     * entirely — this is the actual fix for "Sync to Server" getting
     * permanently stuck on "Checking for pending changes...". */
    fun requestSyncNow(): UUID {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    /** The live state of exactly the request [requestSyncNow] returned the id
     * for — unambiguous regardless of whatever else has ever run under
     * [UNIQUE_WORK_NAME]. [WorkInfo.progress] carries the in-progress upload
     * counters, [WorkInfo.state] the run/finished status (see
     * [SyncWorker.KEY_FINISHED]). */
    fun observeWorkInfo(id: UUID): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id)
}
