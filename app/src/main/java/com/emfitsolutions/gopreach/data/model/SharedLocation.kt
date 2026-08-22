package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * A publisher's live location while preaching (spec §6.1). One doc per publisher,
 * overwritten on each update, cleared when they stop sharing — this is presence,
 * not a history log.
 *
 * Firestore collection: `sharedLocations/{publisherPersonId}`
 */
data class SharedLocation(
    @DocumentId val publisherPersonId: String = "",
    val congregationId: String = "",
    val groupId: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracyMeters: Float? = null,
    // See Person.isTemporaryCredential for why this needs @get:PropertyName —
    // same Firestore Kotlin-bean "is"-prefix mapping quirk.
    @get:PropertyName("isSharing")
    val isSharing: Boolean = false,
    val updatedAt: Long = 0L,
)
