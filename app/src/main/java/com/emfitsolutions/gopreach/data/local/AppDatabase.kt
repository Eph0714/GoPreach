package com.emfitsolutions.gopreach.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.emfitsolutions.gopreach.data.local.dao.CacheDao
import com.emfitsolutions.gopreach.data.local.dao.SyncQueueDao

@Database(
    entities = [CachedDocumentEntity::class, PendingSyncOperationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val DATABASE_NAME = "gopreach.db"
    }
}
