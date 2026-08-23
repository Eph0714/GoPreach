package com.emfitsolutions.gopreach.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.ui.components.charts.BarSlice
import com.emfitsolutions.gopreach.ui.components.charts.DonutChart
import com.emfitsolutions.gopreach.ui.components.charts.KpiCard
import com.emfitsolutions.gopreach.ui.components.charts.SimpleBarChart

/**
 * "Role-Based Dashboard... Graphical Reports" spec §3-§5,§7,§8 — KPI cards +
 * charts, scoped automatically to whatever congregation(s) the signed-in
 * session is authorized to see (enforced upstream, before this screen ever
 * runs — see [DashboardStatsViewModel]'s doc comment). A Super-Admin sees
 * every congregation plus a comparison bar chart; anyone else sees exactly
 * their own congregation's numbers, full stop — there is no congregation
 * picker to escape that scope with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardReportsScreen(
    visibleCongregationIds: Set<String>?,
    onBack: () -> Unit,
    viewModel: DashboardStatsViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(visibleCongregationIds) { viewModel.restrictTo(visibleCongregationIds) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMultiCongregation = uiState.all.size > 1

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
        if (uiState.isLoading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        val displayed = uiState.selectedCongregationId?.let { id -> uiState.all.firstOrNull { it.congregationId == id } }
            ?: if (isMultiCongregation) CongregationStats.total(uiState.all) else uiState.all.firstOrNull()

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (isMultiCongregation) {
                Text("Congregation", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCongregationId == null,
                            onClick = { viewModel.selectCongregation(null) },
                            label = { Text("All Congregations") },
                        )
                    }
                    items(uiState.all, key = { it.congregationId }) { stats ->
                        FilterChip(
                            selected = uiState.selectedCongregationId == stats.congregationId,
                            onClick = { viewModel.selectCongregation(stats.congregationId) },
                            label = { Text(stats.congregationName) },
                        )
                    }
                }
            }

            if (displayed == null) {
                Text("No congregation data available yet.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            Text(displayed.congregationName, style = MaterialTheme.typography.titleLarge)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { KpiCard("Total Publishers", displayed.totalPublishers.toString()) }
                item { KpiCard("Total Elders", displayed.totalElders.toString()) }
                item { KpiCard("Regular Pioneers", displayed.regularPioneers.toString()) }
                item { KpiCard("Auxiliary Pioneers", displayed.auxiliaryPioneers.toString()) }
                item { KpiCard("Unbaptized Publishers", displayed.unbaptizedPublishers.toString()) }
                item { KpiCard("Inactive Publishers", displayed.inactivePublishers.toString()) }
                item { KpiCard("Removed Publishers", displayed.removedPublishers.toString()) }
                item { KpiCard("Bible Studies", displayed.totalBibleStudies.toString()) }
                item { KpiCard("Total Preaching Hours", "%.1f".format(displayed.regularPioneerHours + displayed.auxiliaryPioneerHours)) }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Publisher Status Breakdown", style = MaterialTheme.typography.titleMedium)
                    DonutChart(
                        slices = listOf(
                            BarSlice("Regular Pioneer", displayed.regularPioneers.toFloat(), Color(0xFF0E8F2E)),
                            BarSlice("Auxiliary Pioneer", displayed.auxiliaryPioneers.toFloat(), Color(0xFF4CBE6C)),
                            BarSlice("Regular Publisher", displayed.regularPublishers.toFloat(), Color(0xFFE0A526)),
                            BarSlice("Unbaptized", displayed.unbaptizedPublishers.toFloat(), Color(0xFF6B9BD1)),
                            BarSlice("Inactive", displayed.inactivePublishers.toFloat(), Color(0xFF9E9E9E)),
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
                            BarSlice("Regular Pioneers", displayed.regularPioneerHours.toFloat(), Color(0xFF0E8F2E)),
                            BarSlice("Auxiliary Pioneers", displayed.auxiliaryPioneerHours.toFloat(), Color(0xFF4CBE6C)),
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
                            slices = uiState.all.map { BarSlice(it.congregationName, it.totalPublishers.toFloat(), Color(0xFF0E8F2E)) },
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
}
