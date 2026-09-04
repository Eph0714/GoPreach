package com.emfitsolutions.gopreach.data.repository

import com.emfitsolutions.gopreach.data.model.MonthlyReport
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.mirrorFirestoreCollection
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val COLLECTION = "monthlyReports"

/** Spec §5.2 — publisher monthly ministry reports; also the source for the
 * Admin-side Bible-study/hours report views (spec §5.1). */
@Singleton
class MonthlyReportRepository @Inject constructor(
    private val offline: OfflineFirestoreRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observeAll(): Flow<List<MonthlyReport>> = offline.observeCollection(COLLECTION)

    suspend fun save(report: MonthlyReport): MonthlyReport {
        val id = report.id.ifBlank { firestore.collection(COLLECTION).document().id }
        val withId = report.copy(id = id)
        offline.save(COLLECTION, id, withId)
        return withId
    }

    /** Bug fix ("the publisher can still edit the report even though it's
     * already posted"): [save] only writes to this device's own local cache
     * and queues the change — this app is "manual/periodic sync only" by
     * design (see [OfflineFirestoreRepository]'s own doc comment), so an
     * elder marking a report Posted on *their* device could sit unsynced
     * indefinitely, never reaching Firestore, and therefore never reaching
     * the Publisher's own device's live listener either — the Publisher's
     * copy of the report just kept showing SUBMITTED, and their Submit
     * button was (correctly, per that stale data) never disabled. Posting/
     * un-posting is exactly the "can't wait for the next manual sync" case
     * [OfflineFirestoreRepository.saveNow] exists for: still cache-first
     * (never blocks on this call succeeding), but also pushes straight to
     * Firestore immediately when online, so the lock actually takes effect
     * right away instead of whenever someone next happens to sync. */
    suspend fun saveNow(report: MonthlyReport): MonthlyReport {
        val id = report.id.ifBlank { firestore.collection(COLLECTION).document().id }
        val withId = report.copy(id = id)
        offline.saveNow(firestore, COLLECTION, id, withId)
        return withId
    }

    suspend fun delete(reportId: String) = offline.delete(COLLECTION, reportId)

    fun startRemoteSync(): Flow<Unit> =
        mirrorFirestoreCollection(firestore, offline, appScope, COLLECTION, MonthlyReport::class.java) { it.id }
}
