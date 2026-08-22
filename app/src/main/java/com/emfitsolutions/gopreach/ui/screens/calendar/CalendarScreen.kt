package com.emfitsolutions.gopreach.ui.screens.calendar

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spec §6.2 — Calendar. A single chronological list rather than a month grid
 * for this first pass — a dedicated month/week grid widget is a natural visual
 * upgrade later, but every view/add/edit/delete rule from the spec's role table
 * (via [CalendarScope]) is already enforced here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    currentPersonId: String,
    scope: CalendarScope,
    onBack: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val eventsFlow = remember(scope) { viewModel.eventsFor(scope, currentPersonId) }
    val events by eventsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val isPublisherScope = scope is CalendarScope.Publisher

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = if (isPublisherScope) "New Note" else "New Event")
            }
        },
    ) { padding ->
        if (events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing on the calendar yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    val editable = viewModel.canEdit(scope, event, currentPersonId)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(event.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${dateFormat.format(Date(event.startTime))} – ${dateFormat.format(Date(event.endTime))}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (event.kind == ScheduleKind.PERSONAL_NOTE) {
                                    Text("Personal note", style = MaterialTheme.typography.labelSmall)
                                }
                                if (event.description != null) {
                                    Text(event.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (editable) {
                                IconButton(onClick = { pendingDelete = event }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateEventDialog(
            currentPersonId = currentPersonId,
            scope = scope,
            onSave = { viewModel.save(it) },
            onDismiss = { showCreateDialog = false },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${toDelete.title}\"?") },
            text = { Text("This removes the calendar entry.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(toDelete.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CreateEventDialog(
    currentPersonId: String,
    scope: CalendarScope,
    onSave: (Schedule) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf<Long?>(null) }
    var endTime by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (scope is CalendarScope.Publisher) "New Personal Note" else "New Calendar Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateTimeField(label = "Start", valueMillis = startTime, onValueChange = { startTime = it })
                DateTimeField(label = "End", valueMillis = endTime, onValueChange = { endTime = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = startTime
                    val end = endTime
                    if (title.isNotBlank() && start != null && end != null) {
                        onSave(
                            when (scope) {
                                is CalendarScope.Publisher -> Schedule(
                                    kind = ScheduleKind.PERSONAL_NOTE,
                                    title = title.trim(),
                                    description = description.trim().ifBlank { null },
                                    startTime = start,
                                    endTime = end,
                                    ownerPersonId = currentPersonId,
                                    createdByPersonId = currentPersonId,
                                    createdAt = System.currentTimeMillis(),
                                )
                                is CalendarScope.AdminTrack -> Schedule(
                                    kind = ScheduleKind.CALENDAR_EVENT,
                                    title = title.trim(),
                                    description = description.trim().ifBlank { null },
                                    startTime = start,
                                    endTime = end,
                                    congregationId = scope.congregationId,
                                    groupId = if (!scope.canEditAll) scope.editableGroupId else null,
                                    createdByPersonId = currentPersonId,
                                    createdAt = System.currentTimeMillis(),
                                )
                            }
                        )
                        onDismiss()
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
