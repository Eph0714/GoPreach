package com.emfitsolutions.gopreach.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emfitsolutions.gopreach.data.local.CachedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedDocumentEntity)

    @Query("SELECT * FROM cached_documents WHERE collectionPath = :collectionPath ORDER BY updatedAt DESC")
    fun observeCollection(collectionPath: String): Flow<List<CachedDocumentEntity>>

    /** Every cached document, any collection — the source for a full backup export
     * (spec §5.1 Backup & Restore, Super-Admin only). */
    @Query("SELECT * FROM cached_documents")
    suspend fun getAll(): List<CachedDocumentEntity>

    @Query("SELECT * FROM cached_documents WHERE collectionPath = :collectionPath AND documentId = :documentId")
    suspend fun get(collectionPath: String, documentId: String): CachedDocumentEntity?

    @Query("DELETE FROM cached_documents WHERE collectionPath = :collectionPath AND documentId = :documentId")
    suspend fun delete(collectionPath: String, documentId: String)

    @Query("UPDATE cached_documents SET syncState = :syncState WHERE collectionPath = :collectionPath AND documentId = :documentId")
    suspend fun updateSyncState(collectionPath: String, documentId: String, syncState: String)

    @Delete
    suspend fun deleteEntity(entity: CachedDocumentEntity)
}
