package com.emfitsolutions.gopreach.ui.screens.householdervisithistory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.export.CsvExporter
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.ui.components.openCoordinatesInMaps
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun PipelineStage.statusLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Found Interested"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

private fun Visit.statusLabel(): String = outcome.name.replace('_', ' ')

/** Plain decimal GPS text, exactly as spec §17's own example
 * ("16.5198, 121.1842") — distinct from [com.emfitsolutions.gopreach.data
 * .location.formatCoordinatesDms], which this app's other screens use for a
 * degrees/minutes/seconds display; that format isn't what was asked for
 * here, on-screen or exported. */
private fun formatGpsDecimal(lat: Double, lng: Double): String = "%.4f, %.4f".format(lat, lng)

/**
 * "House Holder Visit History" — spec: a consolidated, read-only view of the
 * *existing* Searching/Return Visit/Bible Study records and their Visit
 * history, for Super-Admin (every authorized congregation) and Publisher
 * (their own congregation) accounts. Nothing here writes anything; see
 * [HouseholderVisitHistoryViewModel]'s own doc comment for why. [congregationId]
 * is the actual security boundary — `null` (Super-Admin) sees every
 * congregation, a real id sees exactly that one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholderVisitHistoryScreen(
    congregationId: String?,
    onBack: () -> Unit,
    viewModel: HouseholderVisitHistoryViewModel = hiltViewModel(),
) {
    LaunchedEffect(congregationId) { viewModel.restrictTo(congregationId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showToast = rememberActionToast()

    // "add an export to Excel or PDF feature" (same convention as every
    // other export in this app) — one row per Visit (a householder with no
    // visits yet still gets one row, visit columns blank) so the exported
    // file is a flat, spreadsheet-friendly table rather than a nested shape
    // CSV/PDF can't represent. GPS is plain decimal text in its own column —
    // spec §17: never a hyperlink, map URL, or formula.
    val exportTable = remember(uiState.rows) {
        ReportTable(
            title = "House Holder Visit History",
            columns = listOf(
                "House Holder", "Record Type", "Assigned Publisher", "Congregation", "Address",
                "Province", "Municipality/City", "Barangay", "GPS",
                "Visit Date", "Visit Status", "Remarks",
            ),
            rows = uiState.rows.flatMap { row ->
                val common = listOf(
                    row.person.name,
                    row.person.pipelineStage.statusLabel(),
                    row.publisherName ?: "Unassigned",
                    row.congregationName,
                    row.person.address,
                    row.person.province.orEmpty(),
                    row.person.cityMunicipality.orEmpty(),
                    row.person.barangay.orEmpty(),
                    if (row.person.hasGpsLocation) formatGpsDecimal(row.person.gpsLat!!, row.person.gpsLng!!) else "",
                )
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                if (row.visits.isEmpty()) {
                    listOf(common + listOf("", "", ""))
                } else {
                    row.visits.map { visit ->
                        common + listOf(dateFormat.format(Date(visit.visitDate)), visit.statusLabel(), visit.topicDiscussed.orEmpty())
                    }
                }
            },
            totals = listOf("Total House Holders" to uiState.rows.size.toString()),
        )
    }
    val exportCsvSuccess = "Exported to Excel (CSV)."
    val exportFailedGeneric = "Export failed: %s"
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                val wrote = CsvExporter.write(context, uri, exportTable.title, subtitle = null, columns = exportTable.columns, rows = exportTable.rows, totals = exportTable.totals)
                if (wrote) {
                    showToast(exportCsvSuccess)
                    CsvExporter.openWithChooser(context, uri, "text/csv")
                } else {
                    showToast("Couldn't write the file.")
                }
            } catch (e: Exception) {
                showToast(exportFailedGeneric.format(e.localizedMessage ?: "unknown error"))
            }
        }
    }
    val exportFileName = "gopreach-householder-visit-history-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("House Holder Visit History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { ReportPrinter.print(context, exportTable) }, enabled = uiState.rows.isNotEmpty()) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export as PDF")
                    }
                    IconButton(onClick = { exportLauncher.launch(exportFileName) }, enabled = uiState.rows.isNotEmpty()) {
                        Icon(Icons.Rounded.TableChart, contentDescription = "Export as Excel")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecordTypeDropdown(selected = uiState.recordType, onSelected = viewModel::setRecordType)
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    label = { Text("House Holder Name") },
                    placeholder = { Text("All (or search by name)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                LocationDropdown(
                    label = "Province",
                    selected = uiState.province,
                    options = viewModel.provinceOptions(uiState),
                    onSelected = viewModel::setProvince,
                )
                LocationDropdown(
                    label = "Municipality / City",
                    selected = uiState.municipality,
                    options = viewModel.municipalityOptions(uiState),
                    onSelected = viewModel::setMunicipality,
                )
                LocationDropdown(
                    label = "Barangay",
                    selected = uiState.barangay,
                    options = viewModel.barangayOptions(uiState),
                    onSelected = viewModel::setBarangay,
                )
            }
            HorizontalDivider()
            if (uiState.rows.isEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("No house holder records found.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.rows, key = { it.person.id }) { row ->
                        HouseholderCard(row)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTypeDropdown(selected: RecordTypeFilter, onSelected: (RecordTypeFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Record Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecordTypeFilter.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

/** One "All" + every currently-reachable value dropdown — shared shape for
 * Province/Municipality/Barangay (spec §5/§6's cascading behavior: each
 * level's own options already come pre-scoped to whatever the level above it
 * picked, via [HouseholderVisitHistoryViewModel]'s own `xOptions` functions). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationDropdown(label: String, selected: String?, options: List<String>, onSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: "All",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelected(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
private fun HouseholderCard(row: HouseholderRow) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${row.person.pipelineStage.statusLabel()} · ${row.congregationName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
            }
            if (expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Assigned Publisher", row.publisherName ?: "Unassigned")
                    row.person.gender?.let { InfoRow("Gender", it.name) }
                    row.person.spouse?.takeIf { it.isNotBlank() }?.let { InfoRow("Spouse", it) }
                    InfoRow("Address", row.person.address)
                    row.person.children?.takeIf { it.isNotBlank() }?.let { InfoRow("Children", it) }
                    row.person.religion?.takeIf { it.isNotBlank() }?.let { InfoRow("Religion", it) }
                    row.person.ageYears?.let { InfoRow("Age", it.toString()) }
                    row.person.placeOrigin?.takeIf { it.isNotBlank() }?.let { InfoRow("Place Origin", it) }
                    row.person.language?.takeIf { it.isNotBlank() }?.let { InfoRow("Language", it) }
                    row.person.literaturePlace?.takeIf { it.isNotBlank() }?.let { InfoRow("Literature Place", it) }
                    if (row.person.hasGpsLocation) {
                        val lat = row.person.gpsLat!!
                        val lng = row.person.gpsLng!!
                        Column {
                            Text("GPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatGpsDecimal(lat, lng),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { openCoordinatesInMaps(context, lat, lng, row.person.name) },
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Visit History", style = MaterialTheme.typography.titleSmall)
                    if (row.visits.isEmpty()) {
                        Text(
                            "No visit history recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
                        row.visits.forEachIndexed { index, visit ->
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(dateFormat.format(Date(visit.visitDate)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Status: ${visit.statusLabel()}", style = MaterialTheme.typography.bodySmall)
                                if (!visit.topicDiscussed.isNullOrBlank()) {
                                    Text("Remarks: ${visit.topicDiscussed}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (index != row.visits.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
