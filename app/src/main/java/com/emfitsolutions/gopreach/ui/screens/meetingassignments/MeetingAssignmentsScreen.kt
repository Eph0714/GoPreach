package com.emfitsolutions.gopreach.ui.screens.meetingassignments

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.MidweekAssignmentItem
import com.emfitsolutions.gopreach.data.model.MidweekMeetingSchedule
import com.emfitsolutions.gopreach.data.model.MidweekSection
import com.emfitsolutions.gopreach.data.model.PublicTalkScheduleRow
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class MeetingAssignmentCategory(val label: String) {
    MIDWEEK("Midweek Meeting Schedule"),
    PUBLIC_TALK("Public Talk and Watchtower Study Schedule"),
}

/**
 * "Meeting Assignments" module — Coordinator Elder/Regular Elder/Service
 * Overseer/Admin (own congregation, via [fixedCongregationId])/Super-Admin
 * (every congregation, picks one) enroll both the Midweek Meeting Schedule
 * and the Public Talk and Watchtower Study Schedule; every Publisher sees
 * their own congregation's copy, [readOnly] — no Add/Edit/Delete for them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingAssignmentsScreen(
    currentPersonId: String,
    fixedCongregationId: String?,
    readOnly: Boolean,
    onBack: () -> Unit,
    viewModel: MeetingAssignmentsViewModel = hiltViewModel(),
) {
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    var pickedCongregationId by remember(congregations) { mutableStateOf(fixedCongregationId ?: congregations.firstOrNull()?.id) }
    val congregationId = fixedCongregationId ?: pickedCongregationId
    var category by remember { mutableStateOf(MeetingAssignmentCategory.MIDWEEK) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meeting Assignments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (fixedCongregationId == null) {
                CongregationDropdown(
                    congregations = congregations,
                    selectedId = pickedCongregationId,
                    onSelected = { pickedCongregationId = it },
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MeetingAssignmentCategory.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = category == entry,
                        onClick = { category = entry },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = MeetingAssignmentCategory.entries.size),
                    ) { Text(entry.label, maxLines = 2) }
                }
            }

            if (congregationId == null) {
                Text(
                    "No congregation to show yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else when (category) {
                MeetingAssignmentCategory.MIDWEEK -> MidweekMeetingScheduleTab(
                    congregationId = congregationId,
                    readOnly = readOnly,
                    currentPersonId = currentPersonId,
                    viewModel = viewModel,
                )
                MeetingAssignmentCategory.PUBLIC_TALK -> PublicTalkScheduleTab(
                    congregationId = congregationId,
                    readOnly = readOnly,
                    currentPersonId = currentPersonId,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CongregationDropdown(congregations: List<Congregation>, selectedId: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: ""
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
            congregations.forEach { congregation ->
                DropdownMenuItem(text = { Text(congregation.name) }, onClick = { onSelected(congregation.id); expanded = false })
            }
        }
    }
}

// ---------------------------------------------------------------------
// Midweek Meeting Schedule
// ---------------------------------------------------------------------

@Composable
private fun MidweekMeetingScheduleTab(
    congregationId: String,
    readOnly: Boolean,
    currentPersonId: String,
    viewModel: MeetingAssignmentsViewModel,
) {
    var weekStart by remember { mutableStateOf(mondayOfWeek(System.currentTimeMillis())) }
    val scheduleFlow = remember(congregationId, weekStart) { viewModel.scheduleFor(congregationId, weekStart) }
    val schedule by scheduleFlow.collectAsStateWithLifecycle(initialValue = null)
    var editingItem by remember { mutableStateOf<Triple<MidweekSection, Int?, MidweekAssignmentItem?>?>(null) }
    var pendingDelete by remember { mutableStateOf<Pair<MidweekSection, Int>?>(null) }

    // "Make the week selectable, e.g. <August 31-September 6 2026>
    // <September 7 2026-September 14 2026>" — a dropdown of actual
    // Monday-Sunday weeks (spanning well before/after today), rather than a
    // raw date picker that just happened to snap to a Monday.
    WeekPickerDropdown(weekStart = weekStart, onSelected = { weekStart = it })

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MidweekSection.entries.forEach { section ->
            val items = schedule?.itemsFor(section).orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    section.displayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = section.textColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(section.backgroundColor(), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (items.isEmpty()) {
                    Text(
                        if (readOnly) "No assignments yet for this section." else "No assignments yet — tap + to add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.particular + (if (item.durationMinutes.isNotBlank()) " (${item.durationMinutes})" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "Assigned to: ${item.assignedTo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!readOnly) {
                                IconButton(onClick = { editingItem = Triple(section, index, item) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit assignment")
                                }
                                IconButton(onClick = { pendingDelete = section to index }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete assignment")
                                }
                            }
                        }
                    }
                }
                if (!readOnly) {
                    TextButton(onClick = { editingItem = Triple(section, null, null) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Add Assignment")
                    }
                }
            }
        }
    }

    val toEdit = editingItem
    if (toEdit != null) {
        val (section, index, item) = toEdit
        MidweekItemDialog(
            existing = item,
            onSave = { newItem ->
                val current = (schedule?.itemsFor(section) ?: emptyList()).toMutableList()
                if (index != null) current[index] = newItem else current.add(newItem)
                viewModel.saveSectionItems(schedule, congregationId, weekStart, section, current, currentPersonId)
                editingItem = null
            },
            onDismiss = { editingItem = null },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        val (section, index) = toDelete
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Assignment?") },
            text = { Text("This removes it from the schedule for this week.") },
            confirmButton = {
                TextButton(onClick = {
                    val current = (schedule?.itemsFor(section) ?: emptyList()).toMutableList()
                    if (index in current.indices) current.removeAt(index)
                    viewModel.saveSectionItems(schedule, congregationId, weekStart, section, current, currentPersonId)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MidweekItemDialog(
    existing: MidweekAssignmentItem?,
    onSave: (MidweekAssignmentItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var particular by remember { mutableStateOf(existing?.particular ?: "") }
    var duration by remember { mutableStateOf(existing?.durationMinutes ?: "") }
    var assignedTo by remember { mutableStateOf(existing?.assignedTo ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val message = requiredFieldsMessage(
            "Particular" to particular.isNotBlank(),
            "Assigned To" to assignedTo.isNotBlank(),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        onSave(MidweekAssignmentItem(particular = particular.trim(), durationMinutes = duration.trim(), assignedTo = assignedTo.trim()))
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "Add Assignment" else "Edit Assignment",
        onConfirm = ::submit,
        confirmLabel = if (existing == null) "Add" else "Save",
        errorMessage = errorMessage,
        maxContentHeight = 360.dp,
    ) {
        OutlinedTextField(
            value = particular,
            onValueChange = { particular = it },
            label = { Text("Particular") },
            placeholder = { Text("e.g. Bible Reading") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it },
            label = { Text("Duration (optional)") },
            placeholder = { Text("e.g. 4 min") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = assignedTo,
            onValueChange = { assignedTo = it },
            label = { Text("Assigned To") },
            placeholder = { Text("e.g. John Funtallera") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun MidweekSection.backgroundColor(): Color = when (this) {
    MidweekSection.TREASURES -> Color(0xFF616161)
    MidweekSection.FIELD_MINISTRY -> Color(0xFFFFD54F)
    MidweekSection.LIVING_AS_CHRISTIANS -> Color(0xFF8B0000)
}

private fun MidweekSection.textColor(): Color = when (this) {
    MidweekSection.TREASURES -> Color.White
    MidweekSection.FIELD_MINISTRY -> Color(0xFF3E2E00)
    MidweekSection.LIVING_AS_CHRISTIANS -> Color.White
}

private fun formatWeekRange(weekStart: Long): String {
    val startCal = Calendar.getInstance().apply { timeInMillis = weekStart }
    val endCal = Calendar.getInstance().apply { timeInMillis = weekStart; add(Calendar.DAY_OF_MONTH, 6) }
    val sameMonth = startCal.get(Calendar.MONTH) == endCal.get(Calendar.MONTH) && startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)
    return if (sameMonth) {
        "${SimpleDateFormat("MMMM d", Locale.getDefault()).format(startCal.time)} - ${SimpleDateFormat("d, yyyy", Locale.getDefault()).format(endCal.time)}"
    } else {
        "${SimpleDateFormat("MMMM d", Locale.getDefault()).format(startCal.time)} - ${SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(endCal.time)}"
    }
}

/** "Make the week selectable, e.g. <August 31-September 6 2026>
 * <September 7 2026-September 14 2026>" — every Monday-Sunday week from 8
 * weeks ago through a year ahead, each shown as its own formatted range
 * (see [formatWeekRange]) rather than a raw calendar date picker. Widest
 * reasonable window for scheduling meetings ahead of time; still a closed
 * list (like [MonthPickerField] in Monthly Report), so there's no way to
 * land on a non-Monday week start by construction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPickerDropdown(weekStart: Long, onSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val weeks = remember {
        val thisWeek = mondayOfWeek(System.currentTimeMillis())
        (-8..52).map { offset -> Calendar.getInstance().apply { timeInMillis = thisWeek; add(Calendar.WEEK_OF_YEAR, offset) }.timeInMillis }
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = formatWeekRange(weekStart),
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Week") },
            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            weeks.forEach { week ->
                DropdownMenuItem(text = { Text(formatWeekRange(week)) }, onClick = { onSelected(week); expanded = false })
            }
        }
    }
}

// ---------------------------------------------------------------------
// Public Talk and Watchtower Study Schedule
// ---------------------------------------------------------------------

@Composable
private fun PublicTalkScheduleTab(
    congregationId: String,
    readOnly: Boolean,
    currentPersonId: String,
    viewModel: MeetingAssignmentsViewModel,
) {
    val rowsFlow = remember(congregationId) { viewModel.rowsFor(congregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<PublicTalkScheduleRow?>(null) }
    var pendingDelete by remember { mutableStateOf<PublicTalkScheduleRow?>(null) }
    val showToast = rememberActionToast()
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!readOnly) {
            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Add Schedule")
            }
        }
        if (rows.isEmpty()) {
            Text(
                "No Public Talk / Watchtower Study schedule yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(dateFormat.format(Date(row.date)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (!readOnly) {
                                    Row {
                                        IconButton(onClick = { pendingEdit = row }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit schedule")
                                        }
                                        IconButton(onClick = { pendingDelete = row }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete schedule")
                                        }
                                    }
                                }
                            }
                            Text("Theme: ${row.theme}", style = MaterialTheme.typography.bodyMedium)
                            Text("Speaker: ${row.speaker}", style = MaterialTheme.typography.bodySmall)
                            Text("Chairman: ${row.chairman}", style = MaterialTheme.typography.bodySmall)
                            Text("Watchtower Conductor: ${row.watchtowerConductor}", style = MaterialTheme.typography.bodySmall)
                            Text("Watchtower Reader: ${row.watchtowerReader}", style = MaterialTheme.typography.bodySmall)
                            Text("Mic Servers: ${row.micServers}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        PublicTalkScheduleDialog(
            existing = null,
            congregationId = congregationId,
            existingRows = rows,
            currentPersonId = currentPersonId,
            viewModel = viewModel,
            onSaved = { showToast("Schedule added.") },
            onDismiss = { showAdd = false },
        )
    }
    val toEdit = pendingEdit
    if (toEdit != null) {
        PublicTalkScheduleDialog(
            existing = toEdit,
            congregationId = congregationId,
            existingRows = rows,
            currentPersonId = currentPersonId,
            viewModel = viewModel,
            onSaved = { showToast("Schedule saved.") },
            onDismiss = { pendingEdit = null },
        )
    }
    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this schedule?") },
            text = { Text("This removes the ${dateFormat.format(Date(toDelete.date))} row.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePublicTalkRow(toDelete, currentPersonId)
                    showToast("Schedule deleted.")
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PublicTalkScheduleDialog(
    existing: PublicTalkScheduleRow?,
    congregationId: String,
    existingRows: List<PublicTalkScheduleRow>,
    currentPersonId: String,
    viewModel: MeetingAssignmentsViewModel,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var date by remember { mutableStateOf(existing?.date) }
    var theme by remember { mutableStateOf(existing?.theme ?: "") }
    var speaker by remember { mutableStateOf(existing?.speaker ?: "") }
    var chairman by remember { mutableStateOf(existing?.chairman ?: "") }
    var watchtowerConductor by remember { mutableStateOf(existing?.watchtowerConductor ?: "") }
    var watchtowerReader by remember { mutableStateOf(existing?.watchtowerReader ?: "") }
    var micServers by remember { mutableStateOf(existing?.micServers ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    fun submit() {
        val message = requiredFieldsMessage(
            "Date" to (date != null),
            "Theme" to theme.isNotBlank(),
            "Speaker" to speaker.isNotBlank(),
            "Chairman" to chairman.isNotBlank(),
            "Watchtower Conductor" to watchtowerConductor.isNotBlank(),
            "Watchtower Reader" to watchtowerReader.isNotBlank(),
            "Mic Servers" to micServers.isNotBlank(),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        isSaving = true
        coroutineScope.launch {
            val row = PublicTalkScheduleRow(
                id = existing?.id ?: "",
                congregationId = congregationId,
                date = date!!,
                theme = theme.trim(),
                speaker = speaker.trim(),
                chairman = chairman.trim(),
                watchtowerConductor = watchtowerConductor.trim(),
                watchtowerReader = watchtowerReader.trim(),
                micServers = micServers.trim(),
                createdByPersonId = existing?.createdByPersonId ?: "",
                createdAt = existing?.createdAt ?: 0L,
            )
            val duplicateError = viewModel.savePublicTalkRow(existingRows, row, currentPersonId)
            isSaving = false
            if (duplicateError != null) {
                errorMessage = duplicateError
            } else {
                onSaved()
                onDismiss()
            }
        }
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "Add Public Talk / Watchtower Schedule" else "Edit Public Talk / Watchtower Schedule",
        onConfirm = ::submit,
        confirmLabel = if (isSaving) "Saving…" else if (existing == null) "Add" else "Save",
        confirmEnabled = !isSaving,
        errorMessage = errorMessage,
        maxContentHeight = 480.dp,
    ) {
        OutlinedButton(
            onClick = {
                val calendar = Calendar.getInstance().apply { date?.let { timeInMillis = it } }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        date = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        errorMessage = null
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(date?.let { dateFormat.format(Date(it)) } ?: "Set Date")
        }
        OutlinedTextField(
            value = theme,
            onValueChange = { theme = it },
            label = { Text("Theme") },
            placeholder = { Text("e.g. Who Is Jehovah?") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = speaker,
            onValueChange = { speaker = it },
            label = { Text("Speaker") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = chairman,
            onValueChange = { chairman = it },
            label = { Text("Chairman") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = watchtowerConductor,
            onValueChange = { watchtowerConductor = it },
            label = { Text("Watchtower Conductor") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = watchtowerReader,
            onValueChange = { watchtowerReader = it },
            label = { Text("Watchtower Reader") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = micServers,
            onValueChange = { micServers = it },
            label = { Text("Mic Servers") },
            placeholder = { Text("e.g. Ephraim / Josue") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
