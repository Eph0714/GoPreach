package com.emfitsolutions.gopreach.ui.screens.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.export.CsvExporter
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.ui.components.DateRange
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.charts.BarSlice
import com.emfitsolutions.gopreach.ui.components.charts.SimpleBarChart
import com.emfitsolutions.gopreach.ui.components.charts.StatCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A fixed color code shared between the KPI cards and the donut chart, so
// "Regular Pioneers" (say) always reads as the same color everywhere on this
// dashboard — the explicit "add a color code" request.
private val COLOR_PUBLISHERS: Color get() = Color(0xFF1565C0)
private val COLOR_ELDERS: Color get() = Color(0xFF6A1B9A)
private val COLOR_REGULAR_PIONEER: Color get() = Color(0xFF2E7D32)
private val COLOR_AUXILIARY_PIONEER: Color get() = Color(0xFF66BB6A)
private val COLOR_HOURS: Color get() = Color(0xFFE0A526)

/** What the details dialog shows for one tapped [StatCard] — spec: "make the
 * button clickable... show the details inside when clicked." [breakdown] is
 * whatever sub-figures actually compose that headline number, when there
 * are any (e.g. Total Publishers breaks down into its categories); it's
 * empty for a figure with no further breakdown in this app's data model,
 * in which case the dialog just confirms the value, congregation, and
 * period it's for. */
private data class StatDetail(
    val label: String,
    val value: String,
    val breakdown: List<Pair<String, String>>,
)

/** Shared by [DashboardStatsContent] (the on-screen cards) and
 * [DashboardReportsScreen] (its PDF/Excel export) so the two can never list
 * a different set of figures — same source, one place this list is defined. */
private fun buildStatCards(displayed: CongregationStats): List<StatDetail> {
    val totalHours = displayed.regularPioneerHours + displayed.auxiliaryPioneerHours
    return listOf(
        StatDetail(
            "Total Publishers", displayed.totalPublishers.toString(),
            breakdown = listOf(
                "Regular Publishers" to displayed.regularPublishers.toString(),
                "Regular Pioneers" to displayed.regularPioneers.toString(),
                "Auxiliary Pioneers" to displayed.auxiliaryPioneers.toString(),
                "Unbaptized Publishers" to displayed.unbaptizedPublishers.toString(),
                "Inactive Publishers" to displayed.inactivePublishers.toString(),
            ),
        ),
        StatDetail("Total Elders", displayed.totalElders.toString(), emptyList()),
        StatDetail("Total Ministerial", displayed.totalMinisterial.toString(), emptyList()),
        StatDetail("Regular Pioneers", displayed.regularPioneers.toString(), emptyList()),
        StatDetail("Auxiliary Pioneers", displayed.auxiliaryPioneers.toString(), emptyList()),
        StatDetail("Unbaptized Publishers", displayed.unbaptizedPublishers.toString(), emptyList()),
        StatDetail("Inactive Publishers", displayed.inactivePublishers.toString(), emptyList()),
        StatDetail("Removed Publishers", displayed.removedPublishers.toString(), emptyList()),
        StatDetail("Bible Studies", displayed.totalBibleStudies.toString(), emptyList()),
        StatDetail(
            "Total Preaching Hours", "%.1f".format(totalHours),
            breakdown = listOf(
                "Regular Pioneer Hours" to "%.1f".format(displayed.regularPioneerHours),
                "Auxiliary Pioneer Hours" to "%.1f".format(displayed.auxiliaryPioneerHours),
            ),
        ),
    )
}

