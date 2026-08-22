package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

enum class ReportStatus { DRAFT, SUBMITTED }

/**
 * One publisher's monthly ministry report (spec §5.2). Required fields differ by
 * [PublisherCategory] — the UI shows only the fields that category needs, but they
 * all share this shape so reporting/aggregation stays uniform:
 *
 * | Category              | Uses                                              |
 * |------------------------|---------------------------------------------------|
 * | Regular Pioneer        | bibleStudiesCount, hoursRendered                   |
 * | Auxiliary Pioneer      | bibleStudiesCount, hoursRendered (+ active date range, see [AuxiliaryPioneerRange]) |
 * | Regular Publisher      | bibleStudiesCount, participatedInPreaching         |
 * | Unbaptized Publisher   | bibleStudiesCount, participatedInPreaching         |
 *
 * `bibleStudiesCount` is entered by the publisher as the number of studies actually
 * conducted in the period — a count derived from [BibleStudyRecord]-linked activity,
 * not the number of [Visit] rows logged.
 *
 * Lock semantics (spec §5.2): editable/deletable by [publisherPersonId] only while
 * [status] is DRAFT. Once SUBMITTED, only a Coordinator Elder or Regular Elder in the
 * same congregation may edit it.
 *
 * Firestore collection: `monthlyReports/{reportId}`
 */
data class MonthlyReport(
    @DocumentId val id: String = "",
    val publisherPersonId: String = "",
    val congregationId: String = "",
    val category: PublisherCategory = PublisherCategory.REGULAR_PUBLISHER,

    /** Report period, first-of-month epoch millis (e.g. 2026-08-01). */
    val periodMonth: Long = 0L,

    val bibleStudiesCount: Int = 0,

    // Pioneer-only fields
    val hoursRendered: Double? = null,

    // Publisher-only field
    val participatedInPreaching: Boolean? = null,

    val status: ReportStatus = ReportStatus.DRAFT,
    val submittedAt: Long? = null,

    val lastEditedByPersonId: String? = null,
    val lastEditedAt: Long? = null,
)

/**
 * One active/extended date range for an Auxiliary Pioneer assignment. Per spec §7
 * open decision, an extension creates a *new* row rather than mutating the existing
 * one, keeping historical reporting clean.
 *
 * Firestore collection: `auxiliaryPioneerRanges/{rangeId}`
 */
data class AuxiliaryPioneerRange(
    @DocumentId val id: String = "",
    val publisherPersonId: String = "",
    val startDate: Long = 0L,
    /** Null while the range is open-ended pending confirmation of an end date. */
    val endDate: Long? = null,
    val createdByPersonId: String = "",
    val createdAt: Long = 0L,
)
