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

    /** Enqueues a flush of the pending-operations queue; a no-op while offline until
     * connectivity returns, since the request carries a network constraint. Every
     * local write (auto-sync, unchanged) and the manual "SYNC TO SERVER" button both
     * call this same method — [observeWorkInfo] is what lets the button's UI tell the
     * two apart and show progress/a summary only for the run it triggered. */
    fun requestSyncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** The unique sync work's live state — [WorkInfo.progress] carries the
     * in-progress upload counters, [WorkInfo.state] the run/finished status. Used by
     * the manual "SYNC TO SERVER" button to show progress and a completion summary
     * for a run it just triggered (see [SyncWorker.KEY_FINISHED]). */
    fun observeWorkInfo(): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .map { it.firstOrNull() }
}
