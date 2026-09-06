package com.emfitsolutions.gopreach.data.local.psgc

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * "Add a dropdown for City, Municipalities, Town Barangay... automatic if the
 * publisher captures the coordinates" — the Philippine Standard Geographic
 * Code (Province -> City/Municipality -> Barangay), covering the whole
 * country (~117 provinces/sub-provinces, ~1,650 cities/municipalities,
 * ~42,000 barangays, from the PSA's own PSGC publication). Shipped as a
 * prebuilt, read-only SQLite file at `assets/databases/psgc.db` and opened
 * via Room's `createFromAsset` (see `DatabaseModule.providePsgcDatabase`) —
 * this is reference data, never written to from the app, so there's no
 * migration story to maintain: replacing the bundled asset in a future
 * update is the only way this ever changes.
 *
 * Deliberately a *separate* Room database from [com.emfitsolutions.gopreach
 * .data.local.AppDatabase] — that one is this app's own mutable offline
 * cache/sync queue with its own migration history; mixing a 42k-row static
 * reference table into it would drag PSGC into every one of that database's
 * future schema changes for no reason.
 */
@Database(entities = [ProvinceEntity::class, MuncityEntity::class, BarangayEntity::class], version = 1, exportSchema = false)
abstract class PsgcDatabase : RoomDatabase() {
    abstract fun psgcDao(): PsgcDao

    companion object {
        const val DATABASE_NAME = "psgc.db"
        const val ASSET_PATH = "databases/psgc.db"
    }
}
