package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/**
 * "SHARE LOCATION SETTINGS" — one per congregation, configurable by
 * Super-Admin (any), Admin/Service Overseer/Coordinator Elder/Regular Elder
 * (own congregation only). Governs how long a Publisher's "Share My
 * Location" toggle stays on before automatically stopping, and how accurate
 * a GPS fix must be before it's actually published (spec's example: "30
 * mins" / "5 mtrs").
 *
 * Firestore collection: `locationSharingSettings/{congregationId}`
 */
data class LocationSharingSettings(
    @DocumentId val congregationId: String = "",
    val sharingDurationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val accuracyRadiusMeters: Int = DEFAULT_ACCURACY_METERS,
    val updatedByPersonId: String? = null,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_DURATION_MINUTES = 30
        // Was 5 (the spec's own illustrative example) — even a good outdoor
        // GPS fix commonly reports 8-15m of accuracy, so that default meant
        // a publisher's location almost never actually got published before
        // an admin had ever touched Share Location Settings: "Share
        // Location" looked broken out of the box. 20m is still tight enough
        // to reject a rough WiFi/cell-only fix but achievable by a normal
        // GPS fix; a congregation that wants the stricter 5m can still set
        // it explicitly.
        const val DEFAULT_ACCURACY_METERS = 20

        fun defaultsFor(congregationId: String) = LocationSharingSettings(congregationId = congregationId)
    }
}
