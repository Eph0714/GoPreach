package com.emfitsolutions.gopreach.ui.screens.bibletext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.export.BibleTextExportFile
import com.emfitsolutions.gopreach.data.model.BibleTextCategory
import com.emfitsolutions.gopreach.data.model.BibleTextRecord
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.repository.BibleTextCategoryRepository
import com.emfitsolutions.gopreach.data.repository.BibleTextRecordRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Add an initial category" — the starter set every Publisher's own
 * category list is seeded with the first time they have none (see
 * [BibleTextRecordViewModel.seedDefaultCategoriesIfNeeded]). Plain names,
 * not ids — a Publisher can freely rename, delete, or add to these
 * afterward, same as any category they created themselves; nothing else in
 * this module treats a default category specially once it exists. */
val DEFAULT_BIBLE_TEXT_CATEGORIES: List<String> = listOf(
    "God and His Attributes",
    "Jesus Christ",
    "The Bible and Its Teachings",
    "God's Kingdom",
    "Faith and Spirituality",
    "Prayer and Worship",
    "Christian Living",
    "Christian Qualities",
    "Family and Personal Matters",
    "Marriage and Relationships",
    "Parenting and Children",
    "Youth and Young People",
    "Daily Life and Practical Decisions",
    "Work, Money, and Material Things",
    "Health and Well-Being",
    "Peace, Happiness, and Encouragement",
    "Trials, Suffering, and Challenges",
    "Life and Death",
    "Sin, Forgiveness, and Salvation",
    "Conduct and Moral Issues",
    "Friendship and Relationships",
    "Congregation and Christian Unity",
    "Ministry and Evangelism",
    "Bible Prophecy and the Future",
    "Bible History",
    "Bible Characters and Examples",
    "Science and the Bible",
    "Bible Study and Understanding",
)

/** Result of attempting to delete a [BibleTextCategory] — spec §11 "Category
 * Delete Protection": a category currently assigned to one or more records
 * is never silently deleted (or, worse, silently orphans those records). */
sealed class CategoryDeleteResult {
    data object Deleted : CategoryDeleteResult()
    /** [recordCount] records still reference this category — the caller
     * (the screen) offers "reassign to another category" or "cancel". */
    data class InUse(val recordCount: Int) : CategoryDeleteResult()
}

/**
 * "My Bible Text Record" module (spec §1-§34) — a Publisher's personal
 * Bible-reference organizer. Every read/write here is scoped to whichever
 * [publisherPersonId] the caller passes, resolved by the screen from the
 * signed-in session (never a value the UI lets the Publisher type/pick
 * themselves) — the same "ownership from the session, not the frontend"
 * rule [BibleTextRecordRepository]'s own doc comment describes, backed
 * server-side by firestore.rules' matching `bibleTextRecords`/
 * `bibleTextCategories` blocks.
 */
