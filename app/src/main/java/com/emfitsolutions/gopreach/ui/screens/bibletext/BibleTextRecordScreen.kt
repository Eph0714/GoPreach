package com.emfitsolutions.gopreach.ui.screens.bibletext

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.export.BibleTextExporter
import com.emfitsolutions.gopreach.data.model.BibleTextCategory
import com.emfitsolutions.gopreach.data.model.BibleTextRecord
import com.emfitsolutions.gopreach.data.model.LEGACY_EVENT_PLACEHOLDER
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.domain.NwtBibleReferenceData
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import kotlinx.coroutines.launch

/** Spec §11 — the Publisher may either search across every field at once, or
 * pin the search to one specific one. */
private enum class BibleSearchBy(val label: String) {
    ALL("All"), EVENT("Event"), THEME_TOPIC("Theme/Topic"), SPEAKER("Speaker"), REMARKS("Remarks")
}

/** One Event plus its own Bible Text children, resolved once per composition
 * — every list/search/print/export below reads off this instead of each
 * re-joining [BibleTextRecord.categoryId] against the Event list itself. */
private data class EventWithTexts(
    val event: BibleTextCategory,
    val texts: List<BibleTextRecord>,
)

/** "Apocalipsis 21:3-4" (spec's own worked example) — falls back to the raw
 * book id if the book somehow isn't in the reference data (a language/book
 * pairing removed after the record was saved). */
private fun BibleTextRecord.referenceLabel(): String {
    val book = NwtBibleReferenceData.book(bibleVersionId, languageId, bibleBookId)
    return "${book?.name ?: bibleBookId} $chapter:$verses"
}

private val BibleTextCategory.eventLabel: String get() = event.ifBlank { LEGACY_EVENT_PLACEHOLDER }

