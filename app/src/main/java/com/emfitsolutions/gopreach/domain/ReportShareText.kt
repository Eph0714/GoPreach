package com.emfitsolutions.gopreach.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Send Report → Send as Text" and the Monthly Report page's own "Share as
 * Text" — the exact wording of the outgoing message, so both entry points
 * (the Planner's shortcut and the actual Monthly Report submission screen)
 * ever produce this one message shape, never two subtly different ones.
 *
 * Pioneer categories report Month/Year, Hours, Minutes, Bible Studies.
 * Every non-Pioneer category reports Month/Year, whether they participated
 * in field ministry at all (Y/N), and Bible Studies — they never report an
 * hours figure, since they aren't required to track one.
 */
object ReportShareText {
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun forPioneer(periodMonthStart: Long, hours: Int, minutes: Int, bibleStudies: Int): String = buildString {
        appendLine(monthYearFormat.format(Date(periodMonthStart)))
        appendLine("Hours: $hours")
        appendLine("Minutes: $minutes")
        append("Bible Studies: $bibleStudies")
    }

    fun forNonPioneer(periodMonthStart: Long, participatedInFieldMinistry: Boolean, bibleStudies: Int): String = buildString {
        appendLine(monthYearFormat.format(Date(periodMonthStart)))
        appendLine("Participated in Field Ministry: ${if (participatedInFieldMinistry) "Y" else "N"}")
        append("Bible Studies: $bibleStudies")
    }
}
