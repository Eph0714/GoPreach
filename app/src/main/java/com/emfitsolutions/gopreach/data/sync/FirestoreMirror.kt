package com.emfitsolutions.gopreach.data.sync

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Attaches a live Firestore snapshot listener on [collectionPath] and mirrors every
 * change into the offline cache via [offline], so every domain repository gets the
 * same "cache is always current when online, always available when offline"
 * behavior without re-implementing the listener each time (see e.g.
 * [com.emfitsolutions.gopreach.data.repository.PersonRepository.startRemoteSync]).
 */
fun <T : Any> mirrorFirestoreCollection(
    firestore: FirebaseFirestore,
    offline: OfflineFirestoreRepository,
    appScope: CoroutineScope,
    collectionPath: String,
    clazz: Class<T>,
    idOf: (T) -> String,
): Flow<Unit> = callbackFlow {
    val registration = firestore.collection(collectionPath).addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null) return@addSnapshotListener
        appScope.launch {
            for (change in snapshot.documentChanges) {
                val model = change.document.toObject(clazz)
                when (change.type) {
                    DocumentChange.Type.REMOVED -> offline.delete(collectionPath, idOf(model))
                    else -> offline.save(collectionPath, idOf(model), model)
                }
            }
        }
        trySend(Unit)
    }
    awaitClose { registration.remove() }
}
