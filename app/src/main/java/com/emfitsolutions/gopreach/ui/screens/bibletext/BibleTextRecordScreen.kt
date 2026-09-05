package com.emfitsolutions.gopreach.ui.screens.bibletext

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
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
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.print.ReportPrinter
import com.emfitsolutions.gopreach.data.print.ReportTable
import com.emfitsolutions.gopreach.domain.NwtBibleReferenceData
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class BibleTextSort(val label: String) {
    BOOK("Bible Book"), CHAPTER("Chapter"), DATE_ADDED("Date Added"), CATEGORY("Category"), LANGUAGE("Language")
}

/** One saved record, resolved against [NwtBibleReferenceData] for display —
 * built once per composition from the raw [BibleTextRecord] so every part
 * of this screen (list, search, sort, export-ish formatting) reads off the
 * same resolved names instead of each re-doing the lookup. */
private data class ResolvedBibleText(
    val record: BibleTextRecord,
    val language: NwtBibleReferenceData.BibleLanguage?,
    val book: NwtBibleReferenceData.BibleBook?,
    val version: NwtBibleReferenceData.BibleVersion?,
    val category: BibleTextCategory?,
) {
    /** "Apocalipsis 21:3-4" (spec §7's own worked example) — falls back to
     * the raw book id if the book somehow isn't in the reference data
     * (a language/book pairing removed after the record was saved). */
    val referenceLabel: String get() = "${book?.name ?: record.bibleBookId} ${record.chapter}:${record.verses}"
}

