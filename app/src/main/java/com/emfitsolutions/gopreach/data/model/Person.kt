package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Identity + login record — created once per human being, independent of whatever
 * role(s) they hold. See BUILD_PLAN Phase 1 / spec §3 recommendation: role, scope,
 * and permissions live on [RoleAssignment], never duplicated here.
 *
 * Firestore collection: `people/{personId}`
 */
data class Person(
    @DocumentId val id: String = "",
    val lastName: String = "",
    val firstName: String = "",
    val middleInitial: String? = null,
    val extensionName: String? = null,
    val address: String = "",
    val gender: Gender? = null,
    val contact: String = "",
    val contactPerson: String? = null,
    val contactPersonNumber: String? = null,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val email: String? = null,

    // Login — attaches to the Person, not to any one role (spec §3).
    val username: String = "",
    // Explicit @PropertyName: Firestore's Kotlin-bean reflection mishandles
    // boolean properties already prefixed with "is" (it derives the wrong
    // getter/field name and silently drops the value on toObject()), so this
    // pins the actual Firestore field name rather than relying on inference.
    @get:PropertyName("isTemporaryCredential")
    val isTemporaryCredential: Boolean = false,

    val createdAt: Long = 0L,
    val createdByPersonId: String? = null,
) {
    val fullName: String
        get() = listOfNotNull(firstName, middleInitial?.let { "$it." }, lastName, extensionName)
            .joinToString(" ")
}