/**
 * "My Bible Text Record: Event/Topic Record Structure Upgrade" — a
 * Publisher's personal Event → Bible Text organizer. Add/Edit/Delete an
 * Event (Event/Theme-Topic/Speaker), then add one or more Bible Texts under
 * it (Bible Book/Chapter selected from visual boxes, never a dropdown —
 * spec §3/§4/§22) — search across Event/Theme/Speaker/Remarks, plus an
 * optional Bible Book/Chapter reference filter (spec §8-§12). Every read/
 * write here is scoped to the Publisher's own records only (see
 * [BibleTextRecordViewModel]'s doc comment for the ownership model).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleTextRecordScreen(
    publisherPersonId: String,
    currentPerson: Person?,
    onBack: () -> Unit,
    viewModel: BibleTextRecordViewModel = hiltViewModel(),
) {
    val recordsFlow = remember(publisherPersonId) { viewModel.recordsFor(publisherPersonId) }
    val records by recordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val eventsFlow = remember(publisherPersonId) { viewModel.eventsFor(publisherPersonId) }
    val events by eventsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val eventsWithTexts = remember(events, records) {
        events.map { event -> EventWithTexts(event, records.filter { it.categoryId == event.id }) }
    }

    var selectedEventId by remember { mutableStateOf<String?>(null) }
    val selected = eventsWithTexts.firstOrNull { it.event.id == selectedEventId }

    if (selected == null) {
        EventListScreen(
            publisherPersonId = publisherPersonId,
            currentPerson = currentPerson,
            eventsWithTexts = eventsWithTexts,
            onOpenEvent = { selectedEventId = it },
            onBack = onBack,
            viewModel = viewModel,
        )
    } else {
        EventDetailScreen(
            publisherPersonId = publisherPersonId,
            currentPerson = currentPerson,
            eventWithTexts = selected,
            onBack = { selectedEventId = null },
            onDeleted = { selectedEventId = null },
            viewModel = viewModel,
        )
    }
}

// ---------------------------------------------------------------------------
// Event list — search, filter, and the "+Add Event" entry point.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EventListScreen(
    publisherPersonId: String,
    currentPerson: Person?,
    eventsWithTexts: List<EventWithTexts>,
    onOpenEvent: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BibleTextRecordViewModel,
) {
    val showToast = rememberActionToast()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchText by remember { mutableStateOf("") }
    var searchBy by remember { mutableStateOf(BibleSearchBy.ALL) }
    var showFilters by remember { mutableStateOf(false) }
    var referenceBookId by remember { mutableStateOf<String?>(null) }
    var referenceChapter by remember { mutableStateOf<Int?>(null) }

    var showAddEvent by remember { mutableStateOf(false) }
    var pendingDeleteEvent by remember { mutableStateOf<EventWithTexts?>(null) }
    var showPreferredLanguage by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val referenceBooks = remember { NwtBibleReferenceData.booksFor(NwtBibleReferenceData.defaultVersion.id, "en") }
    val referenceBookChapterCount = referenceBooks.firstOrNull { it.id == referenceBookId }?.chapterCount ?: 0

    // "The receiving Publisher can import the data" — a plain file picker,
    // same mechanism as before, updated for the Event-shaped export file.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val file = json?.let { BibleTextExporter.parseExportJson(it) }
        if (file == null) {
            showToast("That file isn't a valid Bible Text Record export.")
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val result = viewModel.importRecords(publisherPersonId, file, eventsWithTexts.map { it.event })
            val eventNote = if (result.newEvents > 0) " and ${result.newEvents} new event${if (result.newEvents == 1) "" else "s"}" else ""
            showToast("Imported ${result.newRecords} record${if (result.newRecords == 1) "" else "s"}$eventNote.")
        }
    }

    val filtered = remember(eventsWithTexts, searchText, searchBy, referenceBookId, referenceChapter) {
        eventsWithTexts.filter { item ->
            val referenceMatches = referenceBookId == null || item.texts.any { text ->
                text.bibleBookId == referenceBookId && (referenceChapter == null || text.chapter == referenceChapter)
            }
            if (!referenceMatches) return@filter false

            val query = searchText.trim()
            if (query.isBlank()) return@filter true
            when (searchBy) {
                BibleSearchBy.EVENT -> item.event.eventLabel.contains(query, ignoreCase = true)
                BibleSearchBy.THEME_TOPIC -> item.event.name.contains(query, ignoreCase = true)
                BibleSearchBy.SPEAKER -> item.event.speaker?.contains(query, ignoreCase = true) == true
                BibleSearchBy.REMARKS -> item.texts.any { it.remarks.contains(query, ignoreCase = true) }
                BibleSearchBy.ALL ->
                    item.event.eventLabel.contains(query, ignoreCase = true) ||
                        item.event.name.contains(query, ignoreCase = true) ||
                        item.event.speaker?.contains(query, ignoreCase = true) == true ||
                        item.texts.any { it.remarks.contains(query, ignoreCase = true) || it.referenceLabel().contains(query, ignoreCase = true) }
            }
        }.sortedByDescending { it.event.updatedAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bible Text Record") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showPreferredLanguage = true }) {
                        Icon(Icons.Rounded.Language, contentDescription = "Preferred Bible Language")
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Filters")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Print, Share, or Import")
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Print") },
                                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                                onClick = { showMoreMenu = false; ReportPrinter.print(context, bibleTextReportTable(filtered)) },
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    val allRecords = eventsWithTexts.flatMap { it.texts }
                                    val eventsById = eventsWithTexts.associate { it.event.id to it.event }
                                    BibleTextExporter.share(context, BibleTextExporter.buildExportJson(allRecords, eventsById))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import") },
                                leadingIcon = { Icon(Icons.Rounded.Upload, contentDescription = null) },
                                onClick = { showMoreMenu = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddEvent = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Event")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search Event, Theme, Speaker, Remarks...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.weight(1f),
                )
            }

            if (showFilters) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledDropdown(
                        label = "Search By",
                        selectedLabel = searchBy.label,
                        options = BibleSearchBy.entries.map { it.name to it.label },
                        onSelected = { value -> searchBy = BibleSearchBy.entries.first { it.name == value } },
                    )
                    Text("Search by Bible Reference (optional)", style = MaterialTheme.typography.labelMedium)
                    BibleBookGrid(
                        books = referenceBooks,
                        selectedBookId = referenceBookId,
                        onSelect = { id -> referenceBookId = if (id == referenceBookId) null else id; referenceChapter = null },
                    )
                    if (referenceBookId != null) {
                        Text("Select Chapter", style = MaterialTheme.typography.labelMedium)
                        ChapterGrid(
                            chapterCount = referenceBookChapterCount,
                            selectedChapter = referenceChapter,
                            onSelect = { chapter -> referenceChapter = if (chapter == referenceChapter) null else chapter },
                        )
                    }
                    Row {
                        TextButton(onClick = { searchText = ""; searchBy = BibleSearchBy.ALL; referenceBookId = null; referenceChapter = null }) {
                            Text("Clear")
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (filtered.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (eventsWithTexts.isEmpty()) "No Events saved yet. Tap + to add one." else "No Events found for the selected search/filter.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.event.id }) { item ->
                        EventCard(item = item, onClick = { onOpenEvent(item.event.id) }, onDelete = { pendingDeleteEvent = item })
                    }
                }
            }
        }
    }

    if (showAddEvent) {
        AddEditEventDialog(
            existing = null,
            publisherPersonId = publisherPersonId,
            onSave = { event ->
                showAddEvent = false
                coroutineScope.launch {
                    val saved = viewModel.saveEventAndReturn(event)
                    showToast("Event added successfully.")
                    onOpenEvent(saved.id)
                }
            },
            onDismiss = { showAddEvent = false },
        )
    }

    val toDelete = pendingDeleteEvent
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteEvent = null },
            title = { Text("Delete Event?") },
            text = {
                Text(
                    if (toDelete.texts.isEmpty()) {
                        "Are you sure you want to delete \"${toDelete.event.eventLabel}\"?"
                    } else {
                        "This Event contains ${toDelete.texts.size} Bible Text record${if (toDelete.texts.size == 1) "" else "s"}. " +
                            "Deleting the Event will also remove its associated Bible Text records. Do you want to continue?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEventCascade(publisherPersonId, toDelete.event.id)
                    showToast("Event deleted successfully.")
                    pendingDeleteEvent = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteEvent = null }) { Text("Cancel") } },
        )
    }

    if (showPreferredLanguage && currentPerson != null) {
        PreferredLanguageDialog(
            currentLanguageId = currentPerson.preferredBibleLanguageId,
            onSave = { languageId ->
                viewModel.updatePreferredLanguage(currentPerson, languageId)
                showToast("Preferred Bible language updated.")
                showPreferredLanguage = false
            },
            onDismiss = { showPreferredLanguage = false },
        )
    }
}

/** "Add a print button" — same [ReportTable]/[ReportPrinter] shape every
 * other report screen in this app already uses, one row per Bible Text
 * (spec's own record-level granularity), Event columns repeated per row. */
