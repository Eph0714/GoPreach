package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/** "My Bible Text Record: Event/Topic Record Structure Upgrade" — every
 * [BibleTextCategory] saved before this pass has no occasion info at all
 * (see [BibleTextCategory.event]'s own doc comment); this is what a
 * pre-existing one defaults to instead of a blank required field, so it
 * shows up as something the Publisher can immediately correct rather than
 * losing data or being silently blocked (spec §25: "create an appropriate
 * migration/default strategy rather than deleting the records"). */
const val LEGACY_EVENT_PLACEHOLDER = "Personal Bible Study"

/**
 * "My Bible Text Record: Event/Topic Record Structure Upgrade" — the parent
 * record for one Event/occasion (spec: "Public Talk", "Congregation Bible
 * Study", "Memorial", ...), which one or more [BibleTextRecord]s are saved
 * under. Strictly per-Publisher: [publisherPersonId] is the ownership
 * boundary this app's client-side scoping *and* firestore.rules both enforce
 * (see that file's own comment on this collection) — spec §18/§20's "Do not
 * trust a client-submitted PublisherID."
 *
 * Pre-upgrade this was a flat, reusable "Category" ("God's Promise",
 * "Ministry", "Family", ...) with no occasion/speaker info and no concept of
 * a Bible Text "belonging" to one specific event — every [BibleTextRecord]
 * just picked one from a dropdown, and the same category was reused across
 * unrelated records. [event]/[speaker] are new fields, additive on top of
 * that same collection/document shape (no data migration needed): existing
 * [name] values become this Event's Theme/Topic — precisely what those free-
 * text values already were — and existing records keep working with
 * [event] defaulting to [LEGACY_EVENT_PLACEHOLDER] until the Publisher edits
 * them.
 *
 * Firestore collection: `bibleTextCategories/{eventId}`
 */
data class BibleTextCategory(
    @DocumentId val id: String = "",
    val publisherPersonId: String = "",
    /** The Event/occasion type — required for a new record (spec §1/§20);
     * blank for anything saved before this upgrade (see this class's own doc
     * comment and [LEGACY_EVENT_PLACEHOLDER]). */
    val event: String = "",
    /** Kept under its pre-upgrade Firestore field name (`name`) rather than
     * renamed to `themeTopic` on the wire — this is the exact same free-text
     * value the old flat "Category" already stored ("Christian Living",
     * "Ministry and Evangelism", ...), which is precisely what this
     * upgrade's required "Theme/Topic" field means; renaming the stored
     * field would need a data migration for zero actual behavior change. */
    val name: String = "",
    /** Optional (spec §1: "Speaker must NOT be required"). */
    val speaker: String? = null,
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
    /** The parent [BibleTextCategory] (Event/occasion) this Bible Text
     * belongs to — kept under its pre-upgrade field name (`categoryId`) for
     * Firestore backward compatibility; every existing record's value here
     * is already exactly this relationship (spec §17's `MyBibleEventID`). */
    val categoryId: String = "",
    /** Optional (spec §3/§20) — the Publisher's own personal note; multi-line
     * free text. */
    val remarks: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
