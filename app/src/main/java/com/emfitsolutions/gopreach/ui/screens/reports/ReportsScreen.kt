package com.emfitsolutions.gopreach.ui.screens.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.export.CsvExporter
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.ui.components.DateRange
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun PublisherCategory.displayLabel(): String = name.replace('_', ' ')
    .lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

/** Spec §5.1 — Total Bible Studies / Interested People / preaching hours, per
 * publisher, grouped by Group, with an "All Publishers" summary at the top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    visibleCongregationId: String?,
    visibleGroupId: String? = null,
    /** Spec §5.2: only a Coordinator/Regular Elder may edit a publisher's
     * *submitted* monthly report; false hides the per-row edit affordance
     * entirely (e.g. for Super-Admin/Admin, who only view/export/print, spec §3). */
    canEditReports: Boolean = false,
    onEditPublisher: (personId: String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    // A Regular Elder (any of the three Group roles — Overseer/Servant/Assistant)
    // has both a group and a congregation to view: their own Group's Publisher
    // Report, and their whole Congregation's. Anyone else scoped to just a
    // congregation (or to everything, as Super-Admin) never sees this toggle —
    // there's nothing narrower to switch away from.
    var showCongregationWide by remember { mutableStateOf(false) }
    val canToggleScope = visibleCongregationId != null && visibleGroupId != null
    val effectiveGroupId = if (canToggleScope && showCongregationWide) null else visibleGroupId

    // "Main Form Date Range Filtering" spec §7/§4/§8 — This Month by default
    // (calculated live from the current date), shared with the Dashboard's
    // own Reports screen via DateRangeStore rather than screen-local state,
    // so navigating between the two never resets the selection. Today/This
    // Week/This Month/This Year/Custom (From, To) are all offered by
    // [DateRangeFilterBar] below — nothing extra needed here for that.
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()

    val rowsFlow = remember(visibleCongregationId, effectiveGroupId, dateRange) {
        viewModel.rowsFor(visibleCongregationId, effectiveGroupId, dateRange)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // "Group the record by Group" — the same rows above, bucketed into
    // sections with their own Overseer/Servant/Assistant header and a
    // per-group summary total.
    val sectionsFlow = remember(visibleCongregationId, effectiveGroupId, dateRange) {
        viewModel.groupedRowsFor(visibleCongregationId, effectiveGroupId, dateRange)
    }
    val sections by sectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // "Make all reports have a print preview [and] export as pdf or excel,
    // put a heading" — safe to always offer: it's the exact same rows
    // already visible on screen, not a new data exposure, so there's no
    // separate Permission gate the way Add/Edit/Delete have via [readOnly]
    // elsewhere.
    val context = LocalContext.current
    val showToast = rememberActionToast()
    val reportTable = remember(sections, dateRange) { reportsTableFor(sections, dateRange) }
    // Bug fix ("I cannot see any PDF or Excel inside the Reports Summary"):
    // the Storage Access Framework picker just saves and closes — nothing
    // about that flow shows the result inside the app on its own. Now it
    // confirms the export succeeded and immediately opens the file, instead
    // of leaving the user to go find it in a file manager (or wonder whether
    // it ever actually happened).
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                val wrote = CsvExporter.write(context, uri, reportTable.title, subtitle = null, columns = reportTable.columns, rows = reportTable.rows, totals = reportTable.totals)
                if (wrote) {
                    showToast("Exported to Excel (CSV).")
                    CsvExporter.openWithChooser(context, uri, "text/csv")
                } else {
                    showToast("Export failed: couldn't write the file.")
                }
            } catch (e: Exception) {
                showToast("Export failed: ${e.localizedMessage ?: "unknown error"}")
            }
        }
    }
    val exportFileName = "gopreach-reports-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Bug fix ("I cannot see export to excel or pdf in
                    // Reports Summary"): these were `enabled = rows.isNotEmpty()`
                    // — a disabled IconButton's icon renders at reduced alpha,
                    // and on this TopAppBar's colors that read as "not there
                    // at all" rather than "there but dimmed," especially for
                    // whichever congregation/date range/scope happened to
                    // have zero submitted reports (the default landing state
                    // for plenty of sessions). Always enabled now — printing
                    // or exporting with nothing to show just produces a
                    // heading-only PDF/CSV, which is a harmless, valid
                    // result, not something worth hiding the buttons over.
                    IconButton(onClick = { ReportPrinter.print(context, reportTable) }) {
                        Icon(Icons.Rounded.Print, contentDescription = "Print / Export as PDF")
                    }
                    IconButton(onClick = { exportLauncher.launch(exportFileName) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Export as Excel (CSV)")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (canToggleScope) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedButton(
                    selected = !showCongregationWide,
                    onClick = { showCongregationWide = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("My Group") }
                SegmentedButton(
                    selected = showCongregationWide,
                    onClick = { showCongregationWide = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("My Congregation") }
            }
        }
        DateRangeFilterBar(
            range = dateRange,
            onRangeChange = viewModel::setDateRange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No submitted reports yet. Totals appear here once publishers start submitting monthly reports.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("All Publishers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Bible Studies: ${rows.sumOf { it.totalBibleStudies }}", style = MaterialTheme.typography.bodyMedium)
                            Text("Hours: ${"%.1f".format(rows.sumOf { it.totalHours })}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Interested People: ${rows.sumOf { it.totalInterestedPeople }}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                items(sections, key = { it.groupId ?: "unassigned" }) { section ->
                    GroupReportCard(section, dateRange, canEditReports, onEditPublisher)
                }
            }
        }
        }
    }
}

