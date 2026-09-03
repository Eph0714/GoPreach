package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/**
 * "My Bible Text Record" module — a Publisher's own personal category for
 * organizing saved Bible references (spec §10: "God's Promise", "Ministry",
 * "Family", ...). Strictly per-Publisher: [publisherPersonId] is the
 * ownership boundary this app's client-side scoping *and* firestore.rules
 * both enforce (see that file's own comment on this collection) — spec §20's
 * "Publisher A must not be able to edit or delete Publisher B's categories."
 *
 * Firestore collection: `bibleTextCategories/{categoryId}`
 */
data class BibleTextCategory(
    @DocumentId val id: String = "",
    val publisherPersonId: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * "My Bible Text Record" module (spec §1-§34) — one saved Bible reference: a
 * personal study/ministry/reminder note tied to Version + Language + Book +
 * Chapter + Verses + Category + Remarks. [bibleVersionId]/[languageId]/
 * [bibleBookId] key into the static reference data in
 * [com.emfitsolutions.gopreach.domain.NwtBibleReferenceData] (see that
 * file's own doc comment for why this app's Bible reference metadata is
 * bundled reference data rather than its own synced Firestore collection —
 * this record just stores the ids so a future move to a server-hosted
 * lookup table is a data-source swap under these same fields, not a schema
 * change here).
 *
 * Spec §32 — this stores a *reference* (book/chapter/verses) plus the
 * Publisher's own [remarks], never the underlying NWT verse text itself: no
 * licensed/authorized full-text source is wired into this app, so nothing
 * here reproduces copyrighted Bible text. [remarks] is the Publisher's own
 * original content and is stored like any other personal note.
 *
 * Firestore collection: `bibleTextRecords/{recordId}`
 */
data class BibleTextRecord(
    @DocumentId val id: String = "",
    val publisherPersonId: String = "",
    val bibleVersionId: String = "",
    val languageId: String = "",
    val bibleBookId: String = "",
    val chapter: Int = 0,
    /** Spec §9 — a single verse ("3") or a verse range ("3-4", "10-12"),
     * stored as entered (validated at the UI layer against the selected
     * Book's chapter/verse-count metadata when available — see
     * [com.emfitsolutions.gopreach.domain.NwtBibleReferenceData]). Free text
     * rather than two int columns so "3-4" round-trips exactly as the
     * Publisher typed it, matching every worked example in the spec. */
    val verses: String = "",
    val categoryId: String = "",
    /** Spec §12 — the Publisher's own personal note; multi-line free text. */
    val remarks: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
