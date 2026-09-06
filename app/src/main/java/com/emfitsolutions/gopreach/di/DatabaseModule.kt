package com.emfitsolutions.gopreach.di

import android.content.Context
import androidx.room.Room
import com.emfitsolutions.gopreach.data.local.AppDatabase
import com.emfitsolutions.gopreach.data.local.dao.CacheDao
import com.emfitsolutions.gopreach.data.local.dao.SyncQueueDao
import com.emfitsolutions.gopreach.data.local.psgc.PsgcDao
import com.emfitsolutions.gopreach.data.local.psgc.PsgcDatabase
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration() // dev-only default; real migrations land before release
            .build()

    @Provides
    fun provideCacheDao(db: AppDatabase): CacheDao = db.cacheDao()

    @Provides
    fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    // "Add a dropdown for City, Municipalities, Town Barangay" — see
    // PsgcDatabase's own doc comment for why this is a separate, read-only
    // Room database rather than folded into AppDatabase above.
    @Provides
    @Singleton
    fun providePsgcDatabase(@ApplicationContext context: Context): PsgcDatabase =
        Room.databaseBuilder(context, PsgcDatabase::class.java, PsgcDatabase.DATABASE_NAME)
            .createFromAsset(PsgcDatabase.ASSET_PATH)
            .build()

    @Provides
    fun providePsgcDao(db: PsgcDatabase): PsgcDao = db.psgcDao()
}
