package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Publisher-managed Bible Study Record (spec §6.4). The *count* of Bible studies a
 * publisher reports monthly (see [MonthlyReport]) is how many of these were actually
 * conducted in person during the period — not how many visit log rows exist.
 *
 * Firestore collection: `bibleStudies/{bibleStudyId}`
 */
data class BibleStudyRecord(
    @DocumentId val id: String = "",
    val publisherPersonId: String = "",
    val name: String = "",
    val address: String = "",
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val contact: String? = null,
    val createdAt: Long = 0L,
)

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
 * Publisher-managed Interested Person record (spec §6.3).
 *
 * Firestore collection: `interestedPeople/{interestedPersonId}`
 */
data class InterestedPerson(
    @DocumentId val id: String = "",
    val publisherPersonId: String = "",
    val name: String = "",
    val gender: Gender? = null,
    val address: String = "",
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
    val createdAt: Long = 0L,
    /** Optional (spec §7) — empty until a supporting photo is captured. Only
     * the first entry is used by the current UI; see [SupportingImage]. */
    val supportingImages: List<SupportingImage> = emptyList(),
    /** "Admin Record Deletion and Inactive Status" spec — see [Congregation.status]. */
    val status: RecordStatus = RecordStatus.ACTIVE,
) {
    val primarySupportingImage: SupportingImage? get() = supportingImages.firstOrNull()
    val hasGpsLocation: Boolean get() = gpsLat != null && gpsLng != null
}

/**
 * One preaching visit to an [InterestedPerson]. Several of these can exist per
 * interested person (spec §6.3).
 *
 * Firestore collection: `interestedPeople/{interestedPersonId}/visits/{visitId}`
 */
data class Visit(
    @DocumentId val id: String = "",
    val interestedPersonId: String = "",
    val visitDate: Long = 0L,
    val visitTime: Long = 0L,
    val topicDiscussed: String? = null,
    val householderStatus: HouseholderStatus = HouseholderStatus.NOT_AT_HOME,
    /** Time consumed, in minutes (displayed as hh:mm). */
    val timeConsumedMinutes: Int = 0,
    val createdAt: Long = 0L,
)
