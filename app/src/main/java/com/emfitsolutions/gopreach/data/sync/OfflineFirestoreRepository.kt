package com.emfitsolutions.gopreach.data.sync

import com.emfitsolutions.gopreach.data.local.CachedDocumentEntity
import com.emfitsolutions.gopreach.data.local.PendingSyncOperationEntity
import com.emfitsolutions.gopreach.data.local.dao.CacheDao
import com.emfitsolutions.gopreach.data.local.dao.SyncQueueDao
import com.emfitsolutions.gopreach.data.model.SyncOperationType
import com.emfitsolutions.gopreach.data.model.SyncState
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first read/write path shared by every domain repository (Congregations,
 * Publishers, Territories, Bible Studies, ...) so the "queue-and-sync applies to
 * all CRUD app-wide" requirement (spec §6.5) is implemented once, not per feature.
 *
 * Write path: save to the local cache immediately (state PENDING) and enqueue the
 * operation — that's it. Per the "Manual Sync Requirement" (spec §17: "Do not
 * automatically upload the user's locally created/edited data merely because a
 * network connection becomes available"), this never itself asks [SyncScheduler]
 * to run; the queued row just sits there, tracked by a "pending sync" indicator
 * ([SyncQueueDao.observePendingCount]), until the user explicitly taps
 * [com.emfitsolutions.gopreach.ui.components.SyncToServerButton] (or the older
 * [com.emfitsolutions.gopreach.ui.components.SyncStatusButton]/pull-to-refresh).
 *
 * Read path: callers observe the local cache (always available offline); the cache
 * itself is kept current by Firestore snapshot listeners set up per collection where
 * live updates matter (added alongside each domain repository) — those downloads
 * are a separate concern from this file's upload queue and are unaffected by the
 * manual-sync-only requirement, which is specifically about *this device's own*
 * pending edits.
 */
@Singleton
class OfflineFirestoreRepository @Inject constructor(
    // Non-private: the reified inline functions below (observeCollection, get) need
    // to reach these from call sites in other modules, which public inline functions
    // can only do via @PublishedApi-internal, not private, members.
    @PublishedApi internal val cacheDao: CacheDao,
    private val syncQueueDao: SyncQueueDao,
    @PublishedApi internal val gson: Gson,
) {
    inline fun <reified T> observeCollection(collectionPath: String): Flow<List<T>> =
        cacheDao.observeCollection(collectionPath).map { rows ->
            rows.map { gson.fromJson(it.payloadJson, T::class.java) }
        }

    /** See [CacheDao.observeCollectionsMatching] — for a variable-parent
     * subcollection (e.g. Visits across every Interested Person) rather than
     * one fixed [collectionPath]. */
    inline fun <reified T> observeCollectionsMatching(pathPattern: String): Flow<List<T>> =
        cacheDao.observeCollectionsMatching(pathPattern).map { rows ->
            rows.map { gson.fromJson(it.payloadJson, T::class.java) }
        }

    suspend inline fun <reified T> get(collectionPath: String, documentId: String): T? =
        cacheDao.get(collectionPath, documentId)?.let { gson.fromJson(it.payloadJson, T::class.java) }

    suspend fun <T> save(collectionPath: String, documentId: String, data: T) {
        saveRawJson(collectionPath, documentId, gson.toJson(data))
    }

    /** Same write path as [save], but for a payload that's already serialized —
     * used by [com.emfitsolutions.gopreach.data.repository.BackupRepository] to
     * restore entries straight from a backup file without a round-trip through a
     * typed model. */
    suspend fun saveRawJson(collectionPath: String, documentId: String, json: String) {
        val now = System.currentTimeMillis()
        cacheDao.upsert(
            CachedDocumentEntity(
                collectionPath = collectionPath,
                documentId = documentId,
                payloadJson = json,
                syncState = SyncState.PENDING.name,
                updatedAt = now,
            )
        )
        // A newer edit to the same document supersedes any earlier one still
        // sitting unsynced — never let two queued operations for the same
        // document pile up (see removeForDocument's doc comment).
        syncQueueDao.removeForDocument(collectionPath, documentId)
        syncQueueDao.enqueue(
            // CREATE and UPDATE both resolve to a Firestore set(), so the queue
            // doesn't need to distinguish them once enqueued.
            PendingSyncOperationEntity(
                collectionPath = collectionPath,
                documentId = documentId,
                operationType = SyncOperationType.UPDATE.name,
                payloadJson = json,
                createdAt = now,
            )
        )
    }

    suspend fun delete(collectionPath: String, documentId: String) {
        cacheDao.delete(collectionPath, documentId)
        syncQueueDao.removeForDocument(collectionPath, documentId)
        syncQueueDao.enqueue(
            PendingSyncOperationEntity(
                collectionPath = collectionPath,
                documentId = documentId,
                operationType = SyncOperationType.DELETE.name,
                payloadJson = null,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /** Cache-only write — used **exclusively** by [mirrorFirestoreCollection] to
     * reflect a document that just arrived *from* the server. This must never
     * enqueue a pending upload: doing so was a real, serious bug (every document
     * downloaded by a collection's live listener — including the *entire*
     * initial snapshot the very first time it attaches — was being queued right
     * back up as if the user had just edited it, inflating "pending changes" by
     * hundreds for data nobody ever touched). Marked SYNCED, not PENDING, since
     * it's already exactly what the server has. */
    suspend fun <T> cacheFromServer(collectionPath: String, documentId: String, data: T) {
        cacheDao.upsert(
            CachedDocumentEntity(
                collectionPath = collectionPath,
                documentId = documentId,
                payloadJson = gson.toJson(data),
                syncState = SyncState.SYNCED.name,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Cache-only delete — the [mirrorFirestoreCollection] counterpart to
     * [cacheFromServer] for a document removed on the server. Never enqueues a
     * pending delete for the same reason [cacheFromServer] never enqueues a
     * pending upload. */
    suspend fun deleteFromServer(collectionPath: String, documentId: String) {
        cacheDao.delete(collectionPath, documentId)
    }

    fun observePendingSyncCount(): Flow<Int> = syncQueueDao.observePendingCount()
}
