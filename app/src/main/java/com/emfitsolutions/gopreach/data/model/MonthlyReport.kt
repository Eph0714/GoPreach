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
 * | Regular Pioneer        | bibleStudiesCount, participatedInPreaching, hoursRendered |
 * | Auxiliary Pioneer      | bibleStudiesCount, participatedInPreaching, hoursRendered (+ active date range, see [AuxiliaryPioneerRange]) |
 * | Regular Publisher      | bibleStudiesCount, participatedInPreaching         |
 * | Unbaptized Publisher   | bibleStudiesCount, participatedInPreaching         |
 *
 * "Update Monthly Report Submission — Automatic Bible Study Count and
 * Preaching Participation" — [bibleStudiesCount] is no longer typed in by the
 * Publisher at all: it's the count of *distinct* [InterestedPerson] (see that
 * class's own doc comment on why a "Bible Study" is just one of its
 * [PipelineStage] values, not a separate collection) owned by
 * [publisherPersonId] with [InterestedPerson.pipelineStage] ==
 * [PipelineStage.BIBLE_STUDY] that had at least one qualifying [Visit] during
 * [periodMonth] — never the number of Visit rows themselves (see
 * [com.emfitsolutions.gopreach.domain.MonthlyReportCalculator] for the actual
 * calculation, shared with [participatedInPreaching]'s own suggested value
 * and [systemCalculatedHours]). [participatedInPreaching] now applies to
 * *every* category (previously null'd out for Pioneers, who only ever
 * reported hours) — pre-filled from the same calculation but still a
 * Publisher-editable Yes/No, same as before.
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

    /** Automatically calculated (see this class's own doc comment) — never a
     * free-text field the Publisher types into. */
    val bibleStudiesCount: Int = 0,

    // Pioneer-only field — the Publisher's own final, submitted total (what
    // every existing consumer of this field — Dashboard totals, Consolidated
    // Report, ManagePublisherReportsScreen — already reads). May equal
    // [systemCalculatedHours] (accepted as-is) or differ from it (a manual
    // adjustment, which then requires [hoursConfirmed] + non-blank
    // [hoursAdjustmentRemarks] — enforced both client-side and in
    // firestore.rules' `monthlyReports` write rule).
    val hoursRendered: Double? = null,

    /** Pioneer-only — the "My Total Hours" total for [periodMonth] at the
     * moment this report was last saved, kept separately from
     * [hoursRendered] purely for comparison/audit (spec §12/§19: "preserve
     * the system-calculated value... do not allow the Publisher to overwrite
     * or alter" it). `null` for a Non-Pioneer, or if this report predates
     * this field's introduction. */
    val systemCalculatedHours: Double? = null,

    /** Pioneer-only — required `true` whenever [hoursRendered] differs from
     * [systemCalculatedHours] (spec §10/§19); meaningless (left `false`)
     * when they match, or for a Non-Pioneer. */
    val hoursConfirmed: Boolean = false,

    /** Pioneer-only — required non-blank whenever [hoursRendered] differs
     * from [systemCalculatedHours] (spec §10); distinct from the general
     * [remarks] field below, which stays optional for every category. */
    val hoursAdjustmentRemarks: String? = null,

    // Every category's field now (see this class's own doc comment) — was
    // Publisher-only (non-Pioneer) before this pass.
    val participatedInPreaching: Boolean? = null,

    val status: ReportStatus = ReportStatus.DRAFT,
    val submittedAt: Long? = null,

    /** Optional free-text note the publisher can attach to their own
     * submission — unlike every other field above, never required for any
     * [PublisherCategory]. */
    val remarks: String? = null,

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