private fun bibleTextReportTable(items: List<EventWithTexts>): ReportTable {
    val rows = items.flatMap { item ->
        item.texts.map { text ->
            listOf(
                item.event.eventLabel,
                item.event.name,
                item.event.speaker ?: "—",
                text.referenceLabel(),
                text.remarks,
                formatRecordTimestamp(text.createdAt),
            )
        }
    }
    return ReportTable(
        title = "My Bible Text Record",
        columns = listOf("Event", "Theme/Topic", "Speaker", "Reference", "Remarks", "Date Added"),
        rows = rows,
    )
}

@Composable
private fun EventCard(item: EventWithTexts, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.event.eventLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.event.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (item.event.speaker?.isNotBlank() == true) {
                    Text("Speaker: ${item.event.speaker}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${item.texts.size} Bible Text${if (item.texts.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete Event") }
        }
    }
}

// ---------------------------------------------------------------------------
// Event details — Event info, its Bible Texts, "+Add Bible Text".

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailScreen(
    publisherPersonId: String,
    currentPerson: Person?,
    eventWithTexts: EventWithTexts,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: BibleTextRecordViewModel,
) {
    val showToast = rememberActionToast()
    val event = eventWithTexts.event
    var showEditEvent by remember { mutableStateOf(false) }
    var showAddText by remember { mutableStateOf(false) }
    var pendingEditText by remember { mutableStateOf<BibleTextRecord?>(null) }
    var pendingDeleteText by remember { mutableStateOf<BibleTextRecord?>(null) }
    var pendingDeleteEvent by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event.eventLabel) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showEditEvent = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit Event") }
                    IconButton(onClick = { pendingDeleteEvent = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete Event") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddText = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Bible Text")
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Event Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Event: ${event.eventLabel}", style = MaterialTheme.typography.bodyMedium)
                Text("Theme/Topic: ${event.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Speaker: ${event.speaker?.ifBlank { null } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("Date Created: ${formatRecordTimestamp(event.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Last Updated: ${formatRecordTimestamp(event.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { Text("Bible Texts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            if (eventWithTexts.texts.isEmpty()) {
                item { Text("No Bible texts yet. Tap + to add one.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(eventWithTexts.texts.sortedByDescending { it.createdAt }, key = { it.id }) { text ->
                    BibleTextCard(text = text, onEdit = { pendingEditText = text }, onDelete = { pendingDeleteText = text })
                }
            }
        }
    }

    if (showEditEvent) {
        AddEditEventDialog(
            existing = event,
            publisherPersonId = publisherPersonId,
            onSave = { updated -> viewModel.saveEvent(updated); showToast("Event updated successfully."); showEditEvent = false },
            onDismiss = { showEditEvent = false },
        )
    }

    if (showAddText) {
        BibleTextRecordDialog(
            existing = null,
            eventId = event.id,
            publisherPersonId = publisherPersonId,
            preferredLanguageId = currentPerson?.preferredBibleLanguageId,
            onSave = { viewModel.saveRecord(it); showToast("Bible text added successfully.") },
            onDismiss = { showAddText = false },
        )
    }
    val toEditText = pendingEditText
    if (toEditText != null) {
        BibleTextRecordDialog(
            existing = toEditText,
            eventId = event.id,
            publisherPersonId = publisherPersonId,
            preferredLanguageId = currentPerson?.preferredBibleLanguageId,
            onSave = { viewModel.saveRecord(it); showToast("Bible text updated successfully."); pendingEditText = null },
            onDismiss = { pendingEditText = null },
        )
    }
    val toDeleteText = pendingDeleteText
    if (toDeleteText != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteText = null },
            title = { Text("Delete Bible Text?") },
            text = { Text("Are you sure you want to delete this Bible text (${toDeleteText.referenceLabel()})?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(toDeleteText.id)
                    showToast("Bible text deleted successfully.")
                    pendingDeleteText = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteText = null }) { Text("Cancel") } },
        )
    }
    if (pendingDeleteEvent) {
        AlertDialog(
            onDismissRequest = { pendingDeleteEvent = false },
            title = { Text("Delete Event?") },
            text = {
                Text(
                    if (eventWithTexts.texts.isEmpty()) {
                        "Are you sure you want to delete \"${event.eventLabel}\"?"
                    } else {
                        "This Event contains ${eventWithTexts.texts.size} Bible Text record${if (eventWithTexts.texts.size == 1) "" else "s"}. " +
                            "Deleting the Event will also remove its associated Bible Text records. Do you want to continue?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEventCascade(publisherPersonId, event.id)
                    showToast("Event deleted successfully.")
                    pendingDeleteEvent = false
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteEvent = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BibleTextCard(text: BibleTextRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text.referenceLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (text.remarks.isNotBlank()) {
                    Text(text.remarks, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Added: ${formatRecordTimestamp(text.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add/Edit Event

@Composable
private fun AddEditEventDialog(
    existing: BibleTextCategory?,
    publisherPersonId: String,
    onSave: (BibleTextCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    var eventText by remember { mutableStateOf(existing?.event.orEmpty()) }
    var themeTopic by remember { mutableStateOf(existing?.name.orEmpty()) }
    var speaker by remember { mutableStateOf(existing?.speaker.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val message = requiredFieldsMessage(
            "Event" to eventText.isNotBlank(),
            "Theme/Topic" to themeTopic.isNotBlank(),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        val now = System.currentTimeMillis()
        val base = existing ?: BibleTextCategory(publisherPersonId = publisherPersonId, createdAt = now)
        onSave(base.copy(event = eventText.trim(), name = themeTopic.trim(), speaker = speaker.trim().ifBlank { null }, updatedAt = now))
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "Add Event" else "Edit Event",
        onConfirm = ::submit,
        confirmLabel = if (existing == null) "Save Event" else "Save",
        errorMessage = errorMessage,
    ) {
        OutlinedTextField(
            value = eventText,
            onValueChange = { eventText = it; errorMessage = null },
            label = { Text("Event *") },
            placeholder = { Text("e.g. Public Talk") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        SuggestionChips(suggestions = SUGGESTED_EVENTS, onPick = { eventText = it })
        OutlinedTextField(
            value = themeTopic,
            onValueChange = { themeTopic = it; errorMessage = null },
            label = { Text("Theme/Topic *") },
            placeholder = { Text("e.g. Strengthening Our Faith") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        SuggestionChips(suggestions = SUGGESTED_THEME_TOPICS.take(8), onPick = { themeTopic = it })
        OutlinedTextField(
            value = speaker,
            onValueChange = { speaker = it },
            label = { Text("Speaker (optional)") },
            placeholder = { Text("e.g. Brother Juan Dela Cruz") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionChips(suggestions: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        suggestions.forEach { suggestion ->
            AssistChip(onClick = { onPick(suggestion) }, label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

// ---------------------------------------------------------------------------
// Add/Edit Bible Text — visual Book/Chapter box selection (spec §3/§4).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BibleTextRecordDialog(
    existing: BibleTextRecord?,
    eventId: String,
    publisherPersonId: String,
    preferredLanguageId: String?,
    onSave: (BibleTextRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val version = NwtBibleReferenceData.defaultVersion
    var languageId by remember {
        mutableStateOf(existing?.languageId ?: preferredLanguageId ?: NwtBibleReferenceData.languages.first().id)
    }
    val books = remember(languageId) { NwtBibleReferenceData.booksFor(version.id, languageId) }
    var bookId by remember { mutableStateOf(existing?.bibleBookId?.ifBlank { null }) }
    val selectedBook = books.firstOrNull { it.id == bookId }
    // Spec test 6 — changing the Bible Book always resets the selected
    // Chapter; an already-valid chapter for the *previous* book is never
    // carried over to a book it might not even have that many chapters in.
    var chapter by remember { mutableStateOf(existing?.chapter?.takeIf { it > 0 }) }
    var versesText by remember { mutableStateOf(existing?.verses.orEmpty()) }
    var remarks by remember { mutableStateOf(existing?.remarks.orEmpty()) }

    // Spec §9 — a single verse ("3") or a range ("3-4", "10-12"); rejects
    // "abc", "3--4", "-4", "hello", etc. Chapter/verse *existence* is already
    // enforced structurally by the Chapter grid (only ever offers
    // 1..chapterCount) rather than needing a separate existence check here.
    val versesValid = remember(versesText) { Regex("""^\d+(-\d+)?$""").matches(versesText.trim()) }
    val verseRangeValid = remember(versesText) {
        if (!versesValid) return@remember false
        val parts = versesText.trim().split("-")
        parts.size == 1 || (parts[0].toInt() <= parts[1].toInt())
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val message = requiredFieldsMessage(
            "Bible Book" to (bookId != null),
            "Chapter" to (chapter != null && chapter!! > 0),
            "Verses" to (versesText.isNotBlank() && verseRangeValid),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        val now = System.currentTimeMillis()
        val base = existing ?: BibleTextRecord(publisherPersonId = publisherPersonId, categoryId = eventId, createdAt = now)
        onSave(
            base.copy(
                bibleVersionId = version.id,
                languageId = languageId,
                bibleBookId = bookId.orEmpty(),
                chapter = chapter ?: 0,
                verses = versesText.trim(),
                categoryId = eventId,
                remarks = remarks.trim(),
                updatedAt = now,
            ),
        )
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "Add Bible Text" else "Edit Bible Text",
        onConfirm = ::submit,
        confirmLabel = if (existing == null) "Save Bible Text" else "Save",
        errorMessage = errorMessage,
        maxContentHeight = 620.dp,
    ) {
        LabeledDropdown(
            label = "Bible Language",
            selectedLabel = NwtBibleReferenceData.language(languageId)?.name ?: "Select",
            options = NwtBibleReferenceData.languages.map { it.id to it.name },
            onSelected = { newLanguageId ->
                languageId = newLanguageId ?: return@LabeledDropdown
                val newBooks = NwtBibleReferenceData.booksFor(version.id, newLanguageId)
                // Same book (by language-independent id) if it still exists
                // in the new language's list; the selected Chapter itself
                // never needs resetting here — the book (and so its chapter
                // count) hasn't actually changed, only its display language.
                bookId = newBooks.firstOrNull { it.id == bookId }?.id ?: bookId
            },
            required = true,
        )
        Text("Select Bible Book *", style = MaterialTheme.typography.labelMedium)
        BibleBookGrid(
            books = books,
            selectedBookId = bookId,
            onSelect = { newBookId ->
                if (newBookId != bookId) chapter = null
                bookId = newBookId
            },
        )
        if (selectedBook != null) {
            Text("Select Chapter *", style = MaterialTheme.typography.labelMedium)
            ChapterGrid(
                chapterCount = selectedBook.chapterCount,
                selectedChapter = chapter,
                onSelect = { chapter = it },
            )
        }
        OutlinedTextField(
            value = versesText,
            onValueChange = { versesText = it.filter { c -> c.isDigit() || c == '-' } },
            label = { Text("Verses *") },
            placeholder = { Text("e.g. 3 or 3-4") },
            singleLine = true,
            isError = versesText.isNotBlank() && !verseRangeValid,
            supportingText = {
                if (versesText.isNotBlank() && !verseRangeValid) {
                    Text("Please enter a valid verse or verse range (e.g. 3 or 3-4).")
                }
            },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = { Text("Remarks (optional)") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferredLanguageDialog(
    currentLanguageId: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var languageId by remember { mutableStateOf(currentLanguageId ?: NwtBibleReferenceData.languages.first().id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred Bible Language") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "New Bible Text Records will default to this language. You can still change it per record.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LabeledDropdown(
                    label = "Language",
                    selectedLabel = NwtBibleReferenceData.language(languageId)?.name ?: "Select",
                    options = NwtBibleReferenceData.languages.map { it.id to it.name },
                    onSelected = { languageId = it ?: languageId },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(languageId) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shared dropdown shape every non-box picker on this screen uses (Language,
 * Search By, Sort) — [options] is (value, label) so a `null` value ("All",
 * ...) reads naturally alongside real ids. Bible Book/Chapter deliberately
 * do NOT use this — see [BibleBookGrid]/[ChapterGrid] (spec §3/§4: "Do not
 * use a dropdown for selecting the Bible Book/Chapter"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<String?, String>>,
    onSelected: (String?) -> Unit,
    required: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(if (required) "$label *" else label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onSelected(value); expanded = false })
            }
        }
    }
}