/**
 * "My Bible Text Record" module (spec §1-§34) — a Publisher's personal
 * Bible-reference organizer: Add/Edit/Delete/View, search, filter (language/
 * category/book), and sort, over the Publisher's own saved records only
 * (see [BibleTextRecordViewModel]'s doc comment for the ownership model).
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
    val categoriesFlow = remember(publisherPersonId) { viewModel.categoriesFor(publisherPersonId) }
    val categories by categoriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // "Add an initial category" — seeded once this Publisher's *actual*
    // first emission from the underlying flow comes back empty. Collecting
    // categoriesFlow.first() directly here (not the `categories` state
    // above, which starts at the emptyList() placeholder before the real
    // cache/Firestore data ever arrives) avoids wrongly seeding for a
    // Publisher who already has categories, just not loaded onto screen
    // yet.
    LaunchedEffect(publisherPersonId) {
        viewModel.seedDefaultCategoriesIfNeeded(publisherPersonId, categoriesFlow.first())
    }
    val categoriesById = remember(categories) { categories.associateBy { it.id } }
    val showToast = rememberActionToast()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchText by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var languageFilter by remember { mutableStateOf<String?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var bookFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(BibleTextSort.DATE_ADDED) }

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<ResolvedBibleText?>(null) }
    var viewingRecord by remember { mutableStateOf<ResolvedBibleText?>(null) }
    var pendingDelete by remember { mutableStateOf<ResolvedBibleText?>(null) }
    var showPreferredLanguage by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // "The receiving Publisher can import the data" — a plain file picker
    // (Messenger/Gmail/Drive/... all save a received attachment somewhere
    // ordinary storage can reach) rather than a share-target intent-filter,
    // so importing works the same regardless of which app the file arrived
    // through.
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
            val result = viewModel.importRecords(publisherPersonId, file, categories)
            val categoryNote = if (result.newCategories > 0) " and ${result.newCategories} new categor${if (result.newCategories == 1) "y" else "ies"}" else ""
            showToast("Imported ${result.newRecords} record${if (result.newRecords == 1) "" else "s"}$categoryNote.")
        }
    }

    val resolved = remember(records) {
        records.map { record ->
            val version = NwtBibleReferenceData.version(record.bibleVersionId)
            val language = NwtBibleReferenceData.language(record.languageId)
            val book = NwtBibleReferenceData.book(record.bibleVersionId, record.languageId, record.bibleBookId)
            ResolvedBibleText(record, language, book, version, categoriesById[record.categoryId])
        }
    }

    // Spec §16 — Bible Book filter options resolve to the currently filtered
    // language's names (falling back to English when "All" languages is
    // selected), but the filter itself matches on the language-independent
    // book id (see [NwtBibleReferenceData]'s doc comment: the same book
    // shares one id across every language) — so switching the language
    // filter never resets which *book* is selected.
    val bookOptionLanguageId = languageFilter ?: "en"
    val bookOptions = remember(bookOptionLanguageId) {
        NwtBibleReferenceData.booksFor(NwtBibleReferenceData.defaultVersion.id, bookOptionLanguageId)
    }

    val filtered = remember(resolved, searchText, languageFilter, categoryFilter, bookFilter, sort) {
        resolved
            .filter { languageFilter == null || it.record.languageId == languageFilter }
            .filter { categoryFilter == null || it.record.categoryId == categoryFilter }
            .filter { bookFilter == null || it.record.bibleBookId == bookFilter }
            .filter { r ->
                if (searchText.isBlank()) return@filter true
                val q = searchText.trim()
                r.book?.name?.contains(q, ignoreCase = true) == true ||
                    r.record.chapter.toString() == q ||
                    r.record.verses.contains(q, ignoreCase = true) ||
                    r.category?.name?.contains(q, ignoreCase = true) == true ||
                    r.record.remarks.contains(q, ignoreCase = true)
            }
            .let { list ->
                when (sort) {
                    BibleTextSort.BOOK -> list.sortedBy { it.book?.order ?: Int.MAX_VALUE }
                    BibleTextSort.CHAPTER -> list.sortedBy { it.record.chapter }
                    BibleTextSort.DATE_ADDED -> list.sortedByDescending { it.record.createdAt }
                    BibleTextSort.CATEGORY -> list.sortedBy { it.category?.name ?: "" }
                    BibleTextSort.LANGUAGE -> list.sortedBy { it.language?.name ?: "" }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bible Text Record") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
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
                            // "Add a print button in 'My Bible Text Record'"
                            // — same ReportPrinter/ReportTable every other
                            // report screen in this app uses, over whatever
                            // the current search/filter is showing.
                            DropdownMenuItem(
                                text = { Text("Print") },
                                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                                onClick = { showMoreMenu = false; ReportPrinter.print(context, bibleTextReportTable(filtered)) },
                            )
                            // "Export or share through Messenger and other
                            // platform[s]" — shares the Publisher's whole
                            // collection (not just the current filter), since
                            // the point is handing someone else the full set.
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    BibleTextExporter.share(context, BibleTextExporter.buildExportJson(records, categoriesById))
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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Bible Text")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Search Bible Text...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (showFilters) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledDropdown(
                        label = "Language",
                        selectedLabel = languageFilter?.let { NwtBibleReferenceData.language(it)?.name } ?: "All",
                        options = listOf(null as String? to "All") + NwtBibleReferenceData.languages.map { it.id to it.name },
                        onSelected = { languageFilter = it },
                    )
                    CategoryDropdownField(
                        label = "Category",
                        publisherPersonId = publisherPersonId,
                        categories = categories,
                        selectedCategoryId = categoryFilter,
                        onSelected = { categoryFilter = it },
                        viewModel = viewModel,
                        showToast = showToast,
                        includeAllOption = true,
                    )
                    LabeledDropdown(
                        label = "Bible Book",
                        selectedLabel = bookFilter?.let { id -> bookOptions.firstOrNull { it.id == id }?.name } ?: "All Books",
                        options = listOf(null as String? to "All Books") + bookOptions.map { it.id to it.name },
                        onSelected = { bookFilter = it },
                    )
                    LabeledDropdown(
                        label = "Sort By",
                        selectedLabel = sort.label,
                        options = BibleTextSort.entries.map { it.name to it.label },
                        onSelected = { value -> sort = BibleTextSort.entries.first { it.name == value } },
                    )
                    HorizontalDivider()
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (records.isEmpty()) "No Bible text saved yet. Tap + to add one." else "No records match your search/filters.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.record.id }) { item ->
                        BibleTextCard(
                            item = item,
                            onView = { viewingRecord = item },
                            onEdit = { pendingEdit = item },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        BibleTextRecordDialog(
            existing = null,
            publisherPersonId = publisherPersonId,
            preferredLanguageId = currentPerson?.preferredBibleLanguageId,
            categories = categories,
            showToast = showToast,
            onSave = { viewModel.saveRecord(it); showToast("Bible text record saved successfully."); showAddDialog = false },
            onDismiss = { showAddDialog = false },
            viewModel = viewModel,
        )
    }
    val toEdit = pendingEdit
    if (toEdit != null) {
        BibleTextRecordDialog(
            existing = toEdit.record,
            publisherPersonId = publisherPersonId,
            preferredLanguageId = currentPerson?.preferredBibleLanguageId,
            categories = categories,
            showToast = showToast,
            onSave = { viewModel.saveRecord(it); showToast("Bible text record updated successfully."); pendingEdit = null },
            onDismiss = { pendingEdit = null },
            viewModel = viewModel,
        )
    }
    val toView = viewingRecord
    if (toView != null) {
        AlertDialog(
            onDismissRequest = { viewingRecord = null },
            title = { Text(toView.referenceLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${toView.version?.abbreviation ?: "NWT"} • ${toView.language?.name ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                    Text(toView.category?.name ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(toView.record.remarks.ifBlank { "No remarks." }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Added: ${formatRecordTimestamp(toView.record.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewingRecord = null }) { Text("Close") } },
        )
    }
    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Bible Text Record?") },
            text = { Text("Are you sure you want to delete ${toDelete.referenceLabel}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(toDelete.record.id)
                    showToast("Bible text record deleted successfully.")
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
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

/** "Add a print button in 'My Bible Text Record'" — same [ReportTable]/
 * [ReportPrinter] shape every other report screen in this app already uses. */
