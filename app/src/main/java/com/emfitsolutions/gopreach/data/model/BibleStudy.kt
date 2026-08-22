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
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val religion: String? = null,
    val createdAt: Long = 0L,
)

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
