package com.emfitsolutions.gopreach.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emfitsolutions.gopreach.data.local.PendingSyncOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: PendingSyncOperationEntity): Long

    @Query("SELECT * FROM pending_sync_operations ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingSyncOperationEntity>

    /** Drives the "pending sync" indicator (spec §6.5) app-wide. */
    @Query("SELECT COUNT(*) FROM pending_sync_operations")
    fun observePendingCount(): Flow<Int>

    @Delete
    suspend fun remove(operation: PendingSyncOperationEntity)

    @Query("UPDATE pending_sync_operations SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String)
}
