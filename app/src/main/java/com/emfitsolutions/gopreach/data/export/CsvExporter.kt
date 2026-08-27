package com.emfitsolutions.gopreach.data.export

import android.content.Context
import android.net.Uri

/**
 * "Export as ... excel" — plain CSV, which opens directly in Excel/Sheets/
 * any spreadsheet app and needs no new dependency (a real .xlsx would need
 * a whole spreadsheet-writing library this app doesn't otherwise use). Same
 * shape [com.emfitsolutions.gopreach.ui.screens.reports.writeReportsCsv]
 * already used for its own report, generalized so every report screen can
 * share it instead of hand-rolling its own CSV string-builder.
 */
object CsvExporter {

    fun write(
        context: Context,
        uri: Uri,
        title: String,
        subtitle: String?,
        columns: List<String>,
        rows: List<List<String>>,
        totals: List<Pair<String, String>> = emptyList(),
    ) {
        val csv = buildString {
            append(escapeCsvCell(title)).append('\n')
            if (subtitle != null) append(escapeCsvCell(subtitle)).append('\n')
            append('\n')
            append(columns.joinToString(",") { escapeCsvCell(it) }).append('\n')
            rows.forEach { row -> append(row.joinToString(",") { escapeCsvCell(it) }).append('\n') }
            if (totals.isNotEmpty()) {
                append('\n')
                totals.forEach { (label, value) -> append(escapeCsvCell(label)).append(',').append(escapeCsvCell(value)).append('\n') }
            }
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
    }

    private fun escapeCsvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
