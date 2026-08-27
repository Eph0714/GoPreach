package com.emfitsolutions.gopreach.domain

import com.emfitsolutions.gopreach.data.model.MonthlyReport
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.ReportStatus
import java.util.Calendar

/**
 * "CREATING PUBLISHER" spec's auto-status rules:
 * - No report at all in the last 6 completed months -> [PublisherCategory.INACTIVE_PUBLISHER].
 * - Reported in *some* but not all of the last 6 completed months -> [PublisherCategory.IRREGULAR_PUBLISHER].
 * - Reported every one of the last 6 completed months -> no change.
 *
 * (The spec's first bullet literally reads "is reporting for 6 months
 * consecutive... automatically set to Inactive," which would be backwards —
 * its own second bullet, and standard field-service record-keeping, make
 * clear the intent is "is *not* reporting.")
 *
 * Only re-evaluates the four "actively serving" categories the spec's
 * examples name (Regular Pioneer/Auxiliary Pioneer/Regular Publisher/
 * Unbaptized Publisher) — Inactive/Irregular/Reproof/Removed are left alone
 * here; moving *out* of Inactive/Irregular back onto an active category is a
 * manual admin action (the spec has no auto-reactivation rule), same as
 * Removed Publisher already is (see PermissionChecker.isAccountUsable).
 */
object PublisherAutoStatus {

    private val ACTIVE_CATEGORIES = setOf(
        PublisherCategory.REGULAR_PIONEER,
        PublisherCategory.AUXILIARY_PIONEER,
        PublisherCategory.REGULAR_PUBLISHER,
        PublisherCategory.UNBAPTIZED_PUBLISHER,
    )

    /**
     * [reportsForThisPublisher] should already be filtered to one publisher
     * (any congregation/category history is fine, only [MonthlyReport.periodMonth]
     * /[MonthlyReport.status] and the category-appropriate activity field are
     * read). [asOfMonthStart] is the first-of-month epoch millis for the
     * *current*, possibly still in-progress month — the 6-month window
     * checked is the 6 completed months immediately before it, so a month
     * that hasn't finished yet is never counted against a publisher (same
     * reasoning ReminderWorker already uses for its own mid-month checks).
     *
     * Returns the category to change to, or null if [category] isn't one of
     * the four active ones or nothing needs to change.
     */
    fun evaluate(category: PublisherCategory, reportsForThisPublisher: List<MonthlyReport>, asOfMonthStart: Long): PublisherCategory? {
        if (category !in ACTIVE_CATEGORIES) return null
        val isPioneer = category == PublisherCategory.REGULAR_PIONEER || category == PublisherCategory.AUXILIARY_PIONEER

        val cal = Calendar.getInstance().apply { timeInMillis = asOfMonthStart }
        val windowMonthStarts = (1..6).map { monthsAgo ->
            (cal.clone() as Calendar).apply { add(Calendar.MONTH, -monthsAgo) }.timeInMillis
        }

        fun reportedIn(monthStart: Long): Boolean {
            val report = reportsForThisPublisher.firstOrNull { it.periodMonth == monthStart && it.status == ReportStatus.SUBMITTED }
                ?: return false
            return if (isPioneer) (report.hoursRendered ?: 0.0) > 0.0 else report.participatedInPreaching == true
        }

        val reportedCount = windowMonthStarts.count(::reportedIn)
        return when {
            reportedCount == 0 -> PublisherCategory.INACTIVE_PUBLISHER
            reportedCount < windowMonthStarts.size -> PublisherCategory.IRREGULAR_PUBLISHER
            else -> null
        }
    }
}
