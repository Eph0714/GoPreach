package com.emfitsolutions.gopreach.data.repository

import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.mirrorFirestoreCollection
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val COLLECTION = "people"

/**
 * Source of truth for [Person] identity records — separate from whatever roles a
 * person holds (see [RoleAssignmentRepository] and spec §3). Backs enrollment
 * (spec §4) and every screen that displays a name/contact/address.
 */
@Singleton
class PersonRepository @Inject constructor(
    private val offline: OfflineFirestoreRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observeAll(): Flow<List<Person>> = offline.observeCollection(COLLECTION)

    suspend fun get(personId: String): Person? = offline.get(COLLECTION, personId)

    suspend fun save(person: Person): Person {
        val id = person.id.ifBlank { firestore.collection(COLLECTION).document().id }
        val withId = person.copy(id = id)
        offline.save(COLLECTION, id, withId)
        return withId
    }

    suspend fun delete(personId: String) {
        offline.delete(COLLECTION, personId)
    }

    /** Mirrors server-side Person changes into the local cache; call once per
     * session (see [RoleAssignmentRepository.startRemoteSync] for the pattern). */
    fun startRemoteSync(): Flow<Unit> =
        mirrorFirestoreCollection(firestore, offline, appScope, COLLECTION, Person::class.java) { it.id }
}
