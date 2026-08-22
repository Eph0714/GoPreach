package com.emfitsolutions.gopreach.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One outstanding write against Firestore, queued while offline (or while a prior
 * attempt failed) and flushed by SyncWorker once connectivity is back. Ordered by
 * [createdAt] so operations against the same document replay in the order the user
 * made them.
 */
@Entity(tableName = "pending_sync_operations")
data class PendingSyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionPath: String,
    val documentId: String,
    /** [com.emfitsolutions.gopreach.data.model.SyncOperationType] name. */
    val operationType: String,
    /** Full document JSON for CREATE/UPDATE; null for DELETE. */
    val payloadJson: String?,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
)
