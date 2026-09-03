package com.emfitsolutions.gopreach.ui.screens.schedules

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Schedule
import com.emfitsolutions.gopreach.data.model.ScheduleKind
import com.emfitsolutions.gopreach.ui.components.DateTimeField
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Spec §5.1/§3 — Manage Chat Schedule. [visibleGroupId] narrows further for a
 * Regular Elder (own group only); leave null for congregation-wide roles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageChatSchedulesScreen(
    currentPersonId: String,
    visibleCongregationId: String?,
    visibleGroupId: String?,
    canEdit: Boolean,
    onBack: () -> Unit,
    viewModel: ManageChatSchedulesViewModel = hiltViewModel(),
) {
    val schedulesFlow = remember(visibleCongregationId, visibleGroupId) {
        viewModel.schedulesFor(visibleCongregationId, visibleGroupId)
    }
    val schedules by schedulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<Schedule?>(null) }
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }
    val showToast = rememberActionToast()
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "New Chat Schedule")
                }
            }
        },
    ) { padding ->
        if (schedules.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No chat schedules yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(schedule.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${dateFormat.format(Date(schedule.startTime))} – ${dateFormat.format(Date(schedule.endTime))}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (schedule.description != null) {
                                    Text(schedule.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (canEdit) {
                                Row {
                                    IconButton(onClick = { pendingEdit = schedule }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { pendingDelete = schedule }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ChatScheduleDialog(
            currentPersonId = currentPersonId,
            congregationId = visibleCongregationId,
            groupId = visibleGroupId,
            existingSchedule = null,
            onSave = { viewModel.save(it); showToast("Chat schedule added.") },
            onDismiss = { showCreateDialog = false },
        )
    }

    val toEditSchedule = pendingEdit
    if (toEditSchedule != null) {
        ChatScheduleDialog(
            currentPersonId = currentPersonId,
            congregationId = visibleCongregationId,
            groupId = visibleGroupId,
            existingSchedule = toEditSchedule,
            onSave = { viewModel.save(it); showToast("Chat schedule saved.") },
            onDismiss = { pendingEdit = null },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${toDelete.title}\"?") },
            text = { Text("This removes the chat schedule entry.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(toDelete.id)
                    showToast("\"${toDelete.title}\" deleted.")
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatScheduleDialog(
    currentPersonId: String,
    congregationId: String?,
    groupId: String?,
    existingSchedule: Schedule?,
    onSave: (Schedule) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(existingSchedule?.title ?: "") }
    var description by remember { mutableStateOf(existingSchedule?.description ?: "") }
    var startTime by remember { mutableStateOf(existingSchedule?.startTime) }
    var endTime by remember { mutableStateOf(existingSchedule?.endTime) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val start = startTime
        val end = endTime
        val message = requiredFieldsMessage(
            "Event Title" to title.isNotBlank(),
            "Start" to (start != null),
            "End" to (end != null),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        onSave(
            Schedule(
                id = existingSchedule?.id ?: "",
                kind = ScheduleKind.CHAT_SCHEDULE,
                title = title.trim(),
                description = description.trim().ifBlank { null },
                startTime = start!!,
                endTime = end!!,
                congregationId = congregationId,
                groupId = groupId,
                createdByPersonId = existingSchedule?.createdByPersonId ?: currentPersonId,
                createdAt = existingSchedule?.createdAt ?: System.currentTimeMillis(),
            )
        )
        onDismiss()
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = if (existingSchedule == null) "New Chat Schedule" else "Edit Chat Schedule",
        onConfirm = ::submit,
        confirmLabel = if (existingSchedule == null) "Create" else "Save",
        errorMessage = errorMessage,
        maxContentHeight = 420.dp,
    ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.uppercase() },
                    label = { Text("Event Title") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.uppercase() },
                    label = { Text("Description (optional)") },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateTimeField(label = "Start", valueMillis = startTime, onValueChange = { startTime = it })
                DateTimeField(label = "End", valueMillis = endTime, onValueChange = { endTime = it })
    }
}
