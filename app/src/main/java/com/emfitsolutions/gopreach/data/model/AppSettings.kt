package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Single global settings doc. [logoUrl] is the Super-Admin-customizable app logo
 * (spec §1: "Application logo must be customizable by the Super Admin
 * (upload/replace via Control Panel)") — null until one's been uploaded, in which
 * case the UI falls back to the built-in wordmark.
 *
 * Firestore collection: `appSettings/{id}` — exactly one document, id [GLOBAL_ID].
 */
data class AppSettings(
    @DocumentId val id: String = GLOBAL_ID,
    val logoUrl: String? = null,
    val updatedAt: Long = 0L,
    val updatedByPersonId: String? = null,
) {
    companion object {
        const val GLOBAL_ID = "global"
    }
}
