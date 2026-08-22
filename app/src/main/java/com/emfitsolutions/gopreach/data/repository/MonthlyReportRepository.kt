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

    suspend fun delete(reportId: String) = offline.delete(COLLECTION, reportId)

    fun startRemoteSync(): Flow<Unit> =
        mirrorFirestoreCollection(firestore, offline, appScope, COLLECTION, MonthlyReport::class.java) { it.id }
}
