package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/** What a [SupportingImage] shows — the UI only ever manages one image today
 * (the first entry of [InterestedPerson.supportingImages], defaulting to
 * [HOUSE]), but the type field is already there so a future multi-image
 * picker (spec's "Multiple Images – Future Ready" §8) is a UI change only,
 * not a data-model migration. */
enum class SupportingImageType { HOUSE, GATE, LANDMARK, MEETING_PLACE, OTHER }

/**
 * One supporting-place photo for an [InterestedPerson] (spec's "Interested
 * Person – Supporting Place Image Capture" feature). Stored as a downscaled,
 * compressed JPEG, Base64-encoded directly into the [InterestedPerson]
 * document — **not** Firebase Storage, since this project's Storage bucket
 * isn't provisioned yet (see SETUP.md: needs the paid Blaze plan). Embedding
 * it here instead means: (a) it rides the same offline-first Room cache +
 * Firestore sync path every other field already uses, no new plumbing; (b) it
 * inherits the InterestedPerson document's own `firestore.rules` access
 * control automatically, satisfying spec §9's "same security and access
 * rules as the Interested Person record" for free; and (c) there is no
 * separate download URL of any kind — public, predictable, or otherwise
 * (spec §9's other requirement) — because there's nothing to fetch
 * independently of the record itself. The capture flow (see
 * SupportingImageCapture.kt) keeps the encoded size to a few hundred KB at
 * most, comfortably inside Firestore's 1 MiB per-document limit.
 */
data class SupportingImage(
    val type: String = SupportingImageType.HOUSE.name,
    val base64Jpeg: String = "",
    val capturedAt: Long = 0L,
)

/**
 * Publisher-managed pipeline record — one person moving through Searching →
 * Return Visit → Bible Study (see [PipelineStage]). Before the "Redesign the
 * Publisher Dashboard" phase this was two separate, unrelated entities (an
 * `InterestedPerson` with no stage field, and a standalone `BibleStudyRecord`
 * with no visit history of its own); they're unified into this one record now
 * that the spec describes a single person carrying the same GPS/name/etc.
 * fields and one shared [Visit] history through every stage of their life in
 * this app. A "Bible Study" today is simply an [InterestedPerson] whose
 * [pipelineStage] is [PipelineStage.BIBLE_STUDY] — there is no separate
 * Bible Study collection or model anymore.
 *
 * Firestore collection: `interestedPeople/{interestedPersonId}`
 */
data class InterestedPerson(
    @DocumentId val id: String = "",
    val publisherPersonId: String = "",
    /** The congregation this record currently belongs to — set at creation
     * from the enrolling publisher's own assignment, and the one field a
     * successful [ForwardRequest] acceptance actually changes (along with
     * [publisherPersonId]). This is also the scope boundary a Service
     * Overseer's incoming-request screen filters on. */
    val congregationId: String = "",
    val name: String = "",
    val gender: Gender? = null,
    val address: String = "",
    /** "Searching Module" spec — optional; free text since a household's
     * make-up isn't a fixed shape (multiple spouses/none/n-a are all valid
     * free-text answers in the source spec's own example). */
    val spouse: String? = null,
    val children: String? = null,
    val ageYears: Int? = null,
    val placeOrigin: String? = null,
    val language: String? = null,
    val literaturePlace: String? = null,
    val remarks: String? = null,
    /** "Interested Person GPS Capture" spec §12 — proper numeric fields, not
     * formatted text, so filtering/mapping by coordinate stays possible.
     * `null` means "not captured yet"; [gpsLat]/[gpsLng] are always set or
     * cleared together (see [InterestedPeopleViewModel.saveGpsLocation]/
     * [InterestedPeopleViewModel.clearGpsLocation]), never independently. */
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    /** Meters, as reported by [com.emfitsolutions.gopreach.data.location
     * .LocationTracker] at capture time — spec §12's recommended metadata. */
    val gpsAccuracy: Float? = null,
    val gpsCapturedAt: Long? = null,
    /** personId of whoever last captured/replaced this location — spec §12's
     * recommended metadata; distinct from the record's own [publisherPersonId]
     * since an Elder editing on a Publisher's behalf is still possible. */
    val gpsCapturedBy: String? = null,
    /** Set on every capture/replace, same value as [gpsCapturedAt] for a
     * fresh capture — kept as its own field (rather than reusing
     * [gpsCapturedAt]) so a future "originally captured vs. last updated"
     * distinction is a read-only addition, not a schema change. */
    val gpsUpdatedAt: Long? = null,
    val religion: String? = null,
    /** "Interested Person Fields" spec §2 — optional free text; distinct
     * from [religion] (a specific field with its own semantics) rather than
     * folding general notes into it. */
    val notes: String? = null,
    val createdAt: Long = 0L,
    /** System-generated (spec §2/§12) — the signed-in session that enrolled
     * this person; set once at creation and never touched by an edit, same
     * way [createdAt] is already handled. Not shown as an editable field. */
    val createdByPersonId: String = "",
    /** Optional (spec §7) — empty until a supporting photo is captured. Only
     * the first entry is used by the current UI; see [SupportingImage]. */
    val supportingImages: List<SupportingImage> = emptyList(),
    /** "Admin Record Deletion and Inactive Status" spec — see [Congregation.status]. */
    val status: RecordStatus = RecordStatus.ACTIVE,
    val pipelineStage: PipelineStage = PipelineStage.SEARCHING,
    /** Bumped every time [pipelineStage] changes (and set to [createdAt] at
     * creation) — this, not [createdAt], is what "Bible Studies this month"
     * style date-range reports filter on (see ConsolidatedReportViewModel/
     * PublisherDashboardViewModel), since a record's creation date and the
     * date it actually became a Bible Study are two different things once a
     * person can spend weeks in Searching or Return Visit first. */
    val stageEnteredAt: Long = 0L,
    /** Points at the most recent [ForwardRequest] for this person, in
     * whichever of its three states it's currently in — `null` means this
     * person has never been forwarded (or was forwarded and the sender
     * cleared/acknowledged the outcome). The sending publisher's own screen
     * reads this id to show a live "Forward status: Pending/Accepted/Declined"
     * without a separate per-person lookup table. */
    val pendingForwardRequestId: String? = null,
) {
    val primarySupportingImage: SupportingImage? get() = supportingImages.firstOrNull()
    val hasGpsLocation: Boolean get() = gpsLat != null && gpsLng != null
}

