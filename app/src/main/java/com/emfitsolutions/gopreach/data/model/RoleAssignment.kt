package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/**
 * One active (or historical) role held by a [Person]. A person can carry several of
 * these at once — e.g. a Coordinator Elder RoleAssignment plus a separate Regular
 * Pioneer RoleAssignment for the same personId (spec §3).
 *
 * Permission checks are always "does this Person have an ACTIVE RoleAssignment
 * matching X in scope Y" — never a lookup on the Person record itself.
 *
 * Firestore collection: `roleAssignments/{roleAssignmentId}`
 */
data class RoleAssignment(
    @DocumentId val id: String = "",
    val personId: String = "",

    /** Serialized [RoleType] — an AdminRole or a PublisherCategory. */
    val roleType: String = "",

    val congregationId: String? = null,
    val groupId: String? = null,

    /** Only meaningful for AdminRole.REGULAR_ELDER — id into [ElderTitleEntity]. */
    val elderTitleId: String? = null,

    val status: RoleAssignmentStatus = RoleAssignmentStatus.ACTIVE,
    val dateAssigned: Long = 0L,

    // Audit trail (spec §3: "who assigned it, when, by whom it can be edited").
    val assignedByPersonId: String = "",
    val lastEditedByPersonId: String? = null,
    val lastEditedAt: Long? = null,
) {
    fun resolvedRoleType(): RoleType = RoleType.deserialize(roleType)
}
