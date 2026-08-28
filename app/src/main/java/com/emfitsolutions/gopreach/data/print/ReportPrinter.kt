package com.emfitsolutions.gopreach.data.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

private const val TAG = "ReportPrinter"

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

    /** Holds every in-flight [WebView] until its print hand-off completes —
     * bug fix ("I cannot see any PDF or Excel"): [print] used to create the
     * WebView as a bare local variable with nothing else referencing it.
     * `loadDataWithBaseURL` is asynchronous, and a WebView that's never
     * attached to any view hierarchy is otherwise unreachable from GC roots
     * the moment [print] returns — on a device under memory pressure (or
     * just unlucky timing), the WebView could be collected before
     * `onPageFinished` ever fires, so the print dialog silently never
     * appeared and nothing told the caller why. Keeping a strong reference
     * here until the callback actually runs (success or failure) removes
     * that race entirely. */
    private val inFlightWebViews = mutableSetOf<WebView>()

    fun print(context: Context, table: ReportTable) {
        val webView = WebView(context)
        inFlightWebViews += webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                try {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (printManager == null) {
                        Log.e(TAG, "PRINT_SERVICE unavailable on this device")
                        Toast.makeText(context, "Printing isn't available on this device.", Toast.LENGTH_LONG).show()
                        return
                    }
                    val adapter = view.createPrintDocumentAdapter(table.title)
                    printManager.print(table.title, adapter, PrintAttributes.Builder().build())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open print dialog", e)
                    Toast.makeText(context, "Couldn't open the print dialog: ${e.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
                } finally {
                    inFlightWebViews -= webView
                }
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                Log.e(TAG, "WebView failed to load report HTML: $description")
                Toast.makeText(context, "Couldn't prepare the report for printing.", Toast.LENGTH_LONG).show()
                inFlightWebViews -= webView
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
