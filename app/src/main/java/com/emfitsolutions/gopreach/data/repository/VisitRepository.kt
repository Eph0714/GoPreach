package com.emfitsolutions.gopreach.data.repository

import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.mirrorFirestoreCollection
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private fun visitsPath(interestedPersonId: String) = "interestedPeople/$interestedPersonId/visits"

/** Spec §6.3 — one or more preaching visits per [com.emfitsolutions.gopreach.data.model.InterestedPerson]. */
@Singleton
class VisitRepository @Inject constructor(
    private val offline: OfflineFirestoreRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observeForInterestedPerson(interestedPersonId: String): Flow<List<Visit>> =
        offline.observeCollection(visitsPath(interestedPersonId))

    /** Unlike the app's other collections, visits are scoped per-parent-document,
     * so the live listener is started per interested person on demand (e.g. when
     * opening their detail screen) rather than once app-wide. */
    fun startRemoteSync(interestedPersonId: String): Flow<Unit> =
        mirrorFirestoreCollection(firestore, offline, appScope, visitsPath(interestedPersonId), Visit::class.java) { it.id }

    suspend fun save(visit: Visit): Visit {
        val path = visitsPath(visit.interestedPersonId)
        val id = visit.id.ifBlank { firestore.collection(path).document().id }
        val withId = visit.copy(id = id)
        offline.save(path, id, withId)
        return withId
    }

    suspend fun delete(interestedPersonId: String, visitId: String) =
        offline.delete(visitsPath(interestedPersonId), visitId)
}
