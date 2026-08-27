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
        const val DEFAULT_ACCURACY_METERS = 5

        fun defaultsFor(congregationId: String) = LocationSharingSettings(congregationId = congregationId)
    }
}
