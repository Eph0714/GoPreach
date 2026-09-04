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

/** "Currently sharing" vs. a stale "last known" position — a location older
 * than this is treated as no longer live even if its own [SharedLocation
 * .isSharing] flag still says true (e.g. the publisher's
 * [com.emfitsolutions.gopreach.data.location.LocationSharingService]
 * process died — killed, doze, network loss — without ever getting a
 * chance to write the "stopped" doc). Just over twice that Service's own
 * fix interval (5 minutes) plus real margin for a slow network/GPS fix,
 * so a single missed publish cycle doesn't flicker a still-actively-sharing
 * publisher in and out of "currently sharing." Single source of truth —
 * the Territory Map's Publisher layer and Share Location's own "who's
 * sharing" list both read this rather than each keeping their own
 * threshold. */
const val LOCATION_FRESHNESS_MS = 12 * 60_000L

/** True only when [SharedLocation.isSharing] is set *and* the fix is still
 * within [LOCATION_FRESHNESS_MS] of [nowMillis] — see that constant's own
 * doc comment for why both checks matter. */
fun SharedLocation.isCurrentlyFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
    isSharing && (nowMillis - updatedAt) <= LOCATION_FRESHNESS_MS