@Composable
private fun GroupReportCard(
    section: GroupReportSection,
    dateRange: DateRange,
    canEditReports: Boolean,
    onEditPublisher: (personId: String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Group: ${section.groupName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Group Overseer: ${section.overseerName ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("Group Servant: ${section.servantName ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("Group Assistant: ${section.assistantName ?: "—"}", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            section.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.person.fullName, style = MaterialTheme.typography.titleSmall)
                        Text(row.category.displayLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Hours: ${"%.1f".format(row.totalHours)} · Bible Studies: ${row.totalBibleStudies} · " +
                                "Attended Preaching: ${row.attendedPreaching.toYesNoNa()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (canEditReports) {
                        IconButton(onClick = { onEditPublisher(row.person.id) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit this month's report")
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Summary Total for ${dateRange.periodLabel()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            section.categoryCounts.entries.sortedBy { it.key.displayLabel() }.forEach { (category, count) ->
                Text("Total ${category.displayLabel()}: $count", style = MaterialTheme.typography.bodySmall)
            }
            section.categoryHours.entries.sortedBy { it.key.displayLabel() }.forEach { (category, hours) ->
                Text("Total Hours for ${category.displayLabel()}: ${"%.1f".format(hours)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun Boolean?.toYesNoNa(): String = when (this) {
    true -> "Yes"
    false -> "No"
    null -> "N/A"
}

private fun DateRange.periodLabel(): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return "${dateFormat.format(Date(startMillis))} - ${dateFormat.format(Date(endMillis))}"
}

/** Shared shape for both Print and CSV export — "put a heading" (the title
 * plus the period line) is baked in here, same as before, now walking every
 * [GroupReportSection] in turn: a group header pseudo-row, that group's
 * publisher rows, then its own "SUMMARY TOTAL" pseudo-rows, before moving to
 * the next group — matches the on-screen grouping exactly so the export
 * never shows a different shape than what's on screen. */
private fun reportsTableFor(sections: List<GroupReportSection>, dateRange: DateRange): ReportTable {
    val periodLabel = dateRange.periodLabel()
    val columns = listOf("Status", "Publisher Name", "Hours", "Bible Studies", "Attended Preaching")
    val rows = mutableListOf<List<String>>()
    sections.forEach { section ->
        rows += listOf(
            "GROUP: ${section.groupName}",
            "Overseer: ${section.overseerName ?: "—"} | Servant: ${section.servantName ?: "—"} | Assistant: ${section.assistantName ?: "—"}",
            "", "", "",
        )
        section.rows.forEach { row ->
            rows += listOf(
                row.category.displayLabel(),
                row.person.fullName,
                "%.1f".format(row.totalHours),
                row.totalBibleStudies.toString(),
                row.attendedPreaching.toYesNoNa(),
            )
        }
        rows += listOf("SUMMARY TOTAL FOR THE PERIOD OF $periodLabel", "", "", "", "")
        section.categoryCounts.entries.sortedBy { it.key.displayLabel() }.forEach { (category, count) ->
            rows += listOf("Total ${category.displayLabel()}", count.toString(), "", "", "")
        }
        section.categoryHours.entries.sortedBy { it.key.displayLabel() }.forEach { (category, hours) ->
            rows += listOf("Total Hours for ${category.displayLabel()}", "%.1f".format(hours), "", "", "")
        }
        rows += listOf("", "", "", "", "")
    }
    val allRows = sections.flatMap { it.rows }
    return ReportTable(
        title = "GoPreach Publisher Reports ($periodLabel)",
        columns = columns,
        rows = rows,
        totals = listOf(
            "Bible Studies" to allRows.sumOf { it.totalBibleStudies }.toString(),
            "Hours" to "%.1f".format(allRows.sumOf { it.totalHours }),
        ),
    )
}
