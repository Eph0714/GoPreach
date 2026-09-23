package com.emfitsolutions.gopreach.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Send Report → Send as Text"/"Preview as Text" and the Monthly Report
 * screen's own "Share as Text" — the exact wording of the outgoing message,
 * so every entry point (the Planner's quick shortcut, the Monthly Report
 * form's Preview step, and its own Share action) ever produce this one
 * message shape, never subtly different copies of it. "The Text Preview
 * must display the exact information that will be submitted" — this is
 * also, deliberately, the only place that text is ever built, so Preview
 * and the actual shared/submitted message can never drift apart.
 *
 * Pioneer categories report Month, Hours, Minutes, Bible Studies. Every
 * non-Pioneer category also reports whether they participated in field
 * ministry at all (Y/N) — they never report an hours figure, since they
 * aren't required to track one. Return Visit is deliberately never
 * included here — spec: "excluded from the Monthly Report Form and its
 * Monthly Report-specific preview/output," even though the underlying
 * count is still calculated and saved on the report document itself.
 */
object ReportShareText {
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun forPioneer(periodMonthStart: Long, hours: Int, minutes: Int, bibleStudies: Int, remarks: String = ""): String =
        header(periodMonthStart) + buildString {
            appendLine("Hours: $hours")
            appendLine("Minutes: $minutes")
            append("Bible Studies: $bibleStudies")
        } + remarksSection(remarks)

    fun forNonPioneer(
        periodMonthStart: Long,
        hours: Int,
        minutes: Int,
        bibleStudies: Int,
        participatedInFieldMinistry: Boolean,
        remarks: String = "",
    ): String =
        header(periodMonthStart) + buildString {
            appendLine("Hours: $hours")
            appendLine("Minutes: $minutes")
            appendLine("Bible Studies: $bibleStudies")
            append("Preaching: ${if (participatedInFieldMinistry) "Yes" else "No"}")
        } + remarksSection(remarks)

    private fun header(periodMonthStart: Long): String =
        "MONTHLY REPORT\n\nMonth: ${monthYearFormat.format(Date(periodMonthStart))}\n\n"

    private fun remarksSection(remarks: String): String =
        if (remarks.isBlank()) "" else "\n\nRemarks:\n${remarks.trim()}"
}
