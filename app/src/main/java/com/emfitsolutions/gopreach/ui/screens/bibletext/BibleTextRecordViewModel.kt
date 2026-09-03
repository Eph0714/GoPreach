package com.emfitsolutions.gopreach.ui.screens.bibletext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun saveRecord(record: BibleTextRecord) {
        viewModelScope.launch { recordRepository.save(record) }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch { recordRepository.delete(recordId) }
    }

    fun saveCategory(category: BibleTextCategory) {
        viewModelScope.launch { categoryRepository.save(category) }
    }

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
}
