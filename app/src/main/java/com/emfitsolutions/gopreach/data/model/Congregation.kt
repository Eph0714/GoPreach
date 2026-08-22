package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/** Firestore collection: `congregations/{congregationId}` */
data class Congregation(
    @DocumentId val id: String = "",
    val name: String = "",
    val address: String = "",
    /** Unique per spec §4.1. Uniqueness enforced app-side before write (Firestore has
     * no native unique-field constraint) — see CongregationRepository. */
    val code: String = "",
    val createdAt: Long = 0L,
    val createdByPersonId: String = "",
)

/**
 * A group within a congregation, overseen by exactly one Regular Elder
 * (spec §3: "CRUD Groups + assign 1 Elder").
 *
 * Firestore collection: `groups/{groupId}`
 */
data class Group(
    @DocumentId val id: String = "",
    val congregationId: String = "",
    val name: String = "",
    /** Legacy single-elder field from before the three-role structure below —
     * kept, unmigrated, so existing data is never silently dropped or
     * overwritten. A Group written by the current app always leaves this null
     * and uses the three fields instead; a Group from before this change may
     * have only this set. See ManageGroupsScreen for how the admin carries an
     * existing assignment over into one of the three roles. */
    val regularElderPersonId: String? = null,
    /** A Group needs exactly one Elder in each of these three roles to be
     * considered fully assigned (spec: "Group Assignment Incomplete —
     * Missing: X"). Each personId here is also mirrored onto that Elder's own
     * RoleAssignment.groupId (see ManageGroupsViewModel.save) — that mirror is
     * what actually drives their Group/Congregation report access. */
    val overseerPersonId: String? = null,
    val servantPersonId: String? = null,
    val assistantPersonId: String? = null,
    val createdAt: Long = 0L,
) {
    /** Which of the three required roles still need an Elder — empty means fully assigned. */
    fun missingRoles(): List<RegularElderRole> = buildList {
        if (overseerPersonId == null) add(RegularElderRole.GROUP_OVERSEER)
        if (servantPersonId == null) add(RegularElderRole.GROUP_SERVANT)
        if (assistantPersonId == null) add(RegularElderRole.GROUP_ASSISTANT)
    }

    val isComplete: Boolean get() = missingRoles().isEmpty()

    fun personIdFor(role: RegularElderRole): String? = when (role) {
        RegularElderRole.GROUP_OVERSEER -> overseerPersonId
        RegularElderRole.GROUP_SERVANT -> servantPersonId
        RegularElderRole.GROUP_ASSISTANT -> assistantPersonId
    }
}

/**
 * Lookup table for Regular Elder "specific title" (spec §3), fully CRUD-able by
 * admins so congregations can add titles without a code change.
 *
 * Firestore collection: `elderTitles/{elderTitleId}`
 */
data class ElderTitleEntity(
    @DocumentId val id: String = "",
    val titleName: String = "",
    val active: Boolean = true,
)

/** Firestore collection: `territories/{territoryId}` */
data class Territory(
    @DocumentId val id: String = "",
    val congregationId: String = "",
    val name: String = "",
    val description: String? = null,
    val boundaryNotes: String? = null,
    val assignedGroupId: String? = null,
    val createdAt: Long = 0L,
)
