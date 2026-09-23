package com.emfitsolutions.gopreach.ui.screens.planner

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.export.ComparativeReportPdfExporter
import com.emfitsolutions.gopreach.ui.components.charts.LineSeries
import com.emfitsolutions.gopreach.ui.components.charts.MultiSeriesLineChart
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** My Planner → Compare — "Add a multi-month Comparative Report... Compare
 * Hours, Minutes, Return Visits, and Bible Studies... Add an animated
 * color-coded Line Graph. Allow printing and exporting." A Start/End month
 * picker (reusing [PeriodListDialog], the same jump-to-period list every
 * other Planner view now uses), a compact bordered table, the animated
 * chart, then Export as PDF. */
@Composable
internal fun PlannerComparativeContent(currentPersonId: String, viewModel: PlannerComparativeViewModel = hiltViewModel()) {
    val startMonth by viewModel.startMonth.collectAsStateWithLifecycle()
    val endMonth by viewModel.endMonth.collectAsStateWithLifecycle()
    val state by remember(currentPersonId) { viewModel.stateFor(currentPersonId) }.collectAsStateWithLifecycle()
    val monthFormat = remember { SimpleDateFormat("MMM yyyy", Locale.getDefault()) }
    val context = LocalContext.current

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    // The last 36 months, oldest first — the same lookback window
    // [PlannerComparativeViewModel.monthsBetween] caps a picked range to.
    val pickableMonths = remember {
        (35 downTo 0).map { monthsAgo ->
            Calendar.getInstance().apply {
                add(Calendar.MONTH, -monthsAgo)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(PlannerBodyPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Comparative Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Compare Hours, Return Visits, and Bible Studies across any range of months.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ValueEditRow(label = "Start Month", value = monthFormat.format(Date(startMonth)), onEdit = { showStartPicker = true })
        ValueEditRow(label = "End Month", value = monthFormat.format(Date(endMonth)), onEdit = { showEndPicker = true })

        if (state.rows.isEmpty()) {
            PlannerEmptyHint("No data in this range yet.")
        } else {
            MultiSeriesLineChart(
                series = listOf(
                    LineSeries("Hours", PlannerAccent.Hours, state.rows.map { it.totalMinutes / 60f }),
                    LineSeries("Return Visits", PlannerAccent.ReturnVisits, state.rows.map { it.returnVisitCount.toFloat() }),
                    LineSeries("Bible Studies", PlannerAccent.BibleStudies, state.rows.map { it.bibleStudyCount.toFloat() }),
                ),
                xLabels = state.rows.map { SimpleDateFormat("MMM", Locale.getDefault()).format(Date(it.monthStart)) },
            )

            ComparativeTable(rows = state.rows, monthFormat = monthFormat)

            Button(
                onClick = { ComparativeReportPdfExporter.export(context, state.rows) },
                colors = ButtonDefaults.buttonColors(containerColor = PlannerAccent.Hours),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export as PDF")
            }
        }
    }

    if (showStartPicker) {
        PeriodListDialog(
            title = "Select Start Month",
            items = pickableMonths.map { monthFormat.format(Date(it)) to null },
            onDismiss = { showStartPicker = false },
            onSelect = { index -> viewModel.setStartMonth(pickableMonths[index]) },
        )
    }
    if (showEndPicker) {
        PeriodListDialog(
            title = "Select End Month",
            items = pickableMonths.map { monthFormat.format(Date(it)) to null },
            onDismiss = { showEndPicker = false },
            onSelect = { index -> viewModel.setEndMonth(pickableMonths[index]) },
        )
    }
}

/** "Calendar table grid with visible borders" (the same rule the Monthly
 * Planner/Report specs keep repeating) — a bordered Month/Hours/Return
 * Visits/Bible Studies grid, compact rows, consistent column widths. */
@Composable
private fun ComparativeTable(rows: List<ComparativeMonthRow>, monthFormat: SimpleDateFormat) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(4.dp)) {
            TableRow(listOf("Month", "Hours", "Return Visits", "Bible Studies"), emphasize = true, borderColor = borderColor)
            HorizontalDivider(color = borderColor)
            rows.forEach { row ->
                val hours = row.totalMinutes / 60
                val minutes = row.totalMinutes % 60
                TableRow(
                    listOf(monthFormat.format(Date(row.monthStart)), "${hours}h ${minutes}m", row.returnVisitCount.toString(), row.bibleStudyCount.toString()),
                    emphasize = false,
                    borderColor = borderColor,
                )
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, emphasize: Boolean, borderColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 0.5.dp, color = borderColor)
            .padding(vertical = 8.dp, horizontal = 6.dp),
    ) {
        cells.forEach { cell ->
            Text(
                cell,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