private fun bibleTextReportTable(items: List<ResolvedBibleText>): ReportTable {
    val rows = items.map { item ->
        listOf(
            item.referenceLabel,
            item.category?.name ?: "—",
            item.language?.name ?: "—",
            item.version?.abbreviation ?: "NWT",
            item.record.remarks,
            formatRecordTimestamp(item.record.createdAt),
        )
    }
    return ReportTable(
        title = "My Bible Text Record",
        columns = listOf("Reference", "Category", "Language", "Version", "Remarks", "Date Added"),
        rows = rows,
    )
}

@Composable
private fun BibleTextCard(
    item: ResolvedBibleText,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onView) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.referenceLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${item.version?.abbreviation ?: "NWT"} • ${item.language?.name ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(item.category?.name ?: "—", style = MaterialTheme.typography.bodyMedium)
                if (item.record.remarks.isNotBlank()) {
                    Text(item.record.remarks, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                Text(
                    "Added: ${formatRecordTimestamp(item.record.createdAt)}",
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

/** Add/Edit — Bible Version fixed to NWT, Language/Book/Chapter cascading
 * dropdowns (spec §6/§8: the Chapter list depends on the selected Book, and
 * an invalid combination can't be selected in the first place since the
 * dropdown only ever offers what [NwtBibleReferenceData] says exists),
 * Verses validated as a single verse or verse range (spec §9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BibleTextRecordDialog(
    existing: BibleTextRecord?,
    publisherPersonId: String,
    preferredLanguageId: String?,
    categories: List<BibleTextCategory>,
    showToast: (String) -> Unit,
    onSave: (BibleTextRecord) -> Unit,
    onDismiss: () -> Unit,
    viewModel: BibleTextRecordViewModel,
) {
    val version = NwtBibleReferenceData.defaultVersion
    var languageId by remember {
        mutableStateOf(existing?.languageId ?: preferredLanguageId ?: NwtBibleReferenceData.languages.first().id)
    }
    val books = remember(languageId) { NwtBibleReferenceData.booksFor(version.id, languageId) }
    var bookId by remember { mutableStateOf(existing?.bibleBookId ?: books.firstOrNull()?.id.orEmpty()) }
    val selectedBook = books.firstOrNull { it.id == bookId }
    var chapter by remember { mutableStateOf(existing?.chapter?.takeIf { it > 0 } ?: 1) }
    var versesText by remember { mutableStateOf(existing?.verses.orEmpty()) }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var remarks by remember { mutableStateOf(existing?.remarks.orEmpty()) }

    // Spec §9 — a single verse ("3") or a range ("3-4", "10-12"); rejects
    // "abc", "3--4", "-4", "hello", etc. Chapter/verse *existence* is
    // already enforced structurally by the cascading dropdowns above
    // (Chapter only ever offers 1..chapterCount) rather than needing a
    // separate existence check here.
    val versesValid = remember(versesText) { Regex("""^\d+(-\d+)?$""").matches(versesText.trim()) }
    val verseRangeValid = remember(versesText) {
        if (!versesValid) return@remember false
        val parts = versesText.trim().split("-")
        parts.size == 1 || (parts[0].toInt() <= parts[1].toInt())
    }
    val canSave = languageId.isNotBlank() && bookId.isNotBlank() && chapter > 0 &&
        versesText.isNotBlank() && verseRangeValid && categoryId.isNotBlank() && remarks.isNotBlank()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val message = requiredFieldsMessage(
            "Bible Language" to languageId.isNotBlank(),
            "Bible Book" to bookId.isNotBlank(),
            "Chapter" to (chapter > 0),
            "Verses" to (versesText.isNotBlank() && verseRangeValid),
            "Category" to categoryId.isNotBlank(),
            "Remarks" to remarks.isNotBlank(),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        val now = System.currentTimeMillis()
        val base = existing ?: BibleTextRecord(publisherPersonId = publisherPersonId, createdAt = now)
        onSave(
            base.copy(
                bibleVersionId = version.id,
                languageId = languageId,
                bibleBookId = bookId,
                chapter = chapter,
                verses = versesText.trim(),
                categoryId = categoryId,
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
        maxContentHeight = 560.dp,
    ) {
                Column {
                    Text("Bible Version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(version.name, style = MaterialTheme.typography.bodyMedium)
                }
                LabeledDropdown(
                    label = "Bible Language",
                    selectedLabel = NwtBibleReferenceData.language(languageId)?.name ?: "Select",
                    options = NwtBibleReferenceData.languages.map { it.id to it.name },
                    onSelected = { newLanguageId ->
                        languageId = newLanguageId ?: return@LabeledDropdown
                        val newBooks = NwtBibleReferenceData.booksFor(version.id, newLanguageId)
                        // Same book (by language-independent id) if it still
                        // exists in the new language's list, else fall back
                        // to the first book — never leaves an invalid/blank
                        // selection behind a language switch.
                        bookId = newBooks.firstOrNull { it.id == bookId }?.id ?: newBooks.firstOrNull()?.id.orEmpty()
                        val newChapterCount = newBooks.firstOrNull { it.id == bookId }?.chapterCount ?: 1
                        if (chapter > newChapterCount) chapter = 1
                    },
                    required = true,
                )
                LabeledDropdown(
                    label = "Bible Book",
                    selectedLabel = selectedBook?.name ?: "Select",
                    options = books.map { it.id to it.name },
                    onSelected = { newBookId ->
                        bookId = newBookId ?: return@LabeledDropdown
                        val chapterCount = books.firstOrNull { it.id == newBookId }?.chapterCount ?: 1
                        if (chapter > chapterCount) chapter = 1
                    },
                    required = true,
                )
                LabeledDropdown(
                    label = "Chapter",
                    selectedLabel = if (chapter > 0) chapter.toString() else "Select",
                    options = (1..(selectedBook?.chapterCount ?: 1)).map { it.toString() to it.toString() },
                    onSelected = { chapter = it?.toIntOrNull() ?: chapter },
                    required = true,
                )
                OutlinedTextField(
                    value = versesText,
                    onValueChange = { versesText = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("Verses") },
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
                // "Move the add, edit[,] delete directly in the dropdown" —
                // the Category picker itself carries Add/Edit/Delete now
                // (see CategoryDropdownField's own doc comment); no more
                // separate "+" button or Manage Categories hop.
                CategoryDropdownField(
                    label = "Category",
                    publisherPersonId = publisherPersonId,
                    categories = categories,
                    selectedCategoryId = categoryId.ifBlank { null },
                    onSelected = { categoryId = it.orEmpty() },
                    viewModel = viewModel,
                    showToast = showToast,
                    required = true,
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
    }
}

/** "Move the add, edit[,] delete directly in the dropdown" — the Category
 * picker (used both for filtering and for choosing a record's own category)
 * doubles as the category manager: every real category row carries its own
 * inline Edit/Delete icons, and an "Add Category" row sits at the bottom —
 * no separate "Manage Categories" dialog to hop to first. Add/Edit share one
 * name-entry [FormDialog]; Delete keeps spec §11's protection (a category
 * still assigned to a record is never silently deleted — it offers
 * reassignment instead). [includeAllOption] adds the filter panel's own
 * "All Categories" (null) choice above the real ones; the record dialog's
 * required picker omits it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdownField(
    label: String,
    publisherPersonId: String,
    categories: List<BibleTextCategory>,
    selectedCategoryId: String?,
    onSelected: (String?) -> Unit,
    viewModel: BibleTextRecordViewModel,
    showToast: (String) -> Unit,
    includeAllOption: Boolean = false,
    required: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<BibleTextCategory?>(null) }
    var showAddCategory by remember { mutableStateOf(false) }
    var inUseDelete by remember { mutableStateOf<Pair<BibleTextCategory, Int>?>(null) }
    var reassignToId by remember { mutableStateOf<String?>(null) }

    val selectedLabel = if (selectedCategoryId == null) {
        if (includeAllOption) "All Categories" else "Select"
    } else {
        categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Select"
    }

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
            if (includeAllOption) {
                DropdownMenuItem(text = { Text("All Categories") }, onClick = { onSelected(null); expanded = false })
                if (categories.isNotEmpty()) HorizontalDivider()
            }
            categories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (category.id == selectedCategoryId) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelected(category.id); expanded = false }
                            .padding(vertical = 12.dp),
                    )
                    IconButton(onClick = { editingCategory = category }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${category.name}", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            viewModel.deleteCategory(publisherPersonId, category.id) { result ->
                                when (result) {
                                    CategoryDeleteResult.Deleted -> {
                                        showToast("Category deleted successfully.")
                                        if (category.id == selectedCategoryId) onSelected(null)
                                    }
                                    is CategoryDeleteResult.InUse -> inUseDelete = category to result.recordCount
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete ${category.name}", modifier = Modifier.size(16.dp))
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = false; showAddCategory = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text("Add Category", color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showAddCategory || editingCategory != null) {
        val target = editingCategory
        var name by remember(target) { mutableStateOf(target?.name.orEmpty()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        fun submit() {
            val message = requiredFieldsMessage("Category name" to name.isNotBlank())
            if (message != null) {
                errorMessage = message
                return
            }
            val trimmed = name.trim()
            showAddCategory = false
            editingCategory = null
            if (target != null) {
                viewModel.saveCategory(target.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
                showToast("Category updated successfully.")
            } else {
                // Suspend save, then select the newly-created category the
                // moment its real id comes back, same as the old inline "+"
                // Add flow this dropdown replaces.
                coroutineScope.launch {
                    val now = System.currentTimeMillis()
                    val saved = viewModel.saveCategoryAndReturn(
                        BibleTextCategory(publisherPersonId = publisherPersonId, name = trimmed, createdAt = now, updatedAt = now),
                    )
                    onSelected(saved.id)
                }
                showToast("Category added successfully.")
            }
        }

        FormDialog(
            onDismissRequest = { showAddCategory = false; editingCategory = null },
            title = if (target == null) "Add Category" else "Edit Category",
            onConfirm = ::submit,
            confirmLabel = if (target == null) "Add" else "Save",
            errorMessage = errorMessage,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMessage = null },
                label = { Text("Category name") },
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Spec §11 "Category Delete Protection" — unchanged from the old Manage
    // Categories dialog, just reachable from here now.
    val inUse = inUseDelete
    if (inUse != null) {
        val (category, count) = inUse
        AlertDialog(
            onDismissRequest = { inUseDelete = null; reassignToId = null },
            title = { Text("Category is in use") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This category is currently assigned to $count Bible Text Record${if (count == 1) "" else "s"}.")
                    val otherCategories = categories.filter { it.id != category.id }
                    if (otherCategories.isNotEmpty()) {
                        LabeledDropdown(
                            label = "Reassign records to",
                            selectedLabel = otherCategories.firstOrNull { it.id == reassignToId }?.name ?: "Select a category",
                            options = otherCategories.map { it.id to it.name },
                            onSelected = { reassignToId = it },
                        )
                    } else {
                        Text("Create another category first to reassign these records.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reassignToId != null,
                    onClick = {
                        val target = reassignToId ?: return@TextButton
                        viewModel.reassignRecordsAndDeleteCategory(publisherPersonId, category.id, target)
                        showToast("Category deleted successfully.")
                        if (category.id == selectedCategoryId) onSelected(target)
                        inUseDelete = null
                        reassignToId = null
                    },
                ) { Text("Reassign & Delete") }
            },
            dismissButton = { TextButton(onClick = { inUseDelete = null; reassignToId = null }) { Text("Cancel") } },
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

/** Shared dropdown shape every picker above uses — [options] is (value,
 * label) so a `null` value ("All", "All Categories", ...) reads naturally
 * alongside real ids. */
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
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(value); expanded = false })
            }
        }
    }
}
