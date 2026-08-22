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
    /** personId of the Regular Elder overseeing this group; null until assigned. */
    val regularElderPersonId: String? = null,
    val createdAt: Long = 0L,
)

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
