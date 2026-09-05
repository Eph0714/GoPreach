package com.emfitsolutions.gopreach.ui.screens.publisherreports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.emfitsolutions.gopreach.R
import com.emfitsolutions.gopreach.data.export.CsvExporter
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.ReportStatus
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.ui.components.DateRange
import com.emfitsolutions.gopreach.ui.components.DateRangeFilterBar
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.QuickDateRange
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Manage Publisher Report" module. [fixedCongregationId] is the security
 * boundary, resolved by the caller from the enrolling session's own role
 * (null means Super-Admin — every congregation); [canPermanentlyDelete] is
 * Super-Admin-only, same convention as every other Manage screen.
 * [readOnly] hides Edit/Unlock/Mark Posted (and Delete, on top of
 * [canPermanentlyDelete]) — a grant-based Circuit Overseer with a report-view
 * permission reaches this screen but can never edit through it, since
 * firestore.rules blocks every restricted user's `monthlyReports` write
 * regardless of permission (see AdminHomeScreen.canManagePublisherReports's
 * doc comment).
 *
 * [canMarkPosted] — "the service overseer will click the POST button... the
 * super admin, admin, and coordinator elder, regular elder can do so [too],
 * however the admin, coordinator elder, regular elder can only do it under
 * their congregation" — same set as the general edit right ([readOnly]):
 * Super-Admin (every congregation), Admin/Coordinator Elder/Regular Elder
 * (own congregation only, via [fixedCongregationId]), and Service Overseer.
 * Actual enforcement of "once Posted, the Publisher can no longer edit it"
 * lives in firestore.rules' `monthlyReports` write rule, not this flag —
 * this only governs who in the admin track even sees the "Mark as Posted"/
 * "POST" actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePublisherReportsScreen(
    currentPersonId: String,
    fixedCongregationId: String?,
    canPermanentlyDelete: Boolean,
    canMarkPosted: Boolean,
    readOnly: Boolean = false,
    /** "If [a] report from [a] Publisher will be open[ed] [from the
     * notification balloon], open the exact month, not the default month of
     * the module" — non-null only when arriving from the notification
     * balloon's Monthly Report item (see Destinations.manageReportsForMonth);
     * every other entry point (side panel, dashboard tile) passes null and
     * keeps the screen's own default "This Month" filter. */
    initialPeriodMonth: Long? = null,
    onBack: () -> Unit,
    viewModel: ManagePublisherReportsViewModel = hiltViewModel(),
) {
    LaunchedEffect(fixedCongregationId) { viewModel.restrictTo(fixedCongregationId) }
    LaunchedEffect(initialPeriodMonth) {
        if (initialPeriodMonth != null) viewModel.setDateRange(DateRange.forMonth(initialPeriodMonth))
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingEdit by remember { mutableStateOf<PublisherReportRow?>(null) }
    var pendingDelete by remember { mutableStateOf<PublisherReportRow?>(null) }
    var showPostAllConfirm by remember { mutableStateOf(false) }
    val showToast = rememberActionToast()

    val reportTitle = remember(uiState.dateRange) { reportTitleFor(uiState.dateRange.startMillis, uiState.dateRange.endMillis, uiState.dateRange.option) }
    val reportTable = remember(uiState.rows, reportTitle) { publisherReportTable(reportTitle, uiState) }

    // Bug fix ("I cannot see any PDF or Excel"): see ReportsScreen's matching
    // fix — the Storage Access Framework picker just saves and closes with
    // no feedback of its own; now it confirms and opens the file immediately.
    val exportCsvSuccess = stringResource(R.string.reports_export_csv_success)
    val exportFailedWrite = stringResource(R.string.reports_export_failed_write)
    val exportFailedUnknown = stringResource(R.string.reports_export_failed_unknown)
    val exportFailedGenericTemplate = stringResource(R.string.reports_export_failed_generic)
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                val wrote = CsvExporter.write(context, uri, reportTable.title, subtitle = null, columns = reportTable.columns, rows = reportTable.rows, totals = reportTable.totals)
                if (wrote) {
                    showToast(exportCsvSuccess)
                    CsvExporter.openWithChooser(context, uri, "text/csv")
                } else {
                    showToast(exportFailedWrite)
                }
            } catch (e: Exception) {
                showToast(exportFailedGenericTemplate.format(e.localizedMessage ?: exportFailedUnknown))
            }
        }
    }
    val csvExportFileName = "gopreach-publisher-reports-${SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())}.csv"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_reports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.dashboard_back_cd))
                    }
                },
                actions = {
                    // "Print preview" — Android's own print dialog always
                    // shows a preview before anything prints, and offers
                    // "Save as PDF" out of the box. Bug fix ("I cannot see
                    // export to excel or pdf"): these were disabled when
                    // there were no rows — a disabled IconButton's icon
                    // renders at reduced alpha, which on this TopAppBar read
                    // as "not there at all." Always enabled now.
                    IconButton(onClick = { ReportPrinter.print(context, reportTable) }) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = stringResource(R.string.reports_export_pdf_cd))
                    }
                    // "Export as ... excel" — CSV, opens directly in any
                    // spreadsheet app.
                    IconButton(onClick = { csvExportLauncher.launch(csvExportFileName) }) {
                        Icon(Icons.Rounded.TableChart, contentDescription = stringResource(R.string.reports_export_excel_cd))
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

                Text(stringResource(R.string.manage_reports_show_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.showMode == ReportShowMode.ALL,
                        onClick = { viewModel.setShowMode(ReportShowMode.ALL) },
                        label = { Text(stringResource(R.string.manage_reports_all)) },
                    )
                    FilterChip(
                        selected = uiState.showMode == ReportShowMode.BY_PUBLISHER,
                        onClick = { viewModel.setShowMode(ReportShowMode.BY_PUBLISHER) },
                        label = { Text(stringResource(R.string.manage_reports_by_publisher)) },
                    )
                    // Super-Admin only — an Admin/Coordinator Elder/Service
                    // Overseer is already scoped to exactly one congregation
                    // ([fixedCongregationId] non-null), so this option would
                    // be a no-op for them.
                    if (fixedCongregationId == null) {
                        FilterChip(
                            selected = uiState.showMode == ReportShowMode.BY_CONGREGATION,
                            onClick = { viewModel.setShowMode(ReportShowMode.BY_CONGREGATION) },
                            label = { Text(stringResource(R.string.manage_reports_by_congregation)) },
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

                val searchLabel = if (fixedCongregationId == null) {
                    stringResource(R.string.manage_reports_search_label_with_congregation)
                } else {
                    stringResource(R.string.manage_reports_search_label)
                }
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    label = { Text(searchLabel) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )

                // "Select a month, then see all publishers that submitted
                // their record within that month, then click the POST
                // button — all the record will now be locked" — one tap
                // posts every SUBMITTED report currently shown for whichever
                // month/filters are selected above, instead of the per-row
                // lock icon one publisher at a time.
                val submittedCount = uiState.rows.count { it.report.status == ReportStatus.SUBMITTED }
                if (canMarkPosted && submittedCount > 0) {
                    Button(onClick = { showPostAllConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.manage_reports_post_button, submittedCount, if (submittedCount == 1) "" else "s"))
                    }
                }
            }

            if (uiState.rows.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.manage_reports_no_reports), style = MaterialTheme.typography.bodyMedium)
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
                                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.manage_reports_edit_cd))
                                            }
                                            if (row.isLocked) {
                                                IconButton(onClick = { viewModel.unlock(row.report, currentPersonId) }) {
                                                    Icon(Icons.Rounded.LockOpen, contentDescription = stringResource(R.string.manage_reports_unlock_cd))
                                                }
                                            } else if (canMarkPosted) {
                                                // "The service overseer will mark it as 'Posted', that's
                                                // the time the publisher can no longer edit the record."
                                                IconButton(onClick = { viewModel.markPosted(row.report, currentPersonId) }) {
                                                    Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.manage_reports_mark_posted_cd))
                                                }
                                            }
                                        }
                                        if (canPermanentlyDelete && !readOnly) {
                                            IconButton(onClick = { pendingDelete = row }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.manage_reports_delete_cd))
                                            }
                                        }
                                    }
                                }
                                Text(stringResource(R.string.manage_reports_status_prefix, row.category.name.replace('_', ' ')), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (row.isPioneer) {
                                        stringResource(R.string.manage_reports_bible_study_hours, row.report.bibleStudiesCount, (row.report.hoursRendered ?: 0.0).toString())
                                    } else {
                                        val yesNo = if (row.report.participatedInPreaching == true) stringResource(R.string.consolidated_yes) else stringResource(R.string.consolidated_no)
                                        stringResource(R.string.manage_reports_bible_study_participate, row.report.bibleStudiesCount, yesNo)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(stringResource(R.string.manage_reports_congregation_prefix, row.congregationName), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    when {
                                        row.isPosted -> stringResource(R.string.manage_reports_posted_locked)
                                        row.report.status == ReportStatus.SUBMITTED -> stringResource(R.string.manage_reports_submitted_editable)
                                        else -> stringResource(R.string.manage_reports_draft_editable)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (row.isPosted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.manage_reports_total_bible_study, uiState.totalBibleStudies), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.manage_reports_total_hours_pioneers, uiState.totalHoursByPioneers.toString()), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    val reportSavedToast = stringResource(R.string.manage_reports_report_saved)
    val reportDeletedToast = stringResource(R.string.manage_reports_report_deleted)
    val toEdit = pendingEdit
    if (toEdit != null) {
        EditReportDialog(
            row = toEdit,
            onDismiss = { pendingEdit = null },
            onSave = { bibleStudies, hours, participated ->
                viewModel.updateReport(toEdit.report, bibleStudies, hours, participated, currentPersonId)
                showToast(reportSavedToast)
            },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.manage_reports_delete_title)) },
            text = { Text(stringResource(R.string.manage_reports_delete_message, toDelete.person.fullName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.permanentlyDelete(toDelete.report, currentPersonId); showToast(reportDeletedToast); pendingDelete = null }) {
                    Text(stringResource(R.string.manage_reports_delete_permanently))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showPostAllConfirm) {
        val submittedCount = uiState.rows.count { it.report.status == ReportStatus.SUBMITTED }
        AlertDialog(
            onDismissRequest = { showPostAllConfirm = false },
            title = { Text(stringResource(R.string.manage_reports_post_confirm_title, submittedCount, if (submittedCount == 1) "" else "s")) },
            text = {
                Text(stringResource(R.string.manage_reports_post_confirm_message, reportTitle))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markPostedAll(uiState.rows, currentPersonId)
                    showToast(context.getString(R.string.manage_reports_posted_toast, submittedCount, if (submittedCount == 1) "" else "s"))
                    showPostAllConfirm = false
                }) { Text(stringResource(R.string.manage_reports_post_action)) }
            },
            dismissButton = { TextButton(onClick = { showPostAllConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Shared shape for both Print and CSV export — one place that decides what
 * a "Publisher Report" table actually contains, so the two outputs never
 * drift apart. */
private fun publisherReportTable(title: String, uiState: ManagePublisherReportsUiState): ReportTable {
    val rows = uiState.rows.mapIndexed { index, row ->
        listOf(
            (index + 1).toString(),
            row.person.fullName,
            row.category.name.replace('_', ' '),
            row.report.bibleStudiesCount.toString(),
            if (row.isPioneer) formatHoursForExport(row.report.hoursRendered ?: 0.0) else "N/A",
            if (row.isPioneer) "N/A" else if (row.report.participatedInPreaching == true) "YES" else "NO",
            row.congregationName,
        )
    }
    return ReportTable(
        title = title,
        columns = listOf("#", "Publisher", "Status", "Bible Study", "Hours", "Participate in Preaching", "Congregation/Group"),
        rows = rows,
        totals = listOf(
            "Total Bible Study" to uiState.totalBibleStudies.toString(),
            "Total Hours by Pioneers" to formatHoursForExport(uiState.totalHoursByPioneers),
        ),
    )
}

private fun formatHoursForExport(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(Locale.US, value)

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
    val allLabel = stringResource(R.string.manage_reports_all)
    val selectedName = publishers.firstOrNull { it.id == selectedId }?.fullName ?: allLabel
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.manage_reports_publisher_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelected(null); expanded = false })
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
    val allLabel = stringResource(R.string.manage_reports_all)
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: allLabel
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.reports_congregation_group_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelected(null); expanded = false })
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

    fun submit() {
        onSave(
            bibleStudies.toIntOrNull() ?: 0,
            if (row.isPioneer) hours.toDoubleOrNull() ?: 0.0 else null,
            if (row.isPioneer) null else participated,
        )
        onDismiss()
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.manage_reports_edit_title, row.person.fullName),
        onConfirm = ::submit,
        confirmLabel = stringResource(R.string.manage_reports_save_changes),
        maxContentHeight = 400.dp,
    ) {
                OutlinedTextField(
                    value = bibleStudies,
                    onValueChange = { bibleStudies = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.manage_reports_bible_studies_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (row.isPioneer) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.manage_reports_hours_field)) },
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
                        Text(stringResource(R.string.manage_reports_participated_question))
                        Switch(checked = participated, onCheckedChange = { participated = it })
                    }
                }
    }
}