@HiltViewModel
class BibleTextRecordViewModel @Inject constructor(
    private val recordRepository: BibleTextRecordRepository,
    private val categoryRepository: BibleTextCategoryRepository,
    private val personRepository: PersonRepository,
) : ViewModel() {

    fun recordsFor(publisherPersonId: String): Flow<List<BibleTextRecord>> =
        recordRepository.observeForPublisher(publisherPersonId)

    fun categoriesFor(publisherPersonId: String): Flow<List<BibleTextCategory>> =
        categoryRepository.observeForPublisher(publisherPersonId)

    /** In-memory guard so a rapid recomposition/re-collection of
     * [categoriesFor] can't call [seedDefaultCategoriesIfNeeded] twice
     * before the first save round-trips through the offline cache and the
     * flow re-emits a non-empty list — not persisted, since "already
     * seeded, stay empty" isn't something this needs to remember past this
     * ViewModel's own lifetime; a genuinely still-empty list next time this
     * screen opens is exactly the case this feature exists for. */
    private val seededForPublisher = mutableSetOf<String>()

    /** "Add an initial category" — the first time a Publisher's own
     * category list is genuinely empty (a brand-new Publisher, or one who
     * deleted every category they had), seeds it with
     * [DEFAULT_BIBLE_TEXT_CATEGORIES] so they land on a populated Category
     * dropdown instead of an empty one with no starting point. Every seeded
     * category is a completely ordinary [BibleTextCategory] afterward —
     * freely renamable/deletable, no different from one the Publisher
     * typed in themselves. */
    fun seedDefaultCategoriesIfNeeded(publisherPersonId: String, currentCategories: List<BibleTextCategory>) {
        if (currentCategories.isNotEmpty()) return
        if (!seededForPublisher.add(publisherPersonId)) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            DEFAULT_BIBLE_TEXT_CATEGORIES.forEach { name ->
                categoryRepository.save(BibleTextCategory(publisherPersonId = publisherPersonId, name = name, createdAt = now, updatedAt = now))
            }
        }
    }

    fun saveRecord(record: BibleTextRecord) {
        viewModelScope.launch { recordRepository.save(record) }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch { recordRepository.delete(recordId) }
    }

    fun saveCategory(category: BibleTextCategory) {
        viewModelScope.launch { categoryRepository.save(category) }
    }

    /** "Allow the publisher to add a category directly upon enrolling new
     * Bible Text record" — a suspend variant of [saveCategory] that hands
     * the saved (id-assigned) category straight back, so the Add/Edit Bible
     * Text dialog can select it immediately instead of waiting for the next
     * [categoriesFor] emission to catch up before the new category is even
     * choosable. */
    suspend fun saveCategoryAndReturn(category: BibleTextCategory): BibleTextCategory = categoryRepository.save(category)

    /** Spec §11 — checks every one of [publisherPersonId]'s own records
     * before deleting; a category in use is reported back as
     * [CategoryDeleteResult.InUse] instead of being deleted, so the screen
     * can offer reassignment rather than deleting out from under live
     * records. */
    fun deleteCategory(publisherPersonId: String, categoryId: String, onResult: (CategoryDeleteResult) -> Unit) {
        viewModelScope.launch {
            val inUseCount = recordRepository.observeForPublisher(publisherPersonId).first()
                .count { it.categoryId == categoryId }
            if (inUseCount > 0) {
                onResult(CategoryDeleteResult.InUse(inUseCount))
                return@launch
            }
            categoryRepository.delete(categoryId)
            onResult(CategoryDeleteResult.Deleted)
        }
    }

    /** "Reassign the records to another category" (spec §11), then deletes
     * the now-unused category — one user action, not two separate taps a
     * Publisher could abandon halfway through and leave the old category
     * still in use by nothing. */
    fun reassignRecordsAndDeleteCategory(publisherPersonId: String, fromCategoryId: String, toCategoryId: String) {
        viewModelScope.launch {
            val toReassign = recordRepository.observeForPublisher(publisherPersonId).first()
                .filter { it.categoryId == fromCategoryId }
            toReassign.forEach { record ->
                recordRepository.save(record.copy(categoryId = toCategoryId, updatedAt = System.currentTimeMillis()))
            }
            categoryRepository.delete(fromCategoryId)
        }
    }

    /** Spec §4 — "Preferred Bible Language": auto-selected on a new record,
     * changeable per record without affecting this saved default. */
    fun updatePreferredLanguage(person: Person, languageId: String) {
        viewModelScope.launch { personRepository.save(person.copy(preferredBibleLanguageId = languageId)) }
    }

    /** "The receiving Publisher can import the data[;] the imported data
     * must not override the existing data of the receiving Publisher" —
     * every imported record becomes a brand-new [BibleTextRecord] with a
     * fresh Firestore-assigned id, owned by [publisherPersonId]; nothing the
     * receiving Publisher already has is ever matched, overwritten, or
     * deleted. A category is only created when no existing category of that
     * exact name (case-insensitive) already exists for this Publisher —
     * reusing an existing one by name instead of creating a duplicate is the
     * one bit of "merging" this does, and it never renames/deletes/touches
     * an existing category to do it. */
    suspend fun importRecords(
        publisherPersonId: String,
        file: BibleTextExportFile,
        existingCategories: List<BibleTextCategory>,
    ): ImportResult {
        val categoryIdByName = existingCategories.associateTo(mutableMapOf()) { it.name.trim().lowercase() to it.id }
        var newCategoryCount = 0
        file.records.map { it.categoryName.trim() }.distinct().forEach { name ->
            val key = name.lowercase()
            if (!categoryIdByName.containsKey(key)) {
                val now = System.currentTimeMillis()
                val saved = categoryRepository.save(BibleTextCategory(publisherPersonId = publisherPersonId, name = name, createdAt = now, updatedAt = now))
                categoryIdByName[key] = saved.id
                newCategoryCount++
            }
        }
        var newRecordCount = 0
        file.records.forEach { exported ->
            val categoryId = categoryIdByName[exported.categoryName.trim().lowercase()] ?: return@forEach
            val now = System.currentTimeMillis()
            recordRepository.save(
                BibleTextRecord(
                    publisherPersonId = publisherPersonId,
                    bibleVersionId = exported.bibleVersionId,
                    languageId = exported.languageId,
                    bibleBookId = exported.bibleBookId,
                    chapter = exported.chapter,
                    verses = exported.verses,
                    categoryId = categoryId,
                    remarks = exported.remarks,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            newRecordCount++
        }
        return ImportResult(newCategoryCount, newRecordCount)
    }
}

/** [newCategories]/[newRecords] — how many of each [importRecords] actually
 * added, for the "Imported X records and Y new categories" confirmation. */
data class ImportResult(val newCategories: Int, val newRecords: Int)
