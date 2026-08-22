package com.emfitsolutions.gopreach.data.repository

import com.emfitsolutions.gopreach.data.model.BibleStudyRecord
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.mirrorFirestoreCollection
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val COLLECTION = "bibleStudies"

/** Spec §6.4 — publisher-managed Bible Study Record. */
@Singleton
class BibleStudyRepository @Inject constructor(
    private val offline: OfflineFirestoreRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observeForPublisher(publisherPersonId: String): Flow<List<BibleStudyRecord>> =
        offline.observeCollection<BibleStudyRecord>(COLLECTION)
            .map { list -> list.filter { it.publisherPersonId == publisherPersonId } }

    suspend fun save(record: BibleStudyRecord): BibleStudyRecord {
        val id = record.id.ifBlank { firestore.collection(COLLECTION).document().id }
        val withId = record.copy(id = id)
        offline.save(COLLECTION, id, withId)
        return withId
    }

    suspend fun delete(recordId: String) = offline.delete(COLLECTION, recordId)

    fun startRemoteSync(): Flow<Unit> =
        mirrorFirestoreCollection(firestore, offline, appScope, COLLECTION, BibleStudyRecord::class.java) { it.id }
}
