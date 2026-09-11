package com.emfitsolutions.gopreach.ui.screens.householdervisithistory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.export.CsvExporter
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.screens.pipeline.PipelinePersonDetailScreen
import com.emfitsolutions.gopreach.ui.screens.pipeline.PipelineViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun PipelineStage.statusLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Found Interested"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

private fun Visit.statusLabel(): String = outcome.name.replace('_', ' ')

private fun formatGpsDecimal(lat: Double, lng: Double): String = "%.4f, %.4f".format(lat, lng)

/**
 * "House Holder Visit History" — a consolidated, search/filter/list layer
 * over the *existing* Searching/Return Visit/Bible Study records and their
 * Visit history, for Super-Admin (every authorized congregation) and
 * Publisher (their own congregation) accounts. Opening a record hands off to
 * the existing [PipelinePersonDetailScreen] unchanged (spec §8-§14's Add/
 * Edit/Delete-own-visit-only rules, and firestore.rules' matching `visits`
 * enforcement, already live there — see [HouseholderVisitHistoryViewModel]'s
 * own doc comment for why this screen never reimplements any of that).
 * [congregationId] is the actual security boundary — `null` (Super-Admin)
 * sees every congregation, a real id sees exactly that one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholderVisitHistoryScreen(
    congregationId: String?,
    currentPersonId: String,
    onBack: () -> Unit,
    viewModel: HouseholderVisitHistoryViewModel = hiltViewModel(),
    pipelineViewModel: PipelineViewModel = hiltViewModel(),
) {
    LaunchedEffect(congregationId) { viewModel.restrictTo(congregationId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val personNames by viewModel.personNames.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showToast = rememberActionToast()
    var selectedRow by remember { mutableStateOf<HouseholderRow?>(null) }

    val current = selectedRow
    if (current != null) {
        PipelinePersonDetailScreen(
            person = current.person,
            currentPersonId = currentPersonId,
            congregationName = current.congregationName,
            stage = current.person.pipelineStage,
            // Same "Super-Admin/explicitly-authorized-role override" every
            // other caller of this shared detail screen already uses —
            // `congregationId == null` is this app's established convention
            // for "this session is Super-Admin, unscoped" (see this
            // screen's own doc comment).
            canManageAllVisitHistory = congregationId == null,
            onBack = { selectedRow = null },
            viewModel = pipelineViewModel,
        )
        return
    }

    // "Add an export to Excel or PDF feature" (spec §21/§22) — one row per
    // Visit across every matching householder, retrieving ALL of that
    // person's history regardless of what's scrolled into view on screen
    // (rows/visits here are already the complete, unpaginated lists
    // [HouseholderVisitHistoryViewModel] builds). Coordinates as plain
    // decimal text, never a hyperlink/formula (spec §17/§21).
    val exportTable = remember(uiState.rows, personNames) {
        ReportTable(
            title = "House Holder Visit History",
            columns = listOf(
                "House Holder", "Status", "Assigned Publisher", "Congregation", "Address",
                "Province", "Municipality/City", "Barangay", "House Holder GPS",
                "Visit Date", "Visit Status", "Remarks", "Visit Coordinates", "Recorded By",
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
                    listOf(common + listOf("", "", "", "", ""))
                } else {
                    row.visits.map { visit ->
                        common + listOf(
                            dateFormat.format(Date(visit.visitDate)),
                            visit.statusLabel(),
                            visit.topicDiscussed.orEmpty(),
                            if (visit.hasVisitLocation) formatGpsDecimal(visit.visitLat!!, visit.visitLng!!) else "Not available",
                            personNames[visit.createdByPersonId] ?: "—",
                        )
                    }
                }
            },
            totals = listOf("Total House Holders" to uiState.rows.size.toString()),
        )
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                val wrote = CsvExporter.write(context, uri, exportTable.title, subtitle = null, columns = exportTable.columns, rows = exportTable.rows, totals = exportTable.totals)
                if (wrote) {
                    showToast("Exported to Excel (CSV).")
                    CsvExporter.openWithChooser(context, uri, "text/csv")
                } else {
                    showToast("Couldn't write the file.")
                }
            } catch (e: Exception) {
                showToast("Export failed: ${e.localizedMessage ?: "unknown error"}")
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
                SearchByDropdown(selected = uiState.searchByField, onSelected = viewModel::setSearchByField)
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    label = { Text("Search") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
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
                        HouseholderCard(row, onClick = { selectedRow = row })
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

/** "Simplify the Filter... ONE search/filter dropdown" — replaces the old
 * three cascading Province/Municipality/Barangay dropdowns. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchByDropdown(selected: SearchByField, onSelected: (SearchByField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Search By") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SearchByField.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
private fun HouseholderCard(row: HouseholderRow, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(row.person.pipelineStage.statusLabel(), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Assigned Publisher: ${row.publisherName ?: "Unassigned"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val location = listOfNotNull(row.person.barangay, row.person.cityMunicipality, row.person.province).joinToString(", ")
            if (location.isNotBlank()) {
                Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Unused placeholder to keep the [InterestedPerson] import meaningful for
 * IDE navigation from this file's own doc comments; the type itself is only
 * ever referenced through [HouseholderRow.person] above. */
private fun unusedTypeAnchor(person: InterestedPerson) = person
