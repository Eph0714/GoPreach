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
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Card
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.export.CsvExporter
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.ui.components.DateRange
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "For Admin, Elders and Service overseer select [All Group, Per Group]
 * filter in report" — [PER_GROUP] additionally needs one Group actually
 * picked (see [ReportsScreen]'s own `selectedGroupId`); [ALL_GROUPS] shows
 * every Group's section combined, same as before this filter existed. */
private enum class GroupFilterMode { ALL_GROUPS, PER_GROUP }

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
    // "Select a congregation for Super Admin" — visibleCongregationId is
    // only ever null for Super-Admin (every other role is already fixed to
    // their own congregation upstream in GoPreachNavGraph, the actual
    // security boundary); this screen-local pick just narrows Super-Admin's
    // own already-unrestricted view, the same convention ManagePublishers
    // Screen's own CongregationFilterDropdown already uses. `null` here
    // means "All Congregations," same default as before this filter
    // existed.
    var congregationFilter by remember { mutableStateOf<String?>(null) }
    val effectiveCongregationId = visibleCongregationId ?: congregationFilter
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()

    // "For Admin, Elders and Service overseer select [All Group, Per Group]
    // filter" — generalizes the old Regular-Elder-only "My Group/My
    // Congregation" toggle into a picker every role scoped to one
    // congregation can use, not just Regular Elder. Defaults to PER_GROUP
    // pre-selected to [visibleGroupId] when the caller provided one (a
    // Regular Elder's own group — the same default the old toggle already
    // gave them), ALL_GROUPS otherwise.
    var groupFilterMode by remember { mutableStateOf(if (visibleGroupId != null) GroupFilterMode.PER_GROUP else GroupFilterMode.ALL_GROUPS) }
    var selectedGroupId by remember { mutableStateOf(visibleGroupId) }
    val groupsFlow = remember(effectiveCongregationId) { viewModel.groupsFor(effectiveCongregationId) }
    val groups by groupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // "All Congregations" (no single congregation chosen yet) has no single
    // Group list to filter by — Per Group only makes sense once exactly one
    // congregation is in view.
    val canFilterByGroup = effectiveCongregationId != null
    val effectiveGroupId = if (canFilterByGroup && groupFilterMode == GroupFilterMode.PER_GROUP) selectedGroupId else null
    // Falling back to "All Congregations" (Super-Admin) drops any Per Group
    // pick along with it — there's no single Group list to have kept it
    // pointed at.
    LaunchedEffect(canFilterByGroup) {
        if (!canFilterByGroup) groupFilterMode = GroupFilterMode.ALL_GROUPS
    }

    // "Main Form Date Range Filtering" spec §7/§4/§8 — This Month by default
    // (calculated live from the current date), shared with the Dashboard's
    // own Reports screen via DateRangeStore rather than screen-local state,
    // so navigating between the two never resets the selection. Today/This
    // Week/This Month/This Year/Custom (From, To) are all offered by
    // [DateRangeFilterBar] below — nothing extra needed here for that.
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()

    val rowsFlow = remember(effectiveCongregationId, effectiveGroupId, dateRange) {
        viewModel.rowsFor(effectiveCongregationId, effectiveGroupId, dateRange)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // "Group the record by Group" — the same rows above, bucketed into
    // sections with their own Overseer/Servant/Assistant header and a
    // per-group summary total.
    val sectionsFlow = remember(effectiveCongregationId, effectiveGroupId, dateRange) {
        viewModel.groupedRowsFor(effectiveCongregationId, effectiveGroupId, dateRange)
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
                    // Dedicated PDF/Excel icons — distinct from the generic
                    // Print/Share glyphs, so the two actions read at a glance
                    // as "export as PDF" and "export as Excel" specifically.
                    IconButton(onClick = { ReportPrinter.print(context, reportTable) }) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export as PDF")
                    }
                    IconButton(onClick = { exportLauncher.launch(exportFileName) }) {
                        Icon(Icons.Rounded.TableChart, contentDescription = "Export as Excel (CSV)")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // "Select a congregation for Super Admin" — the only role that ever
        // reaches this screen with visibleCongregationId == null.
        if (visibleCongregationId == null) {
            ReportsCongregationDropdown(
                congregations = congregations,
                selectedId = congregationFilter,
                onSelected = { newCongregationId ->
                    congregationFilter = newCongregationId
                    // A Group picked under the previous congregation almost
                    // never belongs to the new one — reset rather than leave
                    // Per Group silently pointed at a stale/foreign Group.
                    selectedGroupId = null
                    groupFilterMode = GroupFilterMode.ALL_GROUPS
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        // "For Admin, Elders and Service overseer select [All Group, Per
        // Group] filter in report" — Super-Admin gets it too, once they've
        // narrowed to one congregation above (canFilterByGroup); there's
        // nothing role-specific about which of the four groups within a
        // congregation someone may look at once they can already see that
        // whole congregation's combined report.
        if (canFilterByGroup) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedButton(
                    selected = groupFilterMode == GroupFilterMode.ALL_GROUPS,
                    onClick = { groupFilterMode = GroupFilterMode.ALL_GROUPS },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("All Groups") }
                SegmentedButton(
                    selected = groupFilterMode == GroupFilterMode.PER_GROUP,
                    onClick = { groupFilterMode = GroupFilterMode.PER_GROUP },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Per Group") }
            }
            if (groupFilterMode == GroupFilterMode.PER_GROUP) {
                ReportsGroupDropdown(
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    onSelected = { selectedGroupId = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
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

/** "Select a congregation for Super Admin" — same shape
 * ManagePublishersScreen's own CongregationFilterDropdown already uses;
 * `null` selection means "All Congregations" (this screen's original,
 * unfiltered default). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsCongregationDropdown(
    congregations: List<Congregation>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: "All Congregations"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
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
            DropdownMenuItem(text = { Text("All Congregations") }, onClick = { onSelected(null); expanded = false })
            congregations.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelected(c.id); expanded = false })
            }
        }
    }
}

/** "Per Group" — which Group, within whichever congregation is currently in
 * view, to narrow the report down to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsGroupDropdown(
    groups: List<Group>,
    selectedGroupId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "Select a Group"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (groups.isEmpty()) {
                DropdownMenuItem(text = { Text("No groups yet") }, onClick = {}, enabled = false)
            }
            groups.forEach { g ->
                DropdownMenuItem(text = { Text(g.name) }, onClick = { onSelected(g.id); expanded = false })
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
