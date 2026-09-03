package com.emfitsolutions.gopreach.data.model

import com.google.firebase.firestore.DocumentId

/** [POSTED] — "allow the publisher to edit the record until the service
 * overseer will mark it as 'Posted', that's the time the publisher can no
 * longer edit the record": a Publisher may freely edit their own report
 * through both DRAFT and SUBMITTED; [POSTED] is the one status that
 * actually locks them out. Only a Service Overseer/Admin/Super-Admin marks
 * a report Posted (see [com.emfitsolutions.gopreach.ui.screens
 * .publisherreports.ManagePublisherReportsViewModel.markPosted]) — Submit
 * itself never sets this, unlike the old DRAFT/SUBMITTED-only model where
 * Submit alone was what locked the Publisher out. */
enum class ReportStatus { DRAFT, SUBMITTED, POSTED }

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
 * Lock semantics: editable by [publisherPersonId] themselves while [status]
 * is DRAFT *or* SUBMITTED — locked out only once [ReportStatus.POSTED] (see
 * that enum value's own doc comment). A Service Overseer, Admin (own
 * congregation only), or Super-Admin (every congregation) may edit a report
 * at any status, Posted included — "if there is still a correction needed,
 * the Service Overseer will do the edition; the Admin and Super-Admin can
 * do the same."
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
) {
    /** "Has this publisher actually submitted a report for this period" —
     * true for both [ReportStatus.SUBMITTED] and [ReportStatus.POSTED].
     * Bug fix: several call sites (ReminderWorker's "already submitted, skip
     * the reminder" check, PublisherAutoStatus's irregular-publisher
     * detection, and the Dashboard/Consolidated Report totals) used to test
     * `status == ReportStatus.SUBMITTED` directly to mean exactly this —
     * which broke the moment POSTED was introduced as a *further* status
     * beyond Submitted: a Posted report would have silently stopped
     * counting as submitted at all (vanishing from report totals, and
     * wrongly re-triggering "you haven't submitted yet" reminders/
     * irregular-publisher flags for someone who very much had). Every one
     * of those now reads this property instead of comparing `status`
     * directly. */
    val isSubmittedOrPosted: Boolean get() = status == ReportStatus.SUBMITTED || status == ReportStatus.POSTED
}

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
