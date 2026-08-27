package com.emfitsolutions.gopreach.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.emfitsolutions.gopreach.data.export.CsvExporter
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.charts.StatCard
import com.emfitsolutions.gopreach.ui.screens.home.isPioneerCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Consolidated Monthly Report" spec — reachable by Service Overseer,
 * Coordinator Elder, Admin (own congregation), and Super-Admin (every
 * congregation, with a picker — same convention as the Dashboard).
 * [visibleCongregationIds] is the actual access-control boundary, resolved
 * once by the caller from the session's own role (see GoPreachNavGraph),
 * exactly like every other scoped report in this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsolidatedReportScreen(
    visibleCongregationIds: Set<String>?,
    onBack: () -> Unit,
    viewModel: ConsolidatedReportViewModel = hiltViewModel(),
) {
    LaunchedEffect(visibleCongregationIds) { viewModel.restrictTo(visibleCongregationIds) }
    // Started only while this screen is open — see VisitRepository's doc
    // comment on why this isn't an app-wide listener.
    LaunchedEffect(Unit) { viewModel.startVisitSync() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPublisherId by remember { mutableStateOf<String?>(null) }

    // "Make all reports have a print preview [and] export as pdf or excel,
    // put a heading" — same shared ReportTable/CsvExporter/ReportPrinter
    // shape every other report screen uses.
    val context = LocalContext.current
    val reportTable = remember(uiState.visibleEntries, uiState.dateRange) { consolidatedReportTableFor(uiState) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            CsvExporter.write(context, uri, reportTable.title, subtitle = null, columns = reportTable.columns, rows = reportTable.rows, totals = reportTable.totals)
        }
    }
    val exportFileName = "gopreach-consolidated-report-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consolidated Monthly Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { ReportPrinter.print(context, reportTable) },
                        enabled = uiState.visibleEntries.isNotEmpty(),
                    ) { Icon(Icons.Rounded.Print, contentDescription = "Print") }
                    IconButton(
                        onClick = { exportLauncher.launch(exportFileName) },
                        enabled = uiState.visibleEntries.isNotEmpty(),
                    ) { Icon(Icons.Rounded.Share, contentDescription = "Export as CSV") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.congregationsInScope.size > 1) {
                        CongregationScopeDropdown(
                            congregationNames = uiState.congregationsInScope.map { it.id to it.name },
                            selectedId = uiState.selectedCongregationId,
                            onSelected = viewModel::selectCongregation,
                        )
                    }
                    DateRangeFilterBar(range = uiState.dateRange, onRangeChange = viewModel::setDateRange)
                    Text(
                        "${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(uiState.dateRange.startMillis))} – " +
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(uiState.dateRange.endMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Bible Studies", uiState.totalBibleStudies.toString(), onClick = {}, modifier = Modifier.weight(1f))
                        StatCard("Preaching Hours", "%.1f".format(uiState.totalPreachingHours), onClick = {}, modifier = Modifier.weight(1f))
                    }
                    if (uiState.regularPublisherEntries.isNotEmpty()) {
                        StatCard(
                            "Participated in Ministry",
                            "${uiState.participatedYesCount} / ${uiState.regularPublisherEntries.size}",
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    Text("Per Publisher", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap a publisher to see their Bible Study, Return Visit, and Preaching Time records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (uiState.isLoading) {
                item { Text("Loading…", style = MaterialTheme.typography.bodyMedium) }
            } else if (uiState.visibleEntries.isEmpty()) {
                item { Text("No publishers found in this scope.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(uiState.visibleEntries, key = { it.person.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedPublisherId = entry.person.id },
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.person.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${entry.category.name.replace('_', ' ')} · ${entry.congregationName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Bible Studies: ${entry.bibleStudiesCount}  ·  Return Visits: ${entry.returnVisitsCount}", style = MaterialTheme.typography.bodyMedium)
                        if (isPioneerCategory(entry.category)) {
                            Text("Preaching Hours: ${"%.1f".format(entry.preachingHours)}", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                "Participated in Ministry: ${entry.participatedInMinistry?.let { if (it) "YES" else "NO" } ?: "No report yet"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    val entry = uiState.entries.firstOrNull { it.person.id == selectedPublisherId }
    if (entry != null) {
        PublisherRecordsDialog(entry = entry, viewModel = viewModel, onDismiss = { selectedPublisherId = null })
    }
}

/** Shared shape for both Print and CSV export — "put a heading" (the title
 * plus the period line) is baked in here so it's identical for both. */
private fun consolidatedReportTableFor(uiState: ConsolidatedReportUiState): ReportTable {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    val periodLabel = "${dateFormat.format(Date(uiState.dateRange.startMillis))} - ${dateFormat.format(Date(uiState.dateRange.endMillis))}"
    return ReportTable(
        title = "GoPreach Consolidated Monthly Report ($periodLabel)",
        columns = listOf("Publisher", "Status", "Congregation", "Bible Studies", "Return Visits", "Preaching Hours", "Participated in Ministry"),
        rows = uiState.visibleEntries.map { entry ->
            listOf(
                entry.person.fullName,
                entry.category.name.replace('_', ' '),
                entry.congregationName,
                entry.bibleStudiesCount.toString(),
                entry.returnVisitsCount.toString(),
                if (isPioneerCategory(entry.category)) "%.1f".format(Locale.US, entry.preachingHours) else "N/A",
                if (isPioneerCategory(entry.category)) "N/A" else entry.participatedInMinistry?.let { if (it) "YES" else "NO" } ?: "No report yet",
            )
        },
        totals = listOf(
            "Total Bible Studies" to uiState.totalBibleStudies.toString(),
            "Total Preaching Hours" to "%.1f".format(Locale.US, uiState.totalPreachingHours),
            "Participated in Ministry" to "${uiState.participatedYesCount} / ${uiState.regularPublisherEntries.size}",
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CongregationScopeDropdown(congregationNames: List<Pair<String, String>>, selectedId: String?, onSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregationNames.firstOrNull { it.first == selectedId }?.second ?: "ALL CONGREGATIONS"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Congregation") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("ALL CONGREGATIONS") }, onClick = { onSelected(null); expanded = false })
            congregationNames.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelected(id); expanded = false })
            }
        }
    }
}

/** Spec items 3-5 — "see the record of Bible Study/Return Visit/Preaching
 * [Time] per publisher," read-only. Preaching Time only shown for a
 * Pioneer, matching the rest of this app's "not applicable to Regular/
 * Unbaptized Publishers" rule. */
@Composable
private fun PublisherRecordsDialog(
    entry: PublisherReportEntry,
    viewModel: ConsolidatedReportViewModel,
    onDismiss: () -> Unit,
) {
    val bibleStudiesFlow = remember(entry.person.id) { viewModel.bibleStudiesFor(entry.person.id) }
    val bibleStudies by bibleStudiesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val visitsFlow = remember(entry.person.id) { viewModel.visitsFor(entry.person.id) }
    val visits by visitsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val preachingFlow = remember(entry.person.id) { viewModel.preachingRecordsFor(entry.person.id) }
    val preachingRecords by preachingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.person.fullName) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Bible Study Records", style = MaterialTheme.typography.titleSmall)
                if (bibleStudies.isEmpty()) {
                    Text("None recorded.", style = MaterialTheme.typography.bodySmall)
                } else {
                    bibleStudies.forEach { record ->
                        Text("• ${record.name} — ${record.address}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Return Visit Records", style = MaterialTheme.typography.titleSmall)
                if (visits.isEmpty()) {
                    Text("None recorded.", style = MaterialTheme.typography.bodySmall)
                } else {
                    visits.sortedByDescending { it.visitDate }.forEach { visit ->
                        Text(
                            "• ${dateFormat.format(Date(visit.visitDate))} — ${visit.outcome.name.replace('_', ' ')}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (isPioneerCategory(entry.category)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Preaching Time Records", style = MaterialTheme.typography.titleSmall)
                    if (preachingRecords.isEmpty()) {
                        Text("None recorded.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        preachingRecords.sortedByDescending { it.date }.forEach { record ->
                            Text(
                                "• ${dateFormat.format(Date(record.date))} — ${"%.2f".format(record.hoursConsumed)} hours",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
