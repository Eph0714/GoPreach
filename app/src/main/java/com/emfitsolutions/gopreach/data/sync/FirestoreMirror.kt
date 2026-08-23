package com.emfitsolutions.gopreach.data.sync

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private const val TAG = "FirestoreMirror"

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
        if (error != null || snapshot == null) {
            // A listener that errors (e.g. PERMISSION_DENIED because it was registered
            // before sign-in) is dead for good — it will never emit again on its own.
            // [RemoteSyncCoordinator] re-subscribes fresh on every auth-state change to
            // recover from this; this log is so a future silent-sync bug shows up here
            // instead of requiring a full manual repro session to find again.
            if (error != null) Log.w(TAG, "Listener for '$collectionPath' failed: ${error.message}")
            return@addSnapshotListener
        }
        appScope.launch {
            for (change in snapshot.documentChanges) {
                val model = change.document.toObject(clazz)
                when (change.type) {
                    // Cache-only — never offline.save()/delete() here. Those enqueue a
                    // pending *upload*, which is wrong for a document that just came
                    // *from* the server: it was silently re-queuing every document a
                    // listener had ever seen (including its entire initial snapshot)
                    // as if the user had edited it, inflating "pending changes" by
                    // hundreds for data nobody touched.
                    DocumentChange.Type.REMOVED -> offline.deleteFromServer(collectionPath, idOf(model))
                    else -> offline.cacheFromServer(collectionPath, idOf(model), model)
                }
            }
        }
        trySend(Unit)
    }
    awaitClose { registration.remove() }
}
