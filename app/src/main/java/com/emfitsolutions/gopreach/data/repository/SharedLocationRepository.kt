package com.emfitsolutions.gopreach.data.repository

import com.emfitsolutions.gopreach.data.model.SharedLocation
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.mirrorFirestoreCollection
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /** The signed-in publisher's own doc — the actual source of truth for
     * whether their "Share my location while preaching" toggle should read
     * as on or off. Bug fix: the toggle used to be backed by a plain
     * ViewModel-local flag that reset to false every time the screen (and
     * its ViewModel) was recreated — reopening Share Location after leaving
     * it always showed unchecked even while [LocationSharingService] was
     * still actively sharing in the background. Deriving it from this
     * persisted/synced doc instead means the toggle reflects reality no
     * matter when or how the screen is reopened. */
    fun observeFor(publisherPersonId: String): Flow<SharedLocation?> =
        observeAll().map { list -> list.firstOrNull { it.publisherPersonId == publisherPersonId } }

    suspend fun update(location: SharedLocation) = offline.save(COLLECTION, location.publisherPersonId, location)

    suspend fun stopSharing(publisherPersonId: String, lastKnown: SharedLocation) =
        offline.save(COLLECTION, publisherPersonId, lastKnown.copy(isSharing = false))

    fun startRemoteSync(): Flow<Unit> =
        mirrorFirestoreCollection(firestore, offline, appScope, COLLECTION, SharedLocation::class.java) { it.publisherPersonId }
}
