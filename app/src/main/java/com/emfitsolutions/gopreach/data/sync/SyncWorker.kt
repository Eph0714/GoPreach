package com.emfitsolutions.gopreach.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueueDao: SyncQueueDao,
    private val cacheDao: CacheDao,
    private val firestore: FirebaseFirestore,
    private val gson: Gson,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = syncQueueDao.getAllPending()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (op in pending) {
            val ok = runCatching { applyOperation(op) }
            if (ok.isSuccess) {
                syncQueueDao.remove(op)
                cacheDao.updateSyncState(op.collectionPath, op.documentId, SyncState.SYNCED.name)
            } else {
                anyFailed = true
                syncQueueDao.recordFailure(op.id, ok.exceptionOrNull()?.message ?: "Unknown error")
                cacheDao.updateSyncState(op.collectionPath, op.documentId, SyncState.FAILED.name)
            }
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun applyOperation(op: PendingSyncOperationEntity) {
        val docRef = firestore.collection(op.collectionPath).document(op.documentId)
        when (SyncOperationType.valueOf(op.operationType)) {
            SyncOperationType.CREATE, SyncOperationType.UPDATE -> {
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                val fields: Map<String, Any?> = gson.fromJson(op.payloadJson, mapType)
                // Every model's "id" is @DocumentId — Firestore throws on toObject()
                // if that property's name also exists as a literal stored field, so
                // it must never be written; @DocumentId repopulates it from the
                // document reference on every read instead.
                docRef.set(fields - "id").await()
            }
            SyncOperationType.DELETE -> docRef.delete().await()
        }
    }
}
