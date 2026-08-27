package com.emfitsolutions.gopreach.data.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.emfitsolutions.gopreach.ui.screens.publisherreports.PublisherReportRow
import java.util.Locale

/**
 * "Print the Publishers Report" — Android's own [PrintManager] + a throwaway
 * [WebView], no third-party PDF library needed (same "no new dependency"
 * approach ReportsScreen's CSV export already uses for its own export/print
 * backlog item). Every installed printer (including "Save as PDF," which
 * every Android print dialog offers out of the box) is reachable through the
 * system print UI this hands off to.
 */
object ReportPrinter {

    fun print(context: Context, title: String, rows: List<PublisherReportRow>, totalBibleStudies: Int, totalHoursByPioneers: Double) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                val adapter = view.createPrintDocumentAdapter(title)
                printManager.print(title, adapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, buildHtml(title, rows, totalBibleStudies, totalHoursByPioneers), "text/html", "UTF-8", null)
    }

    private fun buildHtml(title: String, rows: List<PublisherReportRow>, totalBibleStudies: Int, totalHoursByPioneers: Double): String {
        val body = buildString {
            append("<html><head><meta charset=\"utf-8\"><style>")
            append("body{font-family:sans-serif;font-size:12px;} h2{text-align:center;} ")
            append("table{width:100%;border-collapse:collapse;margin-top:12px;} ")
            append("th,td{border:1px solid #333;padding:4px 8px;text-align:left;} th{background:#eee;} ")
            append("tfoot td{font-weight:bold;}")
            append("</style></head><body>")
            append("<h2>").append(escapeHtml(title)).append("</h2>")
            append("<table><thead><tr>")
            append("<th>#</th><th>Publisher</th><th>Status</th><th>Bible Study</th><th>Hours</th><th>Participate in Preaching</th><th>Congregation</th>")
            append("</tr></thead><tbody>")
            rows.forEachIndexed { index, row ->
                append("<tr>")
                append("<td>").append(index + 1).append("</td>")
                append("<td>").append(escapeHtml(row.person.fullName)).append("</td>")
                append("<td>").append(escapeHtml(row.category.name.replace('_', ' '))).append("</td>")
                append("<td>").append(row.report.bibleStudiesCount).append("</td>")
                append("<td>").append(if (row.isPioneer) formatHours(row.report.hoursRendered ?: 0.0) else "N/A").append("</td>")
                append("<td>")
                    .append(if (row.isPioneer) "N/A" else if (row.report.participatedInPreaching == true) "YES" else "NO")
                    .append("</td>")
                append("<td>").append(escapeHtml(row.congregationName)).append("</td>")
                append("</tr>")
            }
            append("</tbody><tfoot><tr>")
            append("<td colspan=\"3\">Total Bible Study: ").append(totalBibleStudies).append("</td>")
            append("<td colspan=\"4\">Total Hours by Pioneers: ").append(formatHours(totalHoursByPioneers)).append("</td>")
            append("</tr></tfoot></table></body></html>")
        }
        return body
    }

    private fun formatHours(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
