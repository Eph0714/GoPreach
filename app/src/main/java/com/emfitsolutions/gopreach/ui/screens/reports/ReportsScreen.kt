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
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.ui.components.DateRange
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Spec §5.1 — Total Bible Studies / Interested People / preaching hours, per
 * publisher, with an "All Publishers" summary at the top. */
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
    // so navigating between the two never resets the selection.
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()

    val rowsFlow = remember(visibleCongregationId, effectiveGroupId, dateRange) {
        viewModel.rowsFor(visibleCongregationId, effectiveGroupId, dateRange)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // "Make all reports have a print preview [and] export as pdf or excel,
    // put a heading" — safe to always offer: it's the exact same rows
    // already visible on screen, not a new data exposure, so there's no
    // separate Permission gate the way Add/Edit/Delete have via [readOnly]
    // elsewhere.
    val context = LocalContext.current
    val reportTable = remember(rows, dateRange) { reportsTableFor(rows, dateRange) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            CsvExporter.write(context, uri, reportTable.title, subtitle = null, columns = reportTable.columns, rows = reportTable.rows, totals = reportTable.totals)
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
                    // Print preview — Android's own print dialog always shows
                    // one before anything prints, and offers "Save as PDF"
                    // out of the box.
                    IconButton(onClick = { ReportPrinter.print(context, reportTable) }, enabled = rows.isNotEmpty()) {
                        Icon(Icons.Rounded.Print, contentDescription = "Print")
                    }
                    IconButton(onClick = { exportLauncher.launch(exportFileName) }, enabled = rows.isNotEmpty()) {
                        Icon(Icons.Rounded.Share, contentDescription = "Export as CSV")
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
                items(rows, key = { it.person.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                Text("Bible Studies: ${row.totalBibleStudies}", style = MaterialTheme.typography.bodySmall)
                                Text("Hours: ${"%.1f".format(row.totalHours)}", style = MaterialTheme.typography.bodySmall)
                                Text("Interested People: ${row.totalInterestedPeople}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (canEditReports) {
                                IconButton(onClick = { onEditPublisher(row.person.id) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit this month's report")
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** Shared shape for both Print and CSV export — "put a heading" (the title
 * plus the period line) is baked in here so it's identical for both. */
private fun reportsTableFor(rows: List<PublisherReportRow>, dateRange: DateRange): ReportTable {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    val periodLabel = "${dateFormat.format(Date(dateRange.startMillis))} - ${dateFormat.format(Date(dateRange.endMillis))}"
    return ReportTable(
        title = "GoPreach Publisher Reports ($periodLabel)",
        columns = listOf("Publisher", "Bible Studies", "Hours", "Interested People"),
        rows = rows.map { row ->
            listOf(row.person.fullName, row.totalBibleStudies.toString(), row.totalHours.toString(), row.totalInterestedPeople.toString())
        },
        totals = listOf(
            "Bible Studies" to rows.sumOf { it.totalBibleStudies }.toString(),
            "Hours" to rows.sumOf { it.totalHours }.toString(),
            "Interested People" to rows.sumOf { it.totalInterestedPeople }.toString(),
        ),
    )
}
