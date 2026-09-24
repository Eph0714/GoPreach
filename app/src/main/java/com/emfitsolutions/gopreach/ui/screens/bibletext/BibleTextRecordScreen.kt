package com.emfitsolutions.gopreach.ui.screens.bibletext

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import androidx.compose.ui.window.DialogProperties

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
 * "My Bible Text Record" — a Publisher's personal Event → Bible Text
 * organizer. Add/Edit/Delete an Event (Event and Theme/Topic are searchable
 * dropdowns sourced from this module's existing sample lists, per "Redesign
 * the My Bible Text Record" §1/§2; Speaker stays free text), then add one or
 * more Bible Texts under it — Bible Book is a two-level Book list → that
 * book's Chapters navigator with a Back control (§3-§5), never both shown at
 * once; Bible Language is fixed to English and never shown in the UI (§6).
 * Search across Event/Theme/Speaker/Remarks, plus an optional Bible
 * Book/Chapter reference filter. Every read/write here is scoped to the
 * Publisher's own records only (see [BibleTextRecordViewModel]'s doc comment
 * for the ownership model).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleTextRecordScreen(
    publisherPersonId: String,
    @Suppress("UNUSED_PARAMETER") currentPerson: Person?,
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
            eventsWithTexts = eventsWithTexts,
            onOpenEvent = { selectedEventId = it },
            onBack = onBack,
            viewModel = viewModel,
        )
    } else {
        EventDetailScreen(
            publisherPersonId = publisherPersonId,
            eventWithTexts = selected,
            onBack = { selectedEventId = null },
            onDeleted = { selectedEventId = null },
            viewModel = viewModel,
        )
    }
}

