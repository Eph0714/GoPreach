package com.emfitsolutions.gopreach.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.ui.components.charts.BarSlice
import com.emfitsolutions.gopreach.ui.components.charts.DonutChart
import com.emfitsolutions.gopreach.ui.components.charts.KpiCard
import com.emfitsolutions.gopreach.ui.components.charts.SimpleBarChart

// A fixed color code shared between the KPI cards and the donut chart, so
// "Regular Pioneers" (say) always reads as the same color everywhere on this
// dashboard — the explicit "add a color code" request.
private val COLOR_PUBLISHERS: Color get() = Color(0xFF1565C0)
private val COLOR_ELDERS: Color get() = Color(0xFF6A1B9A)
private val COLOR_REGULAR_PIONEER: Color get() = Color(0xFF2E7D32)
private val COLOR_AUXILIARY_PIONEER: Color get() = Color(0xFF66BB6A)
private val COLOR_UNBAPTIZED: Color get() = Color(0xFF6B9BD1)
private val COLOR_INACTIVE: Color get() = Color(0xFF9E9E9E)
private val COLOR_REMOVED: Color get() = Color(0xFFC63737)
private val COLOR_BIBLE_STUDIES: Color get() = Color(0xFF17A398)
private val COLOR_HOURS: Color get() = Color(0xFFE0A526)

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

    val displayed = uiState.selectedCongregationId?.let { id -> uiState.all.firstOrNull { it.congregationId == id } }
        ?: if (isMultiCongregation) CongregationStats.total(uiState.all) else uiState.all.firstOrNull()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (isMultiCongregation) {
            Text("Congregation", style = MaterialTheme.typography.titleSmall)
            var expanded by remember { mutableStateOf(false) }
            val selectedLabel = uiState.selectedCongregationId
                ?.let { id -> uiState.all.firstOrNull { it.congregationId == id }?.congregationName }
                ?: "All Congregations"
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
                        text = { Text("All Congregations") },
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

        if (displayed == null) {
            Text("No congregation data available yet.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Text(displayed.congregationName, style = MaterialTheme.typography.titleLarge)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpiCard("Total Publishers", displayed.totalPublishers.toString(), COLOR_PUBLISHERS)
            KpiCard("Total Elders", displayed.totalElders.toString(), COLOR_ELDERS)
            KpiCard("Regular Pioneers", displayed.regularPioneers.toString(), COLOR_REGULAR_PIONEER)
            KpiCard("Auxiliary Pioneers", displayed.auxiliaryPioneers.toString(), COLOR_AUXILIARY_PIONEER)
            KpiCard("Unbaptized Publishers", displayed.unbaptizedPublishers.toString(), COLOR_UNBAPTIZED)
            KpiCard("Inactive Publishers", displayed.inactivePublishers.toString(), COLOR_INACTIVE)
            KpiCard("Removed Publishers", displayed.removedPublishers.toString(), COLOR_REMOVED)
            KpiCard("Bible Studies", displayed.totalBibleStudies.toString(), COLOR_BIBLE_STUDIES)
            KpiCard("Total Preaching Hours", "%.1f".format(displayed.regularPioneerHours + displayed.auxiliaryPioneerHours), COLOR_HOURS)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Publisher Status Breakdown", style = MaterialTheme.typography.titleMedium)
                DonutChart(
                    slices = listOf(
                        BarSlice("Regular Pioneer", displayed.regularPioneers.toFloat(), COLOR_REGULAR_PIONEER),
                        BarSlice("Auxiliary Pioneer", displayed.auxiliaryPioneers.toFloat(), COLOR_AUXILIARY_PIONEER),
                        BarSlice("Regular Publisher", displayed.regularPublishers.toFloat(), COLOR_PUBLISHERS),
                        BarSlice("Unbaptized", displayed.unbaptizedPublishers.toFloat(), COLOR_UNBAPTIZED),
                        BarSlice("Inactive", displayed.inactivePublishers.toFloat(), COLOR_INACTIVE),
                    ).filter { it.value > 0f },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

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
                    Text("Publishers per Congregation", style = MaterialTheme.typography.titleMedium)
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
    onBack: () -> Unit,
    viewModel: DashboardStatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
