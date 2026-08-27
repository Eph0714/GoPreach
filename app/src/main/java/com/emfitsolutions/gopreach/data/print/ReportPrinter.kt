package com.emfitsolutions.gopreach.data.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * A generic printable report table — every report screen builds one of
 * these (its own title/heading, column headers, row cells, and any summary
 * totals) and hands it to [ReportPrinter.print]; nothing here is coupled to
 * any one screen's own data class, so it's reusable across every "make all
 * reports have a print preview" report in the app.
 */
data class ReportTable(
    val title: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    /** Rendered as its own small summary block below the table — e.g.
     * "Total Bible Study" to "12". */
    val totals: List<Pair<String, String>> = emptyList(),
)

/**
 * "Make all reports have a print preview" — Android's own [PrintManager] +
 * a throwaway [WebView], no third-party PDF library needed (same "no new
 * dependency" approach [com.emfitsolutions.gopreach.ui.screens.reports
 * .writeReportsCsv]'s CSV export already uses). Every Android print dialog
 * shows its own print preview before anything is sent anywhere, and offers
 * "Save as PDF" out of the box alongside every installed printer — this one
 * hand-off covers both "print preview" and "export as PDF" at once.
 */
object ReportPrinter {

    fun print(context: Context, table: ReportTable) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                val adapter = view.createPrintDocumentAdapter(table.title)
                printManager.print(table.title, adapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, buildHtml(table), "text/html", "UTF-8", null)
    }

    private fun buildHtml(table: ReportTable): String = buildString {
        append("<html><head><meta charset=\"utf-8\"><style>")
        append("body{font-family:sans-serif;font-size:12px;} h2{text-align:center;} ")
        append("table{width:100%;border-collapse:collapse;margin-top:12px;} ")
        append("th,td{border:1px solid #333;padding:4px 8px;text-align:left;} th{background:#eee;} ")
        append("p.total{font-weight:bold;}")
        append("</style></head><body>")
        append("<h2>").append(escapeHtml(table.title)).append("</h2>")
        append("<table><thead><tr>")
        table.columns.forEach { append("<th>").append(escapeHtml(it)).append("</th>") }
        append("</tr></thead><tbody>")
        table.rows.forEach { row ->
            append("<tr>")
            row.forEach { cell -> append("<td>").append(escapeHtml(cell)).append("</td>") }
            append("</tr>")
        }
        append("</tbody></table>")
        table.totals.forEach { (label, value) ->
            append("<p class=\"total\">").append(escapeHtml(label)).append(": ").append(escapeHtml(value)).append("</p>")
        }
        append("</body></html>")
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