/**
 * "Role-Based Dashboard... Graphical Reports" spec §3-§5,§7,§8,§15 — KPI
 * cards + charts, with no `Scaffold`/`TopAppBar` of its own so it can be
 * embedded directly on a role's main dashboard body (see [AdminHomeScreen]'s
 * "graphical Summary in the main form" requirement), not just reached as a
 * separate screen. [DashboardReportsScreen] below wraps this exact same
 * content for the cases (Coordinator Elder, Regular Elder, ...) that still
 * navigate to it as its own destination.
 *
 * Scoped automatically to whatever congregation(s) the signed-in session is
 * authorized to see (enforced upstream, before this composable ever runs —
 * see [DashboardStatsViewModel]'s doc comment). A Super-Admin sees every
 * congregation plus a comparison bar chart; anyone else sees exactly their
 * own congregation's numbers, full stop — there is no congregation picker to
 * escape that scope with.
 *
 * The KPI row is a wrapping [FlowRow], not a horizontally-scrolling list —
 * every card is visible on the main form at once, per explicit request.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardStatsContent(
    visibleCongregationIds: Set<String>?,
    modifier: Modifier = Modifier,
    viewModel: DashboardStatsViewModel = hiltViewModel(),
) {
    LaunchedEffect(visibleCongregationIds) { viewModel.restrictTo(visibleCongregationIds) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMultiCongregation = uiState.all.size > 1

    if (uiState.isLoading) {
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }
        return
    }

    if (uiState.error != null) {
        Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
            Text("Dashboard statistics are temporarily unavailable.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Other GoPreach features are unaffected. (${uiState.error})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val displayed = uiState.selectedCongregationId?.let { id -> uiState.all.firstOrNull { it.congregationId == id } }
        ?: uiState.overallTotal ?: uiState.all.firstOrNull()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (isMultiCongregation) {
            Text("Congregation/Group", style = MaterialTheme.typography.titleSmall)
            var expanded by remember { mutableStateOf(false) }
            val selectedLabel = uiState.selectedCongregationId
                ?.let { id -> uiState.all.firstOrNull { it.congregationId == id }?.congregationName }
                ?: "ALL CONGREGATIONS"
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("ALL CONGREGATIONS") },
                        onClick = { viewModel.selectCongregation(null); expanded = false },
                    )
                    uiState.all.forEach { stats ->
                        DropdownMenuItem(
                            text = { Text(stats.congregationName) },
                            onClick = { viewModel.selectCongregation(stats.congregationId); expanded = false },
                        )
                    }
                }
            }
        }

        DateRangeFilterBar(
            range = uiState.dateRange,
            onRangeChange = viewModel::setDateRange,
        )
        Text(
            "Bible Studies and Preaching Hours reflect the selected period above. " +
                "Publisher/Elder counts always reflect current status. " +
                "\"Total Elders\" counts Coordinator Elders, Regular Elders, and Service Overseers, and " +
                "\"Total Ministerial\" counts Ministerial Servants — anyone holding more than one of " +
                "the roles within a card's own count is counted once.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (displayed == null) {
            Text("No congregation data available yet.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Column {
            Text(displayed.congregationName, style = MaterialTheme.typography.titleLarge)
            Text(
                "${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(uiState.dateRange.startMillis))} – " +
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(uiState.dateRange.endMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text("Overview", style = MaterialTheme.typography.titleMedium)
        // Per explicit request: no icons, no per-item color coding on these
        // cards — Total Publishers and Total Elders are also their own
        // separate cards here now, not one combined "Publishers vs Elders"
        // card with a shared proportion bar. Each is clickable and opens a
        // details dialog (spec: "show the details inside when clicked").
        val statCards = buildStatCards(displayed)
        var selectedDetail by remember { mutableStateOf<StatDetail?>(null) }
        // A fixed 2-column grid (not a wrapping FlowRow) — matches the
        // reference's "Accounts" section exactly: two equal-width cards per
        // row, regardless of screen width, rather than reflowing to 3+ on a
        // wider phone/tablet. Chunked manually (9 cards is small enough that
        // a LazyVerticalGrid would be more machinery than this needs).
        statCards.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { detail ->
                    StatCard(detail.label, detail.value, onClick = { selectedDetail = detail }, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }

        selectedDetail?.let { detail ->
            // Spec: tapping a card shows who actually makes up that number, not
            // just the number — e.g. "Total Elders 3 / Henry Canales (Solano
            // Tagalog Congregation), ...". Scoped to whichever congregation
            // [displayed] currently represents — a blank congregationId means
            // the "All Congregations" total, so every member counts there;
            // otherwise only that one congregation's members do. Same
            // deduplicated-by-person source [CongregationStats.compute] uses
            // for the headline number itself, so the list and the count can
            // never silently disagree.
            val matchingMembers = uiState.members
                .filter { detail.label in it.statLabels }
                .filter { displayed.congregationId.isBlank() || it.congregationId == displayed.congregationId }
                .sortedBy { it.fullName }
            AlertDialog(
                onDismissRequest = { selectedDetail = null },
                title = { Text(detail.label) },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).imePadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(detail.value, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${displayed.congregationName} · " +
                                "${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(uiState.dateRange.startMillis))} – " +
                                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(uiState.dateRange.endMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (detail.breakdown.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            detail.breakdown.forEach { (subLabel, subValue) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(subLabel, style = MaterialTheme.typography.bodyMedium)
                                    Text(subValue, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (matchingMembers.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            matchingMembers.forEach { member ->
                                Text(
                                    "${member.fullName} (${member.congregationName})",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedDetail = null }) { Text("Close") }
                },
            )
        }

        // "Publisher Status Breakdown" donut chart removed per explicit
        // request — the same per-category numbers (Regular Publisher/
        // Regular Pioneer/Auxiliary Pioneer/Unbaptized/Inactive) are still
        // available via the "Total Publishers" stat card's own tap-to-see
        // breakdown dialog above, so no data was lost, just this redundant
        // second visualization of it.

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Preaching Hours", style = MaterialTheme.typography.titleMedium)
                SimpleBarChart(
                    slices = listOf(
                        BarSlice("Regular Pioneers", displayed.regularPioneerHours.toFloat(), COLOR_REGULAR_PIONEER),
                        BarSlice("Auxiliary Pioneers", displayed.auxiliaryPioneerHours.toFloat(), COLOR_AUXILIARY_PIONEER),
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (isMultiCongregation && uiState.selectedCongregationId == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Publishers per Congregation/Group", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap a bar to drill into that congregation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SimpleBarChart(
                        slices = uiState.all.map { BarSlice(it.congregationName, it.totalPublishers.toFloat(), COLOR_PUBLISHERS) },
                        modifier = Modifier.padding(top = 8.dp),
                        onBarTap = { slice ->
                            uiState.all.firstOrNull { it.congregationName == slice.label }?.let { viewModel.selectCongregation(it.congregationId) }
                        },
                    )
                }
            }

            // "Group them per congregation in Super-Admin account" — explicit
            // request. Only a Super-Admin (or anyone else who happens to be
            // scoped to more than one congregation) ever sees this; an
            // Admin/Coordinator Elder is always scoped to exactly their own
            // congregation upstream (visibleCongregationIds — see
            // AdminHomeScreen/GoPreachNavGraph), so `isMultiCongregation` is
            // false for them and they never see anything beyond their own
            // congregation's single Total Elders card above.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Elders per Congregation/Group", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Regular Elders only. Tap a bar to drill into that congregation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SimpleBarChart(
                        slices = uiState.all.map { BarSlice(it.congregationName, it.totalElders.toFloat(), COLOR_ELDERS) },
                        modifier = Modifier.padding(top = 8.dp),
                        onBarTap = { slice ->
                            uiState.all.firstOrNull { it.congregationName == slice.label }?.let { viewModel.selectCongregation(it.congregationId) }
                        },
                    )
                }
            }
        }
    }
}

/** Standalone screen wrapper around [DashboardStatsContent] — used by roles
 * (Coordinator Elder, Regular Elder, ...) that still reach this as its own
 * destination rather than having it embedded on their main dashboard body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardReportsScreen(
    visibleCongregationIds: Set<String>?,
    /** "Allow the admin, super admin, coordinator elder, service overseer to
     * export the report to PDF or Excel" — Regular Elder/Ministerial Servant
     * can still view this screen (see GoPreachNavGraph's own drawer gating)
     * but don't get the export actions; wired from the nav graph based on
     * role, same pattern [ReportsScreen]'s own `canEditReports` uses. */
    canExport: Boolean = false,
    onBack: () -> Unit,
    viewModel: DashboardStatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showToast = rememberActionToast()

    // Same [displayed] derivation [DashboardStatsContent] uses internally —
    // duplicated here (not exposed from that composable) purely so the
    // export table always matches whatever congregation/period the cards on
    // screen are currently showing.
    val displayed = uiState.selectedCongregationId?.let { id -> uiState.all.firstOrNull { it.congregationId == id } }
        ?: uiState.overallTotal ?: uiState.all.firstOrNull()
    val reportTable = remember(displayed, uiState.dateRange) {
        displayed?.let { dashboardTableFor(it, uiState.dateRange) }
    }
    // Bug fix ("I cannot see any PDF or Excel"): see ReportsScreen's matching
    // fix — the Storage Access Framework picker just saves and closes with
    // no feedback of its own; now it confirms and opens the file immediately.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val table = reportTable
        if (uri != null && table != null) {
            try {
                val wrote = CsvExporter.write(context, uri, table.title, subtitle = null, columns = table.columns, rows = table.rows, totals = table.totals)
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
    val exportFileName = "gopreach-dashboard-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    // Data is already live (every source Flow updates this screen
                    // automatically — spec §15), so this is a reassurance affordance
                    // more than a functional necessity; kept per spec §3's explicit
                    // "refresh" requirement.
                    IconButton(onClick = { viewModel.selectCongregation(uiState.selectedCongregationId) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                    if (canExport) {
                        // Same "print preview always offers Save as PDF" +
                        // plain-CSV-for-Excel pair [ReportsScreen] already
                        // uses — no new export mechanism to maintain.
                        IconButton(
                            onClick = { reportTable?.let { ReportPrinter.print(context, it) } },
                            enabled = reportTable != null,
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export as PDF")
                        }
                        IconButton(
                            onClick = { exportLauncher.launch(exportFileName) },
                            enabled = reportTable != null,
                        ) {
                            Icon(Icons.Rounded.TableChart, contentDescription = "Export as Excel (CSV)")
                        }
                    }
                },
            )
        },
    ) { padding ->
        DashboardStatsContent(
            visibleCongregationIds = visibleCongregationIds,
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            viewModel = viewModel,
        )
    }
}

/** Shared shape for both Print/PDF and CSV/Excel export of the dashboard's
 * KPI cards — same [buildStatCards] list the on-screen cards themselves are
 * built from, so the exported report can never show different figures than
 * what's on screen. */
private fun dashboardTableFor(displayed: CongregationStats, dateRange: DateRange): ReportTable {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    val periodLabel = "${dateFormat.format(Date(dateRange.startMillis))} - ${dateFormat.format(Date(dateRange.endMillis))}"
    val cards = buildStatCards(displayed)
    return ReportTable(
        title = "GoPreach Dashboard Report — ${displayed.congregationName} ($periodLabel)",
        columns = listOf("Figure", "Value"),
        rows = cards.map { listOf(it.label, it.value) } +
            cards.flatMap { card -> card.breakdown.map { (label, value) -> listOf("  ${card.label} — $label", value) } },
    )
}
