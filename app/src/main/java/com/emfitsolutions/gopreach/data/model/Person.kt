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

    /** "My Bible Text Record" module spec §4 — "Preferred Bible Language."
     * A [com.emfitsolutions.gopreach.domain.NwtBibleReferenceData.BibleLanguage
     * .id] (e.g. "en", "fil"); `null` means never set. Auto-selected as the
     * default language on a new Bible Text Record (spec: "When the
     * Publisher creates a new Bible Text Record, automatically select their
     * preferred language" — still changeable per record). Lives here rather
     * than a separate `PublisherSettings` collection per spec §21's own
     * instruction: "If GoPreach already has a Publisher settings/profile
     * table, integrate this field into the existing structure instead of
     * creating a duplicate table" — every role's settings already live on
     * this one Person document (see [profileImageUrl] alongside it), and a
     * Publisher is a Person like any other role here. */
    val preferredBibleLanguageId: String? = null,

    /** The profile-menu avatar shown at the top-right of the Admin/Publisher
     * Main Form for every role (Super-Admin, Admin, Coordinator/Regular
     * Elder, Publisher, ...) — uploaded to Firebase Storage at
     * `people/{personId}/profile`, same "no new dependency" pattern
     * [AnnouncementRepository]'s own image upload already uses. `null`
     * means never uploaded — the avatar falls back to a blank icon. */
    val profileImageUrl: String? = null,

    // Login — attaches to the Person, not to any one role (spec §3).
    val username: String = "",
    // Explicit @PropertyName: Firestore's Kotlin-bean reflection mishandles
    // boolean properties already prefixed with "is" (it derives the wrong
    // getter/field name and silently drops the value on toObject()), so this
    // pins the actual Firestore field name rather than relying on inference.
    @get:PropertyName("isTemporaryCredential")
    val isTemporaryCredential: Boolean = false,
    /** The plaintext temp password, kept only while [isTemporaryCredential] is
     * true so an admin can look it up again (e.g. the enrollment share link
     * got lost) — cleared the moment the person completes their forced
     * password change. Firebase Auth itself never stores this; it only ever
     * lives here, transiently, for that lookup window. */
    val temporaryPassword: String? = null,

    val createdAt: Long = 0L,
    val createdByPersonId: String? = null,

    /** Account-level lifecycle switch (spec §9) — checked at sign-in time
     * ([com.emfitsolutions.gopreach.data.repository.AuthRepository.signIn]);
     * an INACTIVE/SUSPENDED account can never sign in, independent of whatever
     * [RoleAssignment]s it still holds. */
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,

    /**
     * Denormalized copy of "does this person hold an active
     * [AdminRole.SUPER_ADMIN] RoleAssignment" — kept only so Firestore security
     * rules can check it with a single `get()` on this known-path document
     * instead of an unsupported cross-collection query (see firestore.rules'
     * file-level note). The [RoleAssignment] row is still the real source of
     * truth for every in-app check ([com.emfitsolutions.gopreach.domain.PermissionChecker]
     * never reads this field) — this flag only has to stay correct for rules
     * enforcement to work, and today it's set exactly once, by hand, per
     * SETUP.md's Super-Admin bootstrap steps (there is no in-app "create another
     * Super-Admin" flow to keep in sync).
     */
    @get:PropertyName("isSuperAdmin")
    val isSuperAdmin: Boolean = false,
) {
    val fullName: String
        get() = listOfNotNull(firstName, middleInitial?.let { "$it." }, lastName, extensionName)
            .joinToString(" ")
}