// ---------------------------------------------------------------------------
// Event list — search, filter, and the "+Add Event" entry point.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventListScreen(
    publisherPersonId: String,
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            if (eventsWithTexts.isEmpty()) "No Events saved yet. Tap Add Record to add one." else "No Events found for the selected search/filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(filtered, key = { it.event.id }) { item ->
                        EventCard(item = item, onClick = { onOpenEvent(item.event.id) }, onDelete = { pendingDeleteEvent = item })
                    }
                }
                // The add action sits just below the list of events (it used to be
                // a floating "+" in the corner of the screen).
                item {
                    Button(onClick = { showAddEvent = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Add Record")
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
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
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

/**
 * The heading colours for one Event, taken from the app's own theme (so they
 * follow whichever theme colour the user picked, light or dark): each Event is
 * given a solid primary, secondary or tertiary colour and its matching "on"
 * colour for text, chosen from the Event's id so an Event keeps the same
 * colour in the list and on its own page, and no matter how the list is sorted.
 */
@Composable
private fun eventAccent(eventId: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (Math.floorMod(eventId.hashCode(), 3)) {
        0 -> scheme.primary to scheme.onPrimary
        1 -> scheme.secondary to scheme.onSecondary
        else -> scheme.tertiary to scheme.onTertiary
    }
}

/** A line of text led by a small icon — the icon says at a glance what the line
 * is (event, speaker, date, ...) without having to read the label. */
@Composable
private fun IconLine(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = Color.Unspecified,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = style, fontWeight = fontWeight, color = textColor)
    }
}

@Composable
private fun EventCard(item: EventWithTexts, onClick: () -> Unit, onDelete: () -> Unit) {
    val (headingBackground, headingText) = eventAccent(item.event.id)
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            // Theme/Topic leads, in capitals and larger (e.g. FAMILY), on a band in
            // the Event's theme colour; the arrow shows the card opens.
            Row(
                modifier = Modifier.fillMaxWidth().background(headingBackground).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.event.name.ifBlank { item.event.eventLabel }.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = headingText,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Open event", tint = headingText)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconLine(Icons.Rounded.Event, item.event.eventLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, tint = MaterialTheme.colorScheme.primary)
                    if (item.event.speaker?.isNotBlank() == true) {
                        IconLine(Icons.Rounded.Person, "Speaker: ${item.event.speaker}", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconLine(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        "${item.texts.size} Bible Text${if (item.texts.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete event", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Event details — Event info, its Bible Texts, "+Add Bible Text".

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailScreen(
    publisherPersonId: String,
    eventWithTexts: EventWithTexts,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: BibleTextRecordViewModel,
) {
    val showToast = rememberActionToast()
    // Video download results ("downloaded for offline use" / "couldn't download").
    val videoViewModel: VideoViewModel = hiltViewModel()
    LaunchedEffect(Unit) { videoViewModel.messages.collect { showToast(it) } }
    val event = eventWithTexts.event
    var showEditEvent by remember { mutableStateOf(false) }
    var showAddText by remember { mutableStateOf(false) }
    var showAddVideo by remember { mutableStateOf(false) }
    var pendingEditText by remember { mutableStateOf<BibleTextRecord?>(null) }
    var pendingDeleteText by remember { mutableStateOf<BibleTextRecord?>(null) }
    var pendingDeleteEvent by remember { mutableStateOf(false) }

    // The event's own videos, plus any attached to one of its Bible Texts before
    // events had their own gallery (each removed from wherever it is kept).
    val galleryVideos = remember(event, eventWithTexts.texts) {
        event.videos.map { video ->
            GalleryVideo(video) { viewModel.saveEvent(event.copy(videos = event.videos - video, updatedAt = System.currentTimeMillis())) }
        } + eventWithTexts.texts.flatMap { record ->
            record.videos.map { video ->
                GalleryVideo(video) { viewModel.saveRecord(record.copy(videos = record.videos - video, updatedAt = System.currentTimeMillis())) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event.name.ifBlank { event.eventLabel }.uppercase()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showEditEvent = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit event", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { pendingDeleteEvent = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete event", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                // Same theme-coloured heading band as this Event's card in the list.
                val (headingBackground, headingText) = eventAccent(event.id)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Column(modifier = Modifier.fillMaxWidth().background(headingBackground).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Event Information", style = MaterialTheme.typography.labelMedium, color = headingText)
                            Text(
                                event.name.ifBlank { event.eventLabel }.uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = headingText,
                            )
                        }
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconLine(Icons.Rounded.Event, "Event: ${event.eventLabel}", style = MaterialTheme.typography.titleMedium, tint = MaterialTheme.colorScheme.primary)
                            IconLine(Icons.Rounded.Person, "Speaker: ${event.speaker?.ifBlank { null } ?: "—"}", style = MaterialTheme.typography.titleMedium, tint = MaterialTheme.colorScheme.primary)
                            IconLine(Icons.Rounded.Schedule, "Created: ${formatRecordTimestamp(event.createdAt)}", style = MaterialTheme.typography.bodySmall)
                            IconLine(Icons.Rounded.Update, "Updated: ${formatRecordTimestamp(event.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            // Bible Texts and Videos are two separate groups, each with its own
            // Add button.
            item {
                GroupCard(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    title = "Bible Texts",
                    count = eventWithTexts.texts.size,
                    addLabel = "Add Bible Text",
                    addIcon = Icons.Rounded.Add,
                    onAdd = { showAddText = true },
                ) {
                    if (eventWithTexts.texts.isEmpty()) {
                        Text("No Bible texts yet. Tap Add Bible Text to add one.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        eventWithTexts.texts.sortedByDescending { it.createdAt }.forEach { text ->
                            BibleTextCard(text = text, onEdit = { pendingEditText = text }, onDelete = { pendingDeleteText = text })
                        }
                    }
                }
            }
            item {
                GroupCard(
                    icon = Icons.Rounded.VideoLibrary,
                    title = "Videos",
                    count = galleryVideos.size,
                    addLabel = "Add Video",
                    addIcon = Icons.Rounded.VideoLibrary,
                    onAdd = { showAddVideo = true },
                ) {
                    if (galleryVideos.isEmpty()) {
                        Text("No videos yet. Tap Add Video to add one from JW Library.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        VideoGallery(galleryVideos)
                    }
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

    if (showAddVideo) {
        AddVideoDialog(
            videos = event.videos,
            onVideosChange = { viewModel.saveEvent(event.copy(videos = it, updatedAt = System.currentTimeMillis())) },
            defaultLocale = "E",
            onDismiss = { showAddVideo = false },
        )
    }

    if (showAddText) {
        BibleTextRecordDialog(
            existing = null,
            eventId = event.id,
            publisherPersonId = publisherPersonId,
            onSave = { viewModel.saveRecord(it); showToast("Bible text added successfully."); showAddText = false },
            onDismiss = { showAddText = false },
        )
    }
    val toEditText = pendingEditText
    if (toEditText != null) {
        BibleTextRecordDialog(
            existing = toEditText,
            eventId = event.id,
            publisherPersonId = publisherPersonId,
            onSave = { viewModel.saveRecord(it); showToast("Bible text updated successfully."); pendingEditText = null },
            onDismiss = { pendingEditText = null },
        )
    }
    val toDeleteText = pendingDeleteText
    if (toDeleteText != null) {
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
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
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
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

/** A titled group on the event page — a heading with its count, that group's own
 * Add button, then its content. Bible Texts and Videos each get one so they read
 * as two separate sections. */
@Composable
private fun GroupCard(
    icon: ImageVector,
    title: String,
    count: Int,
    addLabel: String,
    addIcon: ImageVector,
    onAdd: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconLine(icon, "$title ($count)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, tint = MaterialTheme.colorScheme.primary)
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(addIcon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(addLabel)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content()
            }
        }
    }
}

/** "Make the record smaller" — a compact row instead of the previous
 * generously-padded card: smaller icons/text, tighter spacing, and the
 * Edit/Delete/JW Library actions collapsed into a single icon-only row so
 * more Bible Texts are visible on screen at once without scrolling past
 * mostly-empty space between them. */
@Composable
private fun BibleTextCard(text: BibleTextRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val showToast = rememberActionToast()
    val bookNumber = NwtBibleReferenceData.book(text.bibleVersionId, text.languageId, text.bibleBookId)?.order
    val jwLocale = NwtBibleReferenceData.language(text.languageId)?.jwLocale
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        Text(text.referenceLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        NwtBibleReferenceData.version(text.bibleVersionId)?.let {
                            Text("· ${it.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (text.remarks.isNotBlank()) {
                        Text(text.remarks, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Text("Added: ${formatRecordTimestamp(text.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (bookNumber != null && jwLocale != null) {
                    IconButton(
                        onClick = { if (!openInJwLibrary(context, jwLocale, bookNumber, text.chapter, text.verses)) showToast("Couldn't open JW Library.") },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open verse in JW Library", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Bible text", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete Bible text", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add/Edit Event

/** "Redesign the My Bible Text Record" §1/§2 — Event and Theme/Topic are now
 * dropdowns sourced from this module's existing sample lists ([SUGGESTED_EVENTS]/
 * [SUGGESTED_THEME_TOPICS] — the exact same values the old free-text fields
 * offered as suggestion chips; no option added, removed, or reworded). Both
 * stay searchable-and-editable ([SearchableOptionField]) rather than a
 * strict closed dropdown, so an existing record's Event/Theme/Topic value is
 * always shown even if it was typed before this list existed and isn't one
 * of the sample values (spec §7 — never silently drop/replace an old value). */
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
        hasUnsavedChanges = eventText != existing?.event.orEmpty() || themeTopic != existing?.name.orEmpty() ||
            speaker != existing?.speaker.orEmpty(),
        errorMessage = errorMessage,
    ) {
        SearchableOptionField(
            label = "Event",
            value = eventText,
            options = SUGGESTED_EVENTS,
            onValueChange = { eventText = it; errorMessage = null },
            placeholder = "Select or search Event",
            required = true,
        )
        // A plain textbox — the publisher writes their own Theme or Topic rather
        // than choosing from a list.
        OutlinedTextField(
            value = themeTopic,
            onValueChange = { themeTopic = it; errorMessage = null },
            label = { Text("Theme/Topic *") },
            placeholder = { Text("Type your own Theme or Topic") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
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

/** A dropdown that's still typeable (a "combobox") — spec §1/§2 want a
 * dropdown the Publisher *selects* from, but also (§7) never lose an old
 * record's value if it isn't one of [options]. Typing narrows [options] to
 * matches (spec: "searchable if the list is long"); picking one from the
 * menu or leaving typed text as-is both just set [value] via [onValueChange]
 * — there's no separate "committed selection" state, so an old custom value
 * displays exactly as saved instead of being coerced onto a list entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableOptionField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    placeholder: String,
    required: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, options) {
        if (value.isBlank()) options else options.filter { it.contains(value.trim(), ignoreCase = true) }
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(if (required) "$label *" else label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .onFocusChanged { if (it.isFocused) expanded = true },
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filtered.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add/Edit Bible Text — Book → Chapters two-level navigation (spec §3-§5),
// Bible Language removed from the UI entirely and fixed to English (§6).

private val VERSE_PART_REGEX = Regex("""^\d+(-\d+)?$""")
private val SPACES_AROUND_HYPHEN = Regex("""\s*-\s*""")

/** "3 - 4" and "3–4" (en dash, already converted to "-" as it's typed) read the
 * same as "3-4". */
private fun normalizeVerses(text: String): String = text.replace(SPACES_AROUND_HYPHEN, "-").trim()

/** True for a comma-separated list of verses/ranges ("3", "3-4", "3, 5",
 * "3-4, 8") whose ranges run low to high. Takes text already run through
 * [normalizeVerses]. */
private fun isValidVerseList(normalized: String): Boolean {
    if (normalized.isEmpty()) return false
    return normalized.split(",").map { it.trim() }.all { part ->
        VERSE_PART_REGEX.matches(part) &&
            part.split("-").let { bounds ->
                val numbers = bounds.map { it.toIntOrNull() ?: return false }
                numbers.size == 1 || numbers[0] <= numbers[1]
            }
    }
}

/** Progress of the jw.org verse-text lookup in the Add/Edit dialog. */
private enum class VerseLookup { IDLE, LOADING, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BibleTextRecordDialog(
    existing: BibleTextRecord?,
    eventId: String,
    publisherPersonId: String,
    onSave: (BibleTextRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val version = NwtBibleReferenceData.defaultVersion
    // Bible Language — English by default (spec §6), now with a picker again so a
    // record can be kept in another language the New World Translation is
    // published in. An existing record starts on whatever language it already
    // had (spec §7: never silently change saved data).
    var languageId by remember { mutableStateOf(existing?.languageId?.ifBlank { null } ?: NwtBibleReferenceData.languages.first().id) }
    val language = NwtBibleReferenceData.language(languageId)
    val books = remember(languageId) { NwtBibleReferenceData.booksFor(version.id, languageId) }
    var bookId by remember { mutableStateOf(existing?.bibleBookId?.ifBlank { null }) }
    val selectedBook = books.firstOrNull { it.id == bookId }
    // Spec §3 — starts on the Chapters view when editing a record that
    // already has a book (so the Publisher immediately sees what's saved),
    // and on the Book list otherwise. Toggled only by picking a book (spec
    // §3: "Select Genesis -> Genesis Chapters") or pressing Back (spec §4).
    var showingBookList by remember { mutableStateOf(bookId == null) }
    // Spec test 6 — changing the Bible Book always resets the selected
    // Chapter; an already-valid chapter for the *previous* book is never
    // carried over to a book it might not even have that many chapters in.
    var chapter by remember { mutableStateOf(existing?.chapter?.takeIf { it > 0 }) }
    var versesText by remember { mutableStateOf(existing?.verses.orEmpty()) }
    var remarks by remember { mutableStateOf(existing?.remarks.orEmpty()) }

    // Bug fix: a publisher citing more than one verse in the same chapter
    // (e.g. "16, 18" or "3-4, 8") is a normal, common case — the old regex
    // only ever accepted a single verse ("3") or a single range ("3-4"),
    // silently rejecting anything else as "invalid" even though the field
    // visibly had text in it; from the Save button's error message ("Verses
    // is required.") that read as a bug ("I typed a verse and it still says
    // required"), not as "wrong format". Now a comma-separated list of
    // verses/ranges is accepted too ("3", "3-4", "3, 5", "3-4, 8"); still
    // rejects "abc", "3--4", "-4", "hello", trailing/leading commas, etc.
    // Chapter/verse *existence* is already enforced structurally by the
    // Chapter grid (only ever offers 1..chapterCount) rather than needing a
    // separate existence check here.
    // "3 - 4" and "3–4" (en dash) read the same as "3-4".
    // Used for what's shown on screen. [submit] and [lookUpVerseText] below
    // deliberately re-derive these from [versesText] when they run rather than
    // reading these two values: they are plain values captured when the dialog
    // was composed, and the Save button's callback can keep using the version
    // captured while Verses was still empty — which made Save reject a
    // perfectly valid "1".
    val versesNormalized = normalizeVerses(versesText)
    val verseRangeValid = isValidVerseList(versesNormalized)
    // "Add the real verses in the remarks" — once a book, chapter and valid
    // verses are chosen, the New World Translation text for them is looked up
    // on jw.org and put in Remarks (in the chosen language; needs internet). It only
    // fills Remarks when that's blank or still holds a previous automatic
    // fill, so text the publisher typed is never overwritten — the "Insert
    // verse text" button is the explicit way to replace it. A failed lookup
    // just leaves Remarks to be typed; nothing here blocks saving.
    val verseTextViewModel: BibleVerseTextViewModel = hiltViewModel()
    val context = LocalContext.current
    val showToast = rememberActionToast()
    val clipboardManager = LocalClipboardManager.current
    val verseScope = rememberCoroutineScope()
    var verseStatus by remember { mutableStateOf(VerseLookup.IDLE) }
    var autoFilledRemarks by remember { mutableStateOf<String?>(null) }
    val canLookUpVerses = selectedBook != null && chapter != null && verseRangeValid && language != null
    suspend fun lookUpVerseText(overwrite: Boolean) {
        val book = selectedBook ?: return
        val chapterNumber = chapter ?: return
        val jwLocale = language?.jwLocale ?: return
        verseStatus = VerseLookup.LOADING
        val text = verseTextViewModel.fetchVerses(jwLocale, book.order, chapterNumber, normalizeVerses(versesText))
        coroutineContext.ensureActive()
        if (text == null) {
            verseStatus = VerseLookup.FAILED
            return
        }
        if (overwrite || remarks.isBlank() || remarks == autoFilledRemarks) {
            remarks = text
            autoFilledRemarks = text
        }
        verseStatus = VerseLookup.IDLE
    }
    LaunchedEffect(bookId, chapter, versesText, languageId) {
        val unchangedSavedReference = existing != null && bookId == existing.bibleBookId && chapter == existing.chapter && versesText == existing.verses && languageId == existing.languageId
        if (!canLookUpVerses || unchangedSavedReference) {
            verseStatus = VerseLookup.IDLE
            return@LaunchedEffect
        }
        delay(700) // let the publisher finish typing the verses
        lookUpVerseText(overwrite = false)
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    // A validation message from an earlier Save attempt is stale as soon as the
    // publisher changes anything — it must not linger over a now-valid entry.
    LaunchedEffect(bookId, chapter, versesText) { errorMessage = null }

    fun submit() {
        val message = requiredFieldsMessage(
            "Bible Book" to (bookId != null),
            "Chapter" to (chapter != null && chapter!! > 0),
            "Verses" to versesText.isNotBlank(),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        // Reported separately from the block above — a Verses field that
        // *has* text but doesn't parse as a verse/range list is a format
        // problem, not a missing-field one; conflating the two into the same
        // "is required" message is exactly what made this look like a bug
        // rather than a validation error to the Publisher.
        val currentVerses = normalizeVerses(versesText)
        if (!isValidVerseList(currentVerses)) {
            errorMessage = "Please enter a valid verse or verse range (e.g. 3, 3-4, or 3, 5)."
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
                verses = currentVerses,
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
        hasUnsavedChanges = bookId != existing?.bibleBookId?.ifBlank { null } ||
            languageId != (existing?.languageId?.ifBlank { null } ?: NwtBibleReferenceData.languages.first().id) ||
            chapter != existing?.chapter?.takeIf { it > 0 } ||
            versesText != existing?.verses.orEmpty() || remarks != existing?.remarks.orEmpty(),
    ) {
        // The verse text put in Remarks is always this version (the jw.org
        // Online Bible's "New World Translation of the Holy Scriptures").
        Text(version.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        LabeledDropdown(
            label = "Bible Language",
            selectedLabel = language?.name ?: languageId,
            options = NwtBibleReferenceData.languages.map { it.id to it.name },
            onSelected = { newId -> if (newId != null) languageId = newId },
        )
        Text("Bible Book *", style = MaterialTheme.typography.labelMedium)
        if (showingBookList) {
            // Spec §3 initial view — books only, no chapters shown alongside.
            BibleBookGrid(
                books = books,
                selectedBookId = bookId,
                onSelect = { newBookId ->
                    if (newBookId != bookId) chapter = null
                    bookId = newBookId
                    showingBookList = false
                },
            )
        } else if (selectedBook != null) {
            // Spec §3/§4/§5 — only the selected book's chapters, the book
            // name shown at top, and a Back control that returns to the
            // full book list without losing this book/chapter selection.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showingBookList = true }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Back to Bible Books")
                }
            }
            Text(selectedBook.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Select Chapter *", style = MaterialTheme.typography.labelMedium)
            ChapterGrid(
                chapterCount = selectedBook.chapterCount,
                selectedChapter = chapter,
                onSelect = { chapter = it },
            )
        } else {
            // Spec §7 — an old record's [bookId] that no longer matches any
            // current Bible book (e.g. a removed/renamed id) is preserved
            // as-is rather than silently cleared; the Publisher still has to
            // pick a real book from the list to save further changes.
            Text(
                "Previously selected Bible Book \"$bookId\" is no longer available. Please select a Bible Book.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { showingBookList = true }) { Text("Select Bible Book") }
        }
        OutlinedTextField(
            value = versesText,
            onValueChange = { input ->
                versesText = input
                    .map { c -> if (c == '–' || c == '—') '-' else c }
                    .filter { c -> c.isDigit() || c == '-' || c == ',' || c == ' ' }
                    .joinToString("")
            },
            label = { Text("Verses *") },
            placeholder = { Text("e.g. 3, 3-4, or 3, 5") },
            singleLine = true,
            isError = versesText.isNotBlank() && !verseRangeValid,
            supportingText = {
                if (versesText.isNotBlank() && !verseRangeValid) {
                    Text("Please enter a valid verse or verse range (e.g. 3, 3-4, or 3, 5).")
                }
            },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        when (verseStatus) {
            VerseLookup.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Getting the verse text from jw.org…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
            VerseLookup.FAILED -> Text(
                "Couldn't get the verse text. It needs an internet connection the first time a chapter is used — or open it in JW Library and paste it, or type it in Remarks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            VerseLookup.IDLE -> Unit
        }
        if (canLookUpVerses) {
            TextButton(onClick = { verseScope.launch { lookUpVerseText(overwrite = true) } }, enabled = verseStatus != VerseLookup.LOADING) {
                Icon(Icons.Rounded.AutoStories, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                Text("Insert verse text in Remarks")
            }
            // Read the verse in the JW Library app (works offline if that Bible is
            // downloaded there); GoPreach can't read JW Library's storage, so the
            // text comes back through the copy/paste button below.
            TextButton(
                onClick = {
                    val book = selectedBook
                    val chapterNumber = chapter
                    val jwLocale = language?.jwLocale
                    val opened = book != null && chapterNumber != null && jwLocale != null &&
                        openInJwLibrary(context, jwLocale, book.order, chapterNumber, normalizeVerses(versesText))
                    if (!opened) showToast("Couldn't open JW Library.")
                },
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                Text("Open verse in JW Library")
            }
        }
        // Puts whatever was just copied (for example a verse copied in JW Library)
        // into Remarks — replaces it when it's empty, otherwise adds it on a new
        // line.
        TextButton(
            onClick = {
                val copied = clipboardManager.getText()?.text?.replace("\r", "")?.trim().orEmpty()
                if (copied.isEmpty()) {
                    showToast("Nothing copied yet. Copy a verse first, then tap here.")
                } else {
                    remarks = if (remarks.isBlank()) copied else remarks.trimEnd() + "\n" + copied
                }
            },
        ) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
            Text("Paste copied text into Remarks")
        }
        OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = { Text("Remarks (optional)") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
    }
}

/** Shared dropdown shape every non-box picker on this screen uses (Search By,
 * Sort) — [options] is (value, label) so a `null` value ("All", ...) reads
 * naturally alongside real ids. Bible Book/Chapter deliberately do NOT use
 * this — see [BibleBookGrid]/[ChapterGrid]. */
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