/**
 * One preaching visit to an [InterestedPerson], logged at any pipeline stage
 * (spec §6.3; "Manage Returned Visit/Bible Study Module"'s own visit-history
 * mechanic reuses this same sub-collection rather than inventing a second
 * one per stage).
 *
 * Firestore collection: `interestedPeople/{interestedPersonId}/visits/{visitId}`
 */
data class Visit(
    @DocumentId val id: String = "",
    val interestedPersonId: String = "",
    val visitDate: Long = 0L,
    val visitTime: Long = 0L,
    /** "Notes / Visit Details" (spec §9) — what [topicDiscussed] already was;
     * kept as this field name rather than adding a redundant duplicate. Also
     * doubles as "Remarks/Topic Discussed" for a Return Visit/Bible Study
     * entry (spec's Visit History example). */
    val topicDiscussed: String? = null,
    val outcome: VisitOutcome = VisitOutcome.NOT_AT_HOME,
    /** Time consumed, in minutes (displayed as hh:mm). */
    val timeConsumedMinutes: Int = 0,
    /** "Visit Information" spec §9 — who conducted this visit ("Visited by"/
     * "Studied by" depending on [InterestedPerson.pipelineStage] at logging
     * time). Usually the owning [InterestedPerson.publisherPersonId], but
     * kept as its own field (not derived) since an Elder can log a visit on a
     * Publisher's behalf, same reasoning as [InterestedPerson.gpsCapturedBy]. */
    val publisherPersonId: String = "",
    /** Optional (spec §9: "if applicable") — when a follow-up is planned. */
    val followUpDate: Long? = null,
    val createdAt: Long = 0L,
    /** System-generated (spec §9) — the signed-in session that logged this
     * visit; may differ from [publisherPersonId] (see its doc comment). */
    val createdByPersonId: String = "",
)

/**
 * "Forward to Other Congregation" spec flow — a cross-congregation transfer
 * request for one [InterestedPerson], created from the Searching module.
 * Name/congregation snapshots are captured at request time so the receiving
 * Service Overseer's review screen and the sending publisher's status view
 * both render correctly even if the underlying Person/Congregation records
 * change later — the same "snapshot what mattered at the time" reasoning
 * this app already applies to [MonthlyReport.category].
 *
 * Firestore collection: `forwardRequests/{forwardRequestId}`
 */
data class ForwardRequest(
    @DocumentId val id: String = "",
    val interestedPersonId: String = "",
    val personNameSnapshot: String = "",
    val fromCongregationId: String = "",
    val fromCongregationNameSnapshot: String = "",
    val fromPublisherPersonId: String = "",
    val fromPublisherNameSnapshot: String = "",
    val toCongregationId: String = "",
    val toCongregationNameSnapshot: String = "",
    val status: ForwardRequestStatus = ForwardRequestStatus.PENDING,
    val requestedAt: Long = 0L,
    val respondedAt: Long? = null,
    val respondedByPersonId: String? = null,
    /** Set only on [ForwardRequestStatus.ACCEPTED] — who in the receiving
     * congregation the record was assigned to. */
    val assignedToPublisherPersonId: String? = null,
    val assignedToPublisherNameSnapshot: String? = null,
)
