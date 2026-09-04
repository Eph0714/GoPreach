package com.emfitsolutions.gopreach.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.emfitsolutions.gopreach.data.local.PendingSyncOperationEntity
import com.emfitsolutions.gopreach.data.local.dao.CacheDao
import com.emfitsolutions.gopreach.data.local.dao.SyncQueueDao
import com.emfitsolutions.gopreach.data.model.SyncOperationType
import com.emfitsolutions.gopreach.data.model.SyncState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

/**
 * Flushes [PendingSyncOperationEntity] rows to Firestore, in the order they were
 * created, whenever a network connection is available (see [SyncScheduler]). This
 * is the other half of spec §6.5's offline-first requirement: local writes always
 * succeed instantly; this worker is what eventually makes them durable server-side.
 *
 * Also drives the "SYNC TO SERVER" button's progress/summary UI ([SyncStatusButton]/
 * `SyncToServerButton`): [setProgress] is published as each record finishes, and the
 * final uploaded/failed counts are returned in [Result]'s output data so a caller
 * observing this unique work's [androidx.work.WorkInfo] can show a real "Sync
 * Complete — N uploaded, N failed" summary instead of a bare success/failure flag.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueueDao: SyncQueueDao,
    private val cacheDao: CacheDao,
    private val firestore: FirebaseFirestore,
    private val gson: Gson,
    private val syncStatusCenter: SyncStatusCenter,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_UPLOADED = "uploaded"
        const val KEY_FAILED = "failed"
        const val KEY_TOTAL = "total"
        const val KEY_FINISHED = "finished"
    }

    override suspend fun doWork(): Result {
        val pending = syncQueueDao.getAllPending()
        if (pending.isEmpty()) {
            setProgress(workDataOf(KEY_UPLOADED to 0, KEY_FAILED to 0, KEY_TOTAL to 0, KEY_FINISHED to true))
            return Result.success()
        }

        syncStatusCenter.onSyncStarted()
        var uploaded = 0
        var failed = 0
        for ((index, op) in pending.withIndex()) {
            setProgress(workDataOf("done" to index, KEY_TOTAL to pending.size))
            val ok = runCatching { applyOperation(op) }
            if (ok.isSuccess) {
                syncQueueDao.remove(op)
                cacheDao.updateSyncState(op.collectionPath, op.documentId, SyncState.SYNCED.name)
                uploaded++
            } else {
                failed++
                syncQueueDao.recordFailure(op.id, ok.exceptionOrNull()?.message ?: "Unknown error")
                cacheDao.updateSyncState(op.collectionPath, op.documentId, SyncState.FAILED.name)
            }
        }
        // Published via setProgress (not the terminal Result's output data) so the
        // manual "Sync to Server" UI gets an immediate summary of *this* attempt even
        // when the worker's own Result is retry() below — WorkInfo.outputData is only
        // populated for a truly terminal SUCCEEDED/FAILED state, which a retrying
        // worker on a partial failure never reaches for this attempt.
        setProgress(workDataOf(KEY_UPLOADED to uploaded, KEY_FAILED to failed, KEY_TOTAL to pending.size, KEY_FINISHED to true))
        syncStatusCenter.onSyncFinished(uploaded, failed)
        // Partial failure still asks WorkManager to retry later (unchanged background
        // reliability behavior) — the manual UI already has its summary from the
        // progress update above and doesn't need to wait for that retry to resolve.
        return if (failed > 0) Result.retry() else Result.success()
    }

    private suspend fun applyOperation(op: PendingSyncOperationEntity) {
        val docRef = firestore.collection(op.collectionPath).document(op.documentId)
        when (SyncOperationType.valueOf(op.operationType)) {
            SyncOperationType.CREATE, SyncOperationType.UPDATE -> {
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                val fields: Map<String, Any?> = gson.fromJson(op.payloadJson, mapType)
                // Every model's @DocumentId property — "id" for nearly all of them,
                // but SharedLocation's is "publisherPersonId" (see that class's doc
                // comment) — must never be written as a literal stored field:
                // Firestore's toObject() throws on read if it finds one, since
                // @DocumentId is supposed to repopulate that property from the
                // document reference alone. This raw Gson-map upload path doesn't go
                // through Firestore's POJO mapper (which strips these automatically),
                // so it has to know each collection's @DocumentId key explicitly.
                // Confirmed root cause of a real production crash: this write path
                // was uploading a real "publisherPersonId" field for sharedLocations
                // (only "id" was ever stripped), and every session's post-login
                // mirror of that collection crashed the whole app the instant it
                // downloaded that corrupt document back — see FirestoreMirror.kt.
                val documentIdKey = if (op.collectionPath == "sharedLocations") "publisherPersonId" else "id"
                docRef.set(fields - documentIdKey).await()
            }
            SyncOperationType.DELETE -> docRef.delete().await()
        }
    }
}
