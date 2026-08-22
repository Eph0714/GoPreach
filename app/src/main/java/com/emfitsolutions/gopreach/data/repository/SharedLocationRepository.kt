package com.emfitsolutions.gopreach.data.repository

import com.emfitsolutions.gopreach.data.model.SharedLocation
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.mirrorFirestoreCollection
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val COLLECTION = "sharedLocations"

/** Spec §6.1 — Share Location. One doc per publisher (keyed by their personId),
 * overwritten on each update; this is live presence, not a location history log. */
@Singleton
class SharedLocationRepository @Inject constructor(
    private val offline: OfflineFirestoreRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observeAll(): Flow<List<SharedLocation>> = offline.observeCollection(COLLECTION)

    suspend fun update(location: SharedLocation) = offline.save(COLLECTION, location.publisherPersonId, location)

    suspend fun stopSharing(publisherPersonId: String, lastKnown: SharedLocation) =
        offline.save(COLLECTION, publisherPersonId, lastKnown.copy(isSharing = false))

    fun startRemoteSync(): Flow<Unit> =
        mirrorFirestoreCollection(firestore, offline, appScope, COLLECTION, SharedLocation::class.java) { it.publisherPersonId }
}
