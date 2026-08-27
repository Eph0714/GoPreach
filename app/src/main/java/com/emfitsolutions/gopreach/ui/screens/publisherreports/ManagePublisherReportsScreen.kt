package com.emfitsolutions.gopreach.ui.screens.publisherreports

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.QuickDateRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Manage Publisher Report" module. [fixedCongregationId] is the security
 * boundary, resolved by the caller from the enrolling session's own role
 * (null means Super-Admin — every congregation); [canPermanentlyDelete] is
 * Super-Admin-only, same convention as every other Manage screen.
 * [readOnly] hides Edit/Unlock (and Delete, on top of [canPermanentlyDelete])
 * — a grant-based Circuit Overseer with a report-view permission reaches
 * this screen but can never edit through it, since firestore.rules blocks
 * every restricted user's `monthlyReports` write regardless of permission
 * (see AdminHomeScreen.canManagePublisherReports's doc comment).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePublisherReportsScreen(
    currentPersonId: String,
    fixedCongregationId: String?,
    canPermanentlyDelete: Boolean,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    viewModel: ManagePublisherReportsViewModel = hiltViewModel(),
) {
    LaunchedEffect(fixedCongregationId) { viewModel.restrictTo(fixedCongregationId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingEdit by remember { mutableStateOf<PublisherReportRow?>(null) }
    var pendingDelete by remember { mutableStateOf<PublisherReportRow?>(null) }

    val reportTitle = remember(uiState.dateRange) { reportTitleFor(uiState.dateRange.startMillis, uiState.dateRange.endMillis, uiState.dateRange.option) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publisher Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            ReportPrinter.print(context, reportTitle, uiState.rows, uiState.totalBibleStudies, uiState.totalHoursByPioneers)
                        },
                        enabled = uiState.rows.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.Print, contentDescription = "Print")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateRangeFilterBar(range = uiState.dateRange, onRangeChange = viewModel::setDateRange)

                Text("Show", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.showMode == ReportShowMode.ALL,
                        onClick = { viewModel.setShowMode(ReportShowMode.ALL) },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = uiState.showMode == ReportShowMode.BY_PUBLISHER,
                        onClick = { viewModel.setShowMode(ReportShowMode.BY_PUBLISHER) },
                        label = { Text("By Publisher") },
                    )
                    // Super-Admin only — an Admin/Coordinator Elder/Service
                    // Overseer is already scoped to exactly one congregation
                    // ([fixedCongregationId] non-null), so this option would
                    // be a no-op for them.
                    if (fixedCongregationId == null) {
                        FilterChip(
                            selected = uiState.showMode == ReportShowMode.BY_CONGREGATION,
                            onClick = { viewModel.setShowMode(ReportShowMode.BY_CONGREGATION) },
                            label = { Text("By Congregation") },
                        )
                    }
                }

                if (uiState.showMode == ReportShowMode.BY_PUBLISHER) {
                    PublisherPickerDropdown(
                        publishers = uiState.publishersInScope,
                        selectedId = uiState.selectedPublisherId,
                        onSelected = viewModel::selectPublisher,
                    )
                }
                if (uiState.showMode == ReportShowMode.BY_CONGREGATION) {
                    CongregationPickerDropdown(
                        congregations = uiState.congregationsInScope,
                        selectedId = uiState.selectedCongregationId,
                        onSelected = viewModel::selectCongregation,
                    )
                }

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    label = { Text("Search: Name, Status" + if (fixedCongregationId == null) ", Congregation" else "") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (uiState.rows.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No reports for this period.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    reportTitle,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.rows, key = { it.report.id }) { row ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                    Row {
                                        if (!readOnly) {
                                            IconButton(onClick = { pendingEdit = row }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit report")
                                            }
                                            if (row.isLocked) {
                                                IconButton(onClick = { viewModel.unlock(row.report, currentPersonId) }) {
                                                    Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock for publisher")
                                                }
                                            }
                                        }
                                        if (canPermanentlyDelete && !readOnly) {
                                            IconButton(onClick = { pendingDelete = row }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete report")
                                            }
                                        }
                                    }
                                }
                                Text("Status: ${row.category.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Bible Study: ${row.report.bibleStudiesCount}    " +
                                        (if (row.isPioneer) "Hours: ${row.report.hoursRendered ?: 0.0}" else "Participate in Preaching: ${if (row.report.participatedInPreaching == true) "YES" else "NO"}"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text("Congregation: ${row.congregationName}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (row.isLocked) "Locked" else "Unlocked — editable by the publisher",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (row.isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Total Bible Study: ${uiState.totalBibleStudies}", style = MaterialTheme.typography.bodyMedium)
                                Text("Total Hours by Pioneers: ${uiState.totalHoursByPioneers}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    val toEdit = pendingEdit
    if (toEdit != null) {
        EditReportDialog(
            row = toEdit,
            onDismiss = { pendingEdit = null },
            onSave = { bibleStudies, hours, participated ->
                viewModel.updateReport(toEdit.report, bibleStudies, hours, participated, currentPersonId)
            },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Permanently Delete Report?") },
            text = { Text("This will permanently delete ${toDelete.person.fullName}'s report for this period. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.permanentlyDelete(toDelete.report, currentPersonId); pendingDelete = null }) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

/** "PUBLISHER MINISTRY REPORT FOR THE MONTH OF AUGUST 2026" when the range is
 * exactly one calendar month (the spec's own example, and this screen's
 * "This Month" default); a plain date-range heading otherwise. */
private fun reportTitleFor(startMillis: Long, endMillis: Long, option: QuickDateRange): String {
    if (option == QuickDateRange.THIS_MONTH) {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return "PUBLISHER MINISTRY REPORT FOR THE MONTH OF ${monthFormat.format(startMillis).uppercase()}"
    }
    val start = Calendar.getInstance().apply { timeInMillis = startMillis }
    val end = Calendar.getInstance().apply { timeInMillis = endMillis }
    val isWholeCalendarMonth = start.get(Calendar.DAY_OF_MONTH) == 1 &&
        end.get(Calendar.MONTH) == start.get(Calendar.MONTH) &&
        end.get(Calendar.YEAR) == start.get(Calendar.YEAR) &&
        end.get(Calendar.DAY_OF_MONTH) == end.getActualMaximum(Calendar.DAY_OF_MONTH)
    if (isWholeCalendarMonth) {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return "PUBLISHER MINISTRY REPORT FOR THE MONTH OF ${monthFormat.format(startMillis).uppercase()}"
    }
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return "PUBLISHER MINISTRY REPORT (${dateFormat.format(startMillis)} - ${dateFormat.format(endMillis)})"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublisherPickerDropdown(publishers: List<Person>, selectedId: String?, onSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = publishers.firstOrNull { it.id == selectedId }?.fullName ?: "All"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Publisher") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelected(null); expanded = false })
            publishers.forEach { person ->
                DropdownMenuItem(text = { Text(person.fullName) }, onClick = { onSelected(person.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CongregationPickerDropdown(congregations: List<Congregation>, selectedId: String?, onSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: "All"
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
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelected(null); expanded = false })
            congregations.forEach { congregation ->
                DropdownMenuItem(text = { Text(congregation.name) }, onClick = { onSelected(congregation.id); expanded = false })
            }
        }
    }
}

/** "Edit directly the report if there are changes" — Bible Study/Hours/
 * Participated only; doesn't touch [MonthlyReport.status]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditReportDialog(
    row: PublisherReportRow,
    onDismiss: () -> Unit,
    onSave: (bibleStudiesCount: Int, hoursRendered: Double?, participatedInPreaching: Boolean?) -> Unit,
) {
    var bibleStudies by remember { mutableStateOf(row.report.bibleStudiesCount.toString()) }
    var hours by remember { mutableStateOf((row.report.hoursRendered ?: 0.0).toString()) }
    var participated by remember { mutableStateOf(row.report.participatedInPreaching ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${row.person.fullName}'s Report") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = bibleStudies,
                    onValueChange = { bibleStudies = it.filter { c -> c.isDigit() } },
                    label = { Text("Number of Bible Studies Conducted") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (row.isPioneer) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Hours Rendered") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Participated in preaching this month?")
                        Switch(checked = participated, onCheckedChange = { participated = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        bibleStudies.toIntOrNull() ?: 0,
                        if (row.isPioneer) hours.toDoubleOrNull() ?: 0.0 else null,
                        if (row.isPioneer) null else participated,
                    )
                    onDismiss()
                },
            ) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
